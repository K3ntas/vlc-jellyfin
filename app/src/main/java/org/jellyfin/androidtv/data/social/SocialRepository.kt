package org.jellyfin.androidtv.data.social

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.io.IOException
import java.util.UUID

/**
 * Client for the social endpoints of the Jellyfin Ratings plugin, served under `/Social`.
 *
 * Every call returns null or false when the plugin is missing, unreachable, or refuses the
 * request, so callers can treat "no plugin" and "no data" the same way and simply hide the UI.
 * See [isAvailable].
 */
class SocialRepository(
	private val api: ApiClient,
	private val okHttpClient: OkHttpClient,
) {
	private val json = Json {
		ignoreUnknownKeys = true
		isLenient = true
	}

	private val emptyBody = "".toRequestBody("application/json".toMediaType())

	private var cachedCapabilities: Capabilities? = null

	/**
	 * Whether the server has the ratings plugin installed. A server without it has no /Social
	 * routes at all, so the profile request 404s and this returns false.
	 */
	suspend fun isAvailable(): Boolean = getMyProfile() != null

	/** Feature and presence config, cached for the session. Null on servers without the endpoint. */
	suspend fun getCapabilities(): Capabilities? {
		cachedCapabilities?.let { return it }
		return get<Capabilities>("Capabilities")?.also { cachedCapabilities = it }
	}

	/** The aggregate profile call. Null on older servers, which need the per-section calls. */
	suspend fun getProfileFull(userId: UUID): ProfileFull? = get("Profile/$userId/Full")

	suspend fun getUserRatings(userId: UUID, limit: Int = 30, offset: Int = 0): RatedItemsPage? =
		get("Profile/$userId/Ratings?limit=$limit&offset=$offset")

	suspend fun getUserReviews(userId: UUID, limit: Int = 30, offset: Int = 0): RatedItemsPage? =
		get("Profile/$userId/Reviews?limit=$limit&offset=$offset")

	suspend fun getUserActivity(userId: UUID, limit: Int = 30): RatedItemsPage? =
		get("Profile/$userId/Activity?limit=$limit")

	suspend fun getDirectoryUsers(limit: Int = 100): DirectoryResponse? = get("Users?limit=$limit")

	suspend fun getSimilarUsers(userId: UUID, limit: Int = 5): SimilarUsersResponse? =
		get("Profile/$userId/SimilarUsers?limit=$limit")

	/**
	 * Turns the server-relative imageUrl the API returns into something loadable, and asks the
	 * server to size it for a card rather than sending the full asset. Items whose media was
	 * deleted come back with an absolute TMDB url instead, which is passed through untouched.
	 */
	fun imageUrl(relativeOrAbsolute: String, maxHeight: Int = 270): String? {
		if (relativeOrAbsolute.isBlank()) return null
		if (relativeOrAbsolute.startsWith("http")) return relativeOrAbsolute

		val baseUrl = api.baseUrl?.trimEnd('/') ?: return null
		return "$baseUrl$relativeOrAbsolute?maxHeight=$maxHeight&quality=90"
	}

	suspend fun getMyProfile(): SocialProfile? = get("MyProfile")

	suspend fun getProfile(userId: UUID): SocialProfile? = get("Profile/$userId")

	suspend fun getProfileStats(userId: UUID): ProfileStats? = get("Profile/$userId/Stats")

	suspend fun getProfileStyle(userId: UUID): ProfileStyle? = get("Profile/$userId/Style")

	suspend fun getFriends(): FriendsResponse? = get("Friends")

	suspend fun getGenres(userId: UUID): GenresResponse? = get("Profile/$userId/Genres")

	suspend fun getFollowers(userId: UUID): FollowListResponse? = get("Profile/$userId/Followers")

	suspend fun getFollowing(userId: UUID): FollowListResponse? = get("Profile/$userId/Following")

	suspend fun getIncomingFriendRequests(): FriendRequestsResponse? =
		get("FriendRequests/Incoming")

	suspend fun getOutgoingFriendRequests(): FriendRequestsResponse? =
		get("FriendRequests/Outgoing")

	suspend fun getNotifications(unreadOnly: Boolean = false, limit: Int = 20): NotificationsResponse? =
		get("Notifications?unreadOnly=$unreadOnly&limit=$limit")

	suspend fun getProfileLikes(userId: UUID): ProfileLikesResponse? = get("Profile/$userId/Likes")

	suspend fun acceptFriendRequest(requestId: String): Boolean =
		send("FriendRequest/$requestId/Accept", "POST")

	suspend fun rejectFriendRequest(requestId: String): Boolean =
		send("FriendRequest/$requestId/Reject", "POST")

	suspend fun sendFriendRequest(targetUserId: UUID): Boolean =
		send("FriendRequest/$targetUserId", "POST")

	suspend fun removeFriend(friendUserId: UUID): Boolean =
		send("Friend/$friendUserId", "DELETE")

	suspend fun likeProfile(userId: UUID): Boolean = send("Profile/$userId/Like", "POST")

	suspend fun unlikeProfile(userId: UUID): Boolean = send("Profile/$userId/Like", "DELETE")

	suspend fun markNotificationRead(notificationId: String): Boolean =
		send("Notifications/$notificationId/Read", "POST")

	suspend fun markAllNotificationsRead(): Boolean = send("Notifications/ReadAll", "POST")

	/** Tells the server this client is alive, which drives the online-status indicators. */
	suspend fun heartbeat(): Boolean = send("Heartbeat", "POST")

	private suspend inline fun <reified T> get(path: String): T? = withContext(Dispatchers.IO) {
		val baseUrl = api.baseUrl ?: return@withContext null

		try {
			val request = Request.Builder()
				.url("$baseUrl/Social/$path")
				.get()
				.addAuthHeaders()
				.build()

			okHttpClient.newCall(request).execute().use { response ->
				if (!response.isSuccessful) {
					Timber.d("Social GET %s failed: %d", path, response.code)
					return@withContext null
				}

				val body = response.body?.string()
				if (body.isNullOrBlank()) return@withContext null

				Timber.d("Social GET %s -> %s", path, body.take(400))
				return@withContext json.decodeFromString<T>(body)
			}
		} catch (e: IOException) {
			Timber.d(e, "Social GET %s: network error", path)
		} catch (e: Exception) {
			Timber.w(e, "Social GET %s: could not parse response", path)
		}

		null
	}

	private suspend fun send(path: String, method: String): Boolean = withContext(Dispatchers.IO) {
		val baseUrl = api.baseUrl ?: return@withContext false

		try {
			val builder = Request.Builder().url("$baseUrl/Social/$path").addAuthHeaders()
			when (method) {
				"POST" -> builder.post(emptyBody)
				"DELETE" -> builder.delete()
				else -> builder.get()
			}

			okHttpClient.newCall(builder.build()).execute().use { response ->
				if (!response.isSuccessful) Timber.d("Social %s %s failed: %d", method, path, response.code)
				return@withContext response.isSuccessful
			}
		} catch (e: IOException) {
			Timber.d(e, "Social %s %s: network error", method, path)
		} catch (e: Exception) {
			Timber.w(e, "Social %s %s failed", method, path)
		}

		false
	}

	/**
	 * The social controller resolves the caller with `_sessionManager.GetSessionByAuthenticationToken`
	 * on the raw `X-Emby-Token` header, so that one is required - the `MediaBrowser ...` header the
	 * ratings endpoints accept is ignored there and every call comes back 401 without it.
	 */
	private fun Request.Builder.addAuthHeaders(): Request.Builder {
		val token = api.accessToken
		val deviceInfo = api.deviceInfo
		val clientInfo = api.clientInfo

		if (token != null) addHeader("X-Emby-Token", token)

		if (token != null && deviceInfo != null && clientInfo != null) {
			val value = "MediaBrowser Client=\"${clientInfo.name}\", " +
				"Device=\"${deviceInfo.name}\", " +
				"DeviceId=\"${deviceInfo.id}\", " +
				"Version=\"${clientInfo.version}\", " +
				"Token=\"$token\""

			// Current Jellyfin reads Authorization; X-Emby-Authorization is the legacy name and is
			// sent as well so this keeps working against older servers.
			addHeader("Authorization", value)
			addHeader("X-Emby-Authorization", value)
		}

		return this
	}
}
