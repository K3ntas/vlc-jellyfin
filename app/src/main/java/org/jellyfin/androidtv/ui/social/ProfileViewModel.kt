package org.jellyfin.androidtv.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.social.Friend
import org.jellyfin.androidtv.data.social.FriendRequest
import org.jellyfin.androidtv.data.social.DirectoryUser
import org.jellyfin.androidtv.data.social.GenreSlice
import org.jellyfin.androidtv.data.social.RatedItem
import org.jellyfin.androidtv.data.social.SimilarUser
import org.jellyfin.androidtv.data.social.TopGenre
import org.jellyfin.androidtv.data.social.ProfileStats
import org.jellyfin.androidtv.data.social.ProfileStyle
import org.jellyfin.androidtv.data.social.SocialNotification
import org.jellyfin.androidtv.data.social.SocialProfile
import org.jellyfin.androidtv.data.social.SocialRepository
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.util.UUID

/**
 * Everything the profile screen renders. All of it is optional: a server without the ratings
 * plugin returns nothing, and each section simply stays empty rather than failing the screen.
 */
data class ProfileUiState(
	val loading: Boolean = true,
	val profile: SocialProfile? = null,
	val stats: ProfileStats? = null,
	val style: ProfileStyle = ProfileStyle(),
	val friends: List<Friend> = emptyList(),
	val incomingRequests: List<FriendRequest> = emptyList(),
	val notifications: List<SocialNotification> = emptyList(),
	val unreadCount: Int = 0,
	val likeCount: Int = 0,
	val genres: List<GenreSlice> = emptyList(),
	val totalGenreMinutes: Long = 0,
	val followersCount: Int = 0,
	val followingCount: Int = 0,
	val ratingDistribution: List<Int> = emptyList(),
	val onlineStatus: String = "",
	val memberSince: String = "",
	val topGenres: List<TopGenre> = emptyList(),
	val ratings: List<RatedItem> = emptyList(),
	val reviews: List<RatedItem> = emptyList(),
	val activity: List<RatedItem> = emptyList(),
	val similarUsers: List<SimilarUser> = emptyList(),
	val followers: List<Friend> = emptyList(),
	val following: List<Friend> = emptyList(),
	val otherUsers: List<DirectoryUser> = emptyList(),
	val userLiked: Boolean = false,
	/** True when viewing someone else's profile, which hides notifications and requests. */
	val isOtherUser: Boolean = false,
	/** Set when the server could not be reached, to tell that apart from "no plugin installed". */
	val unreachable: Boolean = false,
)

/**
 * Survives navigation so returning to a profile shows it immediately instead of replaying ten
 * requests. The cached copy is shown at once and then refreshed in the background.
 */
private object ProfileCache {
	private const val TTL_MILLIS = 5 * 60 * 1000L

	private var key: String? = null
	private var cached: ProfileUiState? = null
	private var loadedAt = 0L

	fun get(cacheKey: String): ProfileUiState? {
		if (key != cacheKey) return null
		if (System.currentTimeMillis() - loadedAt > TTL_MILLIS) return null

		return cached
	}

	fun put(cacheKey: String, state: ProfileUiState) {
		key = cacheKey
		cached = state
		loadedAt = System.currentTimeMillis()
	}
}

private const val DEFAULT_HEARTBEAT_SECONDS = 30

class ProfileViewModel(
	private val socialRepository: SocialRepository,
) : ViewModel() {
	private val _state = MutableStateFlow(ProfileUiState())
	val state = _state.asStateFlow()

	private var profileUserId: UUID? = null
	private var heartbeatJob: Job? = null

	/** Pass null to load the signed-in user's own profile. */
	fun load(userId: UUID?) {
		viewModelScope.launch {
			val cacheKey = userId?.toString() ?: "me"

			// Show what we had immediately; the refresh below replaces it when it lands
			val cached = ProfileCache.get(cacheKey)
			if (cached != null) {
				_state.value = cached.copy(loading = false)
			} else {
				_state.update { it.copy(loading = true, isOtherUser = userId != null) }
			}

			val capabilities = socialRepository.getCapabilities()
			startHeartbeat(capabilities?.presence?.heartbeatSeconds ?: DEFAULT_HEARTBEAT_SECONDS)
			Timber.i(
				"Social capabilities: version=%s social=%s full=%s",
				capabilities?.version,
				capabilities?.enabled?.social,
				capabilities?.supports("profile.full"),
			)

			val profile = if (userId == null) {
				socialRepository.getMyProfile()
			} else {
				socialRepository.getProfile(userId)
			}

			val id = userId ?: profile?.userId?.let(::runCatchingUuid)
			profileUserId = id

			// One aggregate request instead of six, when the server is new enough to serve it
			val full = if (id != null && capabilities?.supports("profile.full") == true) {
				socialRepository.getProfileFull(id)
			} else {
				null
			}

			if (full != null && id != null) {
				Timber.i(
					"Profile/Full: user=%s ratings=%d followers=%d rows=%d",
					full.username,
					full.stats.totalRatings,
					full.counts.followers,
					full.favoriteRows.size,
				)

				_state.update {
					it.copy(
						loading = false,
						profile = (profile ?: SocialProfile()).copy(
							userId = full.userId,
							username = full.username.ifBlank { profile?.username.orEmpty() },
							bio = full.bio.ifBlank { profile?.bio.orEmpty() },
							avatarUrl = full.avatarUrl.ifBlank { profile?.avatarUrl.orEmpty() },
							headerMediaUrl = full.headerMediaUrl.ifBlank { profile?.headerMediaUrl.orEmpty() },
							favorites = full.favorites.ifEmpty { profile?.favorites.orEmpty() },
							favoriteRows = full.favoriteRows.ifEmpty { profile?.favoriteRows.orEmpty() },
						),
						stats = ProfileStats(
							friendsCount = full.counts.friends,
							ratingsCount = full.stats.totalRatings,
							reviewsCount = full.stats.reviewCount,
							averageRating = full.stats.averageRating,
							moviesWatched = full.stats.watchedItems,
							totalWatchHours = (full.stats.watchedMinutes / 60).toInt(),
						),
						style = full.style,
						genres = full.topGenres.map { g -> GenreSlice(g.name, g.percent.toLong()) },
						totalGenreMinutes = 100,
						followersCount = full.counts.followers,
						followingCount = full.counts.following,
						likeCount = full.counts.profileLikes,
						ratingDistribution = full.ratingDistribution,
						onlineStatus = full.onlineStatus,
					)
				}

				// Everything Full does not carry, fetched together
				val ratingsPage = async { socialRepository.getUserRatings(id, limit = 40) }
				val reviewsPage = async { socialRepository.getUserReviews(id, limit = 40) }
				val activityPage = async { socialRepository.getUserActivity(id, limit = 20) }
				val similar = async { socialRepository.getSimilarUsers(id) }
				val followersPage = async { socialRepository.getFollowers(id) }
				val followingPage = async { socialRepository.getFollowing(id) }
				val directory = async { if (userId == null) socialRepository.getDirectoryUsers() else null }

				_state.update {
					it.copy(
						topGenres = full.topGenres,
						ratings = ratingsPage.await()?.items.orEmpty(),
						reviews = reviewsPage.await()?.items.orEmpty(),
						activity = activityPage.await()?.items.orEmpty(),
						similarUsers = similar.await()?.matches.orEmpty(),
						followers = followersPage.await()?.followers.orEmpty(),
						following = followingPage.await()?.following.orEmpty(),
						otherUsers = directory.await()?.users.orEmpty(),
					)
				}

				// These were three sequential round trips; on a TV over wifi that is three waits
				val friendsCall = async { socialRepository.getFriends() }
				val requestsCall = async {
					if (userId == null) socialRepository.getIncomingFriendRequests() else null
				}
				val notificationsCall = async {
					if (userId == null) socialRepository.getNotifications() else null
				}

				val friends = friendsCall.await()?.friends.orEmpty()
				val requests = requestsCall.await()?.requests.orEmpty()
				val notifications = notificationsCall.await()

				_state.update {
					it.copy(
						friends = friends,
						incomingRequests = requests,
						notifications = notifications?.notifications.orEmpty(),
						unreadCount = notifications?.unreadCount ?: 0,
					)
				}

				ProfileCache.put(cacheKey, _state.value)
				return@launch
			}

			if (id == null) {
				// Without a usable id none of the per-user endpoints can be called, so say so
				// rather than leaving a header with no content behind it.
				Timber.w("Profile has no usable userId (raw=%s); skipping stats and lists", profile?.userId)
				_state.update {
					it.copy(
						loading = false,
						profile = profile,
						// Capabilities answering means the plugin is there and something else broke
						unreachable = profile == null && capabilities != null,
					)
				}
				return@launch
			}

			// Fetch the independent sections together rather than one after another
			val stats = async { socialRepository.getProfileStats(id) }
			val style = async { socialRepository.getProfileStyle(id) }
			val likes = async { socialRepository.getProfileLikes(id) }
			val friends = async { socialRepository.getFriends() }
			val requests = async { if (userId == null) socialRepository.getIncomingFriendRequests() else null }
			val notifications = async { if (userId == null) socialRepository.getNotifications() else null }
			val genres = async { socialRepository.getGenres(id) }
			val followers = async { socialRepository.getFollowers(id) }
			val following = async { socialRepository.getFollowing(id) }

			val likesResult = likes.await()
			val notificationsResult = notifications.await()
			val statsResult = stats.await()

			Timber.i(
				"Profile loaded: user=%s favouriteRows=%d friends=%d ratings=%d",
				profile?.username,
				profile?.favoriteRows?.size ?: 0,
				friends.await()?.friends?.size ?: 0,
				statsResult?.ratingsCount ?: -1,
			)

			_state.update {
				it.copy(
					loading = false,
					profile = profile,
					stats = statsResult,
					style = style.await() ?: ProfileStyle(),
					friends = friends.await()?.friends.orEmpty(),
					incomingRequests = requests.await()?.requests.orEmpty(),
					notifications = notificationsResult?.notifications.orEmpty(),
					unreadCount = notificationsResult?.unreadCount ?: 0,
					likeCount = likesResult?.likeCount ?: 0,
					userLiked = likesResult?.userLiked == true,
					genres = genres.await()?.genres.orEmpty(),
					totalGenreMinutes = genres.await()?.totalMinutes ?: 0,
					followersCount = followers.await()?.let { r -> r.total.takeIf { t -> t > 0 } ?: r.followers.size } ?: 0,
					followingCount = following.await()?.let { r -> r.total.takeIf { t -> t > 0 } ?: r.following.size } ?: 0,
				)
			}
		}
	}

	fun toggleLike() {
		val id = profileUserId ?: return
		val liked = _state.value.userLiked

		viewModelScope.launch {
			// Reflect the change immediately, then correct from the server's count
			_state.update {
				it.copy(
					userLiked = !liked,
					likeCount = (it.likeCount + if (liked) -1 else 1).coerceAtLeast(0),
				)
			}

			val ok = if (liked) socialRepository.unlikeProfile(id) else socialRepository.likeProfile(id)
			if (!ok) {
				_state.update { it.copy(userLiked = liked) }
				return@launch
			}

			socialRepository.getProfileLikes(id)?.let { likes ->
				_state.update { it.copy(likeCount = likes.likeCount, userLiked = likes.userLiked) }
			}
		}
	}

	fun acceptRequest(request: FriendRequest) = resolveRequest(request) {
		socialRepository.acceptFriendRequest(request.id)
	}

	fun rejectRequest(request: FriendRequest) = resolveRequest(request) {
		socialRepository.rejectFriendRequest(request.id)
	}

	private fun resolveRequest(request: FriendRequest, action: suspend () -> Boolean) {
		viewModelScope.launch {
			if (!action()) return@launch

			// Drop the handled request and pull the friend list back in, since accepting changes it
			_state.update { current ->
				current.copy(incomingRequests = current.incomingRequests.filterNot { it.id == request.id })
			}

			val friends = socialRepository.getFriends()?.friends.orEmpty()
			_state.update { it.copy(friends = friends) }
		}
	}

	fun markAllRead() {
		viewModelScope.launch {
			if (!socialRepository.markAllNotificationsRead()) return@launch

			_state.update { current ->
				current.copy(
					unreadCount = 0,
					notifications = current.notifications.map { it.copy(isRead = true) },
				)
			}
		}
	}

	/**
	 * Jellyfin hands out ids without dashes ("1c241fc6...") which [UUID.fromString] rejects, so use
	 * the SDK parser that understands both spellings.
	 */
	/**
	 * Keeps the user marked online while the profile is on screen, at whatever cadence the server
	 * asks for. Tied to the ViewModel so it stops when the screen goes away - a heartbeat left
	 * running would leave a TV showing its owner as permanently online.
	 */
	private fun startHeartbeat(intervalSeconds: Int) {
		if (heartbeatJob?.isActive == true) return

		heartbeatJob = viewModelScope.launch {
			while (isActive) {
				socialRepository.heartbeat()
				delay(intervalSeconds * 1000L)
			}
		}
	}

	override fun onCleared() {
		heartbeatJob?.cancel()
		super.onCleared()
	}

	private fun runCatchingUuid(value: String) = runCatching { value.toUUID() }.getOrNull()
}
