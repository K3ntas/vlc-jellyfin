package org.jellyfin.androidtv.data.social

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Models for the social endpoints of the Jellyfin Ratings plugin (`/Ratings/Social/...`).
 *
 * Note these use camelCase, unlike the rating endpoints in [org.jellyfin.androidtv.data.ratings]
 * which are PascalCase. The social controller returns C# anonymous objects, and the plugin's own
 * web client reads them as camelCase. Every field carries a default so an unexpected shape yields
 * empty values rather than throwing.
 */

/** A media item pinned to a profile. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FavoriteItem(
	@SerialName("itemId") @JsonNames("ItemId") val itemId: String = "",
	@SerialName("title") @JsonNames("Title") val title: String = "",
	@SerialName("imageUrl") @JsonNames("ImageUrl") val imageUrl: String = "",
	@SerialName("notInLibrary") @JsonNames("NotInLibrary") val notInLibrary: Boolean = false,
	@SerialName("tmdbId") @JsonNames("TmdbId") val tmdbId: String = "",
	@SerialName("year") @JsonNames("Year") val year: Int? = null,
	@SerialName("mediaType") @JsonNames("MediaType") val mediaType: String = "",
	@SerialName("overview") @JsonNames("Overview") val overview: String = "",
)

/** A named row of favourites, which maps directly onto a leanback row. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FavoriteRow(
	@SerialName("title") @JsonNames("Title") val title: String = "",
	@SerialName("items") @JsonNames("Items") val items: List<FavoriteItem> = emptyList(),
)

/** Who may see a given part of a profile: "Everyone", "Friends" or "Nobody". */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PrivacySettings(
	@SerialName("showFriendsList") @JsonNames("ShowFriendsList") val showFriendsList: String = "Everyone",
	@SerialName("showWatchedHistory") @JsonNames("ShowWatchedHistory") val showWatchedHistory: String = "Everyone",
	@SerialName("showRatings") @JsonNames("ShowRatings") val showRatings: String = "Everyone",
)

/** A user profile. Returned whole by MyProfile and Profile/{userId}. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SocialProfile(
	@SerialName("userId") @JsonNames("UserId") val userId: String = "",
	@SerialName("username") @JsonNames("Username") val username: String = "",
	@SerialName("bio") @JsonNames("Bio") val bio: String = "",
	@SerialName("avatarUrl") @JsonNames("AvatarUrl") val avatarUrl: String = "",
	@SerialName("headerMediaUrl") @JsonNames("HeaderMediaUrl") val headerMediaUrl: String = "",
	@SerialName("headerMediaType") @JsonNames("HeaderMediaType") val headerMediaType: String = "",
	@SerialName("createdAt") @JsonNames("CreatedAt") val createdAt: String = "",
	@SerialName("privacy") @JsonNames("Privacy") val privacy: PrivacySettings? = null,
	@SerialName("favorites") @JsonNames("Favorites") val favorites: List<FavoriteItem> = emptyList(),
	@SerialName("favoriteRows") @JsonNames("FavoriteRows") val favoriteRows: List<FavoriteRow> = emptyList(),
)

/** Aggregate counters shown across the top of a profile. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ProfileStats(
	@SerialName("friendsCount") @JsonNames("FriendsCount") val friendsCount: Int = 0,
	@SerialName("ratingsCount") @JsonNames("RatingsCount") val ratingsCount: Int = 0,
	@SerialName("reviewsCount") @JsonNames("ReviewsCount") val reviewsCount: Int = 0,
	@SerialName("averageRating") @JsonNames("AverageRating") val averageRating: Double = 0.0,
	@SerialName("memberDays") @JsonNames("MemberDays") val memberDays: Int = 0,
	@SerialName("moviesWatched") @JsonNames("MoviesWatched") val moviesWatched: Int = 0,
	@SerialName("seriesWatched") @JsonNames("SeriesWatched") val seriesWatched: Int = 0,
	@SerialName("totalWatchHours") @JsonNames("TotalWatchHours") val totalWatchHours: Int = 0,
)

/** Per-user profile theming. Only the parts that translate to Android views are modelled. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ProfileStyle(
	@SerialName("backgroundColor") @JsonNames("BackgroundColor") val backgroundColor: String = "#1a1a2e",
	@SerialName("accentColor") @JsonNames("AccentColor") val accentColor: String = "#00d4ff",
	@SerialName("usernameColor") @JsonNames("UsernameColor") val usernameColor: String = "#ffffff",
	@SerialName("bioColor") @JsonNames("BioColor") val bioColor: String = "#a0a0a0",
	@SerialName("statsNumberColor") @JsonNames("StatsNumberColor") val statsNumberColor: String = "#ffffff",
	@SerialName("statsLabelColor") @JsonNames("StatsLabelColor") val statsLabelColor: String = "#808080",
	@SerialName("cardBackgroundColor") @JsonNames("CardBackgroundColor") val cardBackgroundColor: String = "#2a2a3e",
	@SerialName("cardTextColor") @JsonNames("CardTextColor") val cardTextColor: String = "#ffffff",
	@SerialName("sectionHeaderColor") @JsonNames("SectionHeaderColor") val sectionHeaderColor: String = "#a0a0a0",
	@SerialName("ratingStarsColor") @JsonNames("RatingStarsColor") val ratingStarsColor: String = "#ffd700",
	@SerialName("likeColor") @JsonNames("LikeColor") val likeColor: String = "#ff6b6b",
	@SerialName("reviewTextColor") @JsonNames("ReviewTextColor") val reviewTextColor: String = "#d0d0d0",
	@SerialName("cardBorderRadius") @JsonNames("CardBorderRadius") val cardBorderRadius: Int = 8,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Friend(
	@SerialName("userId") @JsonNames("UserId") val userId: String = "",
	@SerialName("username") @JsonNames("Username") val username: String = "Unknown",
	@SerialName("avatarUrl") @JsonNames("AvatarUrl") val avatarUrl: String = "",
	@SerialName("friendsSince") @JsonNames("FriendsSince") val friendsSince: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FriendsResponse(
	@SerialName("friends") @JsonNames("Friends") val friends: List<Friend> = emptyList(),
	@SerialName("totalCount") @JsonNames("TotalCount") val totalCount: Int = 0,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FriendRequest(
	@SerialName("id") @JsonNames("Id") val id: String = "",
	@SerialName("fromUserId") @JsonNames("FromUserId") val fromUserId: String = "",
	@SerialName("fromUsername") @JsonNames("FromUsername") val fromUsername: String = "",
	@SerialName("toUserId") @JsonNames("ToUserId") val toUserId: String = "",
	@SerialName("toUsername") @JsonNames("ToUsername") val toUsername: String = "",
	@SerialName("status") @JsonNames("Status") val status: String = "pending",
	@SerialName("createdAt") @JsonNames("CreatedAt") val createdAt: String = "",
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FriendRequestsResponse(
	@SerialName("requests") @JsonNames("Requests") val requests: List<FriendRequest> = emptyList(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SocialNotification(
	@SerialName("id") @JsonNames("Id") val id: String = "",
	@SerialName("type") @JsonNames("Type") val type: String = "",
	@SerialName("title") @JsonNames("Title") val title: String = "",
	@SerialName("message") @JsonNames("Message") val message: String = "",
	@SerialName("isRead") @JsonNames("IsRead") val isRead: Boolean = false,
	@SerialName("createdAt") @JsonNames("CreatedAt") val createdAt: String = "",
	@SerialName("data") @JsonNames("Data") val data: Map<String, String> = emptyMap(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NotificationsResponse(
	@SerialName("notifications") @JsonNames("Notifications") val notifications: List<SocialNotification> = emptyList(),
	@SerialName("unreadCount") @JsonNames("UnreadCount") val unreadCount: Int = 0,
)

/** Result of liking or unliking a profile. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ProfileLikesResponse(
	@SerialName("likeCount") @JsonNames("LikeCount") val likeCount: Int = 0,
	@SerialName("userLiked") @JsonNames("UserLiked") val userLiked: Boolean = false,
)

/** One slice of the taste breakdown: how many minutes watched in a genre. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GenreSlice(
	@SerialName("name") @JsonNames("Name") val name: String = "",
	@SerialName("minutes") @JsonNames("Minutes") val minutes: Long = 0,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GenresResponse(
	@SerialName("hasData") @JsonNames("HasData") val hasData: Boolean = false,
	@SerialName("genres") @JsonNames("Genres") val genres: List<GenreSlice> = emptyList(),
	@SerialName("totalMinutes") @JsonNames("TotalMinutes") val totalMinutes: Long = 0,
	@SerialName("itemCount") @JsonNames("ItemCount") val itemCount: Int = 0,
)

/**
 * Followers and following. The list is keyed by which endpoint was called, so both names are
 * declared and only one is ever populated. `count` is the page size, `total` the full count.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FollowListResponse(
	@SerialName("followers") val followers: List<Friend> = emptyList(),
	@SerialName("following") val following: List<Friend> = emptyList(),
	@SerialName("count") val count: Int = 0,
	@SerialName("total") val total: Int = 0,
	@SerialName("offset") val offset: Int = 0,
	@SerialName("limit") val limit: Int = 0,
)

// ---------------------------------------------------------------------------------------------
// Endpoints added in plugin 1.0.369. Everything here degrades to null on older servers, so the
// client falls back to the per-section calls above.
// ---------------------------------------------------------------------------------------------

@Serializable
data class PresenceConfig(
	@SerialName("heartbeatSeconds") val heartbeatSeconds: Int = 30,
	@SerialName("onlineWithinSeconds") val onlineWithinSeconds: Int = 60,
	@SerialName("awayWithinSeconds") val awayWithinSeconds: Int = 300,
	@SerialName("offlineAfterSeconds") val offlineAfterSeconds: Int = 300,
)

@Serializable
data class CapabilityFlags(
	@SerialName("ratings") val ratings: Boolean = true,
	@SerialName("social") val social: Boolean = true,
	@SerialName("chat") val chat: Boolean = false,
	@SerialName("requests") val requests: Boolean = true,
)

@Serializable
data class Capabilities(
	@SerialName("plugin") val plugin: String = "",
	@SerialName("version") val version: String = "",
	@SerialName("features") val features: List<String> = emptyList(),
	@SerialName("enabled") val enabled: CapabilityFlags = CapabilityFlags(),
	@SerialName("presence") val presence: PresenceConfig = PresenceConfig(),
	@SerialName("jsonCasing") val jsonCasing: String = "camelCase",
) {
	fun supports(feature: String) = feature in features
}

@Serializable
data class FullStats(
	@SerialName("totalRatings") val totalRatings: Int = 0,
	@SerialName("averageRating") val averageRating: Double = 0.0,
	@SerialName("reviewCount") val reviewCount: Int = 0,
	@SerialName("watchedMinutes") val watchedMinutes: Long = 0,
	@SerialName("watchedItems") val watchedItems: Int = 0,
)

@Serializable
data class FullCounts(
	@SerialName("friends") val friends: Int = 0,
	@SerialName("followers") val followers: Int = 0,
	@SerialName("following") val following: Int = 0,
	@SerialName("profileLikes") val profileLikes: Int = 0,
)

@Serializable
data class ViewerRelation(
	@SerialName("isSelf") val isSelf: Boolean = false,
	@SerialName("isFriend") val isFriend: Boolean = false,
	@SerialName("isFollowing") val isFollowing: Boolean = false,
)

@Serializable
data class TopGenre(
	@SerialName("name") val name: String = "",
	@SerialName("percent") val percent: Double = 0.0,
)

/** One aggregate call replacing profile + stats + style + counts + genres + distribution. */
@Serializable
data class ProfileFull(
	@SerialName("userId") val userId: String = "",
	@SerialName("username") val username: String = "",
	@SerialName("bio") val bio: String = "",
	@SerialName("avatarUrl") val avatarUrl: String = "",
	@SerialName("headerMediaUrl") val headerMediaUrl: String = "",
	@SerialName("headerMediaType") val headerMediaType: String = "",
	@SerialName("createdAt") val createdAt: String = "",
	@SerialName("style") val style: ProfileStyle = ProfileStyle(),
	@SerialName("onlineStatus") val onlineStatus: String = "",
	@SerialName("stats") val stats: FullStats = FullStats(),
	@SerialName("ratingDistribution") val ratingDistribution: List<Int> = emptyList(),
	@SerialName("topGenres") val topGenres: List<TopGenre> = emptyList(),
	@SerialName("counts") val counts: FullCounts = FullCounts(),
	@SerialName("viewer") val viewer: ViewerRelation = ViewerRelation(),
	@SerialName("favorites") val favorites: List<FavoriteItem> = emptyList(),
	@SerialName("favoriteRows") val favoriteRows: List<FavoriteRow> = emptyList(),
)

/** A rated item with its library metadata already resolved by the server. */
@Serializable
data class RatedItem(
	@SerialName("id") val id: String = "",
	@SerialName("itemId") val itemId: String = "",
	@SerialName("rating") val rating: Int = 0,
	@SerialName("review") val review: String = "",
	@SerialName("title") val title: String = "",
	@SerialName("year") val year: Int? = null,
	@SerialName("mediaType") val mediaType: String = "",
	/** Server-relative, e.g. "/Items/{id}/Images/Primary" - prefix the server address. */
	@SerialName("imageUrl") val imageUrl: String = "",
	@SerialName("inLibrary") val inLibrary: Boolean = true,
	@SerialName("createdAt") val createdAt: String = "",
)

@Serializable
data class RatedItemsPage(
	@SerialName("total") val total: Int = 0,
	@SerialName("offset") val offset: Int = 0,
	@SerialName("limit") val limit: Int = 0,
	@SerialName("items") val items: List<RatedItem> = emptyList(),
)

/** What another user is playing right now, for the presence line on a match card. */
@Serializable
data class WatchingInfo(
	@SerialName("itemId") val itemId: String = "",
	@SerialName("title") val title: String = "",
	@SerialName("seriesName") val seriesName: String = "",
	@SerialName("episodeInfo") val episodeInfo: String = "",
)

@Serializable
data class SimilarUser(
	@SerialName("userId") val userId: String = "",
	@SerialName("username") val username: String = "",
	@SerialName("matchPercent") val matchPercent: Int = 0,
	@SerialName("sharedGenres") val sharedGenres: List<String> = emptyList(),
	@SerialName("isFriend") val isFriend: Boolean = false,
	@SerialName("isOnline") val isOnline: Boolean = false,
	@SerialName("status") val status: String = "Offline",
	@SerialName("watching") val watching: WatchingInfo? = null,
)

@Serializable
data class SimilarUsersResponse(
	@SerialName("hasData") val hasData: Boolean = false,
	@SerialName("matches") val matches: List<SimilarUser> = emptyList(),
)

/** An entry in the OTHER USERS list. */
@Serializable
data class DirectoryUser(
	@SerialName("userId") val userId: String = "",
	@SerialName("username") val username: String = "",
	@SerialName("isFriend") val isFriend: Boolean = false,
	@SerialName("isOnline") val isOnline: Boolean = false,
)

@Serializable
data class DirectoryResponse(
	@SerialName("users") val users: List<DirectoryUser> = emptyList(),
	@SerialName("total") val total: Int = 0,
)
