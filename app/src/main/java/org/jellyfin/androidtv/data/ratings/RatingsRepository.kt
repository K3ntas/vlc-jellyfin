package org.jellyfin.androidtv.data.ratings

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for interacting with the Jellyfin Ratings Plugin API.
 *
 * The plugin provides custom community ratings (1-10 scale) stored on the Jellyfin server.
 * API endpoints: /Ratings/Items/{itemId}/...
 */
class RatingsRepository(
    private val api: ApiClient,
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // In-memory cache for rating stats
    private val statsCache = ConcurrentHashMap<String, RatingStats>()

    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Get rating statistics for an item.
     * Returns cached value if available.
     */
    suspend fun getRatingStats(itemId: UUID, forceRefresh: Boolean = false): RatingStats? {
        val itemIdStr = itemId.toString()

        // Return cached if available and not forcing refresh
        if (!forceRefresh && statsCache.containsKey(itemIdStr)) {
            return statsCache[itemIdStr]
        }

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = api.baseUrl ?: return@withContext null
                val url = "$baseUrl/Ratings/Items/$itemIdStr/Stats"

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addAuthHeaders()
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    Timber.d("Rating stats response body: $body")
                    if (!body.isNullOrBlank()) {
                        val stats = json.decodeFromString<RatingStats>(body)
                        Timber.d("Parsed rating stats: $stats")
                        // Cache the result
                        statsCache[itemIdStr] = stats
                        return@withContext stats
                    }
                } else {
                    Timber.d("Failed to get rating stats for $itemIdStr: ${response.code}")
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error getting rating stats")
            } catch (e: Exception) {
                Timber.e(e, "Error parsing rating stats")
            }
            null
        }
    }

    /**
     * Submit or update a rating for an item with optional review.
     *
     * @param itemId The item to rate
     * @param rating Rating value (1-10)
     * @param review Optional review text (max 2000 chars)
     * @return true if successful, false otherwise
     */
    suspend fun submitRating(itemId: UUID, rating: Int, review: String? = null): Boolean {
        require(rating in 1..10) { "Rating must be between 1 and 10" }

        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = api.baseUrl ?: return@withContext false
                val itemIdStr = itemId.toString()

                // Build URL with rating and optional review (parameter is "review", not "reviewText")
                val urlBuilder = StringBuilder("$baseUrl/Ratings/Items/$itemIdStr/Rating?rating=$rating")
                if (!review.isNullOrBlank()) {
                    val encodedReview = java.net.URLEncoder.encode(review, "UTF-8")
                    urlBuilder.append("&review=$encodedReview")
                }
                val url = urlBuilder.toString()

                Timber.i("RATING_API: Submitting to $url")

                val request = Request.Builder()
                    .url(url)
                    .post("".toRequestBody("application/json".toMediaType()))
                    .addAuthHeaders()
                    .build()

                val response = okHttpClient.newCall(request).execute()
                Timber.i("RATING_API: Response code=${response.code}")

                if (response.isSuccessful) {
                    statsCache.remove(itemIdStr)
                    return@withContext true
                } else {
                    Timber.e("RATING_API: Failed: ${response.code}")
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error submitting rating")
            } catch (e: Exception) {
                Timber.e(e, "Error submitting rating")
            }
            false
        }
    }

    /**
     * Update review text only (without changing rating).
     * User must have an existing rating.
     *
     * @param itemId The item ID
     * @param review New review text (empty string to clear)
     * @return true if successful, false otherwise
     */
    suspend fun updateReview(itemId: UUID, review: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = api.baseUrl ?: return@withContext false
                val itemIdStr = itemId.toString()
                val encodedReview = java.net.URLEncoder.encode(review, "UTF-8")
                val url = "$baseUrl/Ratings/Items/$itemIdStr/Review?review=$encodedReview"

                Timber.d("Updating review for item $itemIdStr")

                val request = Request.Builder()
                    .url(url)
                    .put("".toRequestBody("application/json".toMediaType()))
                    .addAuthHeaders()
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    statsCache.remove(itemIdStr)
                    Timber.d("Review updated successfully")
                    return@withContext true
                } else {
                    Timber.e("Failed to update review: ${response.code}")
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error updating review")
            } catch (e: Exception) {
                Timber.e(e, "Error updating review")
            }
            false
        }
    }

    /**
     * Like or dislike a review.
     *
     * @param reviewerUserId User ID who wrote the review
     * @param itemId The item ID
     * @param isLike true for like, false for dislike
     * @return LikeResponse on success, null on error
     */
    suspend fun likeReview(reviewerUserId: String, itemId: UUID, isLike: Boolean): LikeResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = api.baseUrl ?: return@withContext null
                val url = "$baseUrl/Ratings/Reviews/$reviewerUserId/${itemId}/Like?isLike=$isLike"

                Timber.d("Submitting ${if (isLike) "like" else "dislike"} for review by $reviewerUserId")

                val request = Request.Builder()
                    .url(url)
                    .post("".toRequestBody("application/json".toMediaType()))
                    .addAuthHeaders()
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val likeResponse = json.decodeFromString<LikeResponse>(body)
                        Timber.d("Like response: $likeResponse")
                        return@withContext likeResponse
                    }
                } else {
                    Timber.e("Failed to like review: ${response.code} - ${response.body?.string()}")
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error liking review")
            } catch (e: Exception) {
                Timber.e(e, "Error liking review")
            }
            null
        }
    }

    /**
     * Delete the current user's rating for an item.
     */
    suspend fun deleteRating(itemId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = api.baseUrl ?: return@withContext false
                val itemIdStr = itemId.toString()
                val url = "$baseUrl/Ratings/Items/$itemIdStr/Rating"

                val request = Request.Builder()
                    .url(url)
                    .delete()
                    .addAuthHeaders()
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    // Invalidate cache for this item
                    statsCache.remove(itemIdStr)
                    return@withContext true
                } else {
                    Timber.e("Failed to delete rating: ${response.code}")
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error deleting rating")
            } catch (e: Exception) {
                Timber.e(e, "Error deleting rating")
            }
            false
        }
    }

    /**
     * Get detailed ratings for an item (all user ratings).
     */
    suspend fun getDetailedRatings(itemId: UUID): List<UserRatingDetail>? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = api.baseUrl ?: return@withContext null
                val url = "$baseUrl/Ratings/Items/${itemId}/DetailedRatings"

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addAuthHeaders()
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    Timber.i("RATING_API: DetailedRatings response body: $body")
                    if (body != null) {
                        val result = json.decodeFromString<List<UserRatingDetail>>(body)
                        Timber.i("RATING_API: Parsed ${result.size} ratings, first one hasReview=${result.firstOrNull()?.hasReview}, reviewText='${result.firstOrNull()?.reviewText}'")
                        return@withContext result
                    }
                } else {
                    Timber.i("RATING_API: Failed to get detailed ratings: ${response.code}")
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error getting detailed ratings")
            } catch (e: Exception) {
                Timber.e(e, "Error parsing detailed ratings")
            }
            null
        }
    }

    /**
     * Get all ratings by the current user.
     */
    suspend fun getMyRatings(): List<UserRating>? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = api.baseUrl ?: return@withContext null
                val url = "$baseUrl/Ratings/MyRatings"

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addAuthHeaders()
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        return@withContext json.decodeFromString<List<UserRating>>(body)
                    }
                } else {
                    Timber.d("Failed to get my ratings: ${response.code}")
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error getting my ratings")
            } catch (e: Exception) {
                Timber.e(e, "Error parsing my ratings")
            }
            null
        }
    }

    /**
     * Clear the ratings cache.
     */
    fun clearCache() {
        statsCache.clear()
    }

    /**
     * Get cached rating stats without making a network call.
     * Returns null if not in cache.
     */
    fun getCachedStats(itemId: UUID): RatingStats? {
        return statsCache[itemId.toString()]
    }

    /**
     * Fetch rating stats asynchronously and invoke callback on UI thread.
     * Used by Java code that cannot use coroutines directly.
     */
    fun fetchStatsAsync(itemId: UUID, callback: (RatingStats?) -> Unit) {
        scope.launch {
            val stats = getRatingStats(itemId)
            mainHandler.post { callback(stats) }
        }
    }

    /**
     * Get comments for a review.
     */
    suspend fun getReviewComments(reviewerUserId: String, itemId: UUID): List<ReviewComment>? {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = api.baseUrl ?: return@withContext null
                val url = "$baseUrl/Ratings/Reviews/$reviewerUserId/${itemId}/Comments"

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addAuthHeaders()
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        return@withContext json.decodeFromString<List<ReviewComment>>(body)
                    }
                } else {
                    Timber.d("Failed to get review comments: ${response.code}")
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error getting review comments")
            } catch (e: Exception) {
                Timber.e(e, "Error parsing review comments")
            }
            null
        }
    }

    /**
     * Add a comment to a review.
     */
    suspend fun addComment(reviewerUserId: String, itemId: UUID, commentText: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrl = api.baseUrl ?: return@withContext false
                val encodedComment = java.net.URLEncoder.encode(commentText, "UTF-8")
                val url = "$baseUrl/Ratings/Reviews/$reviewerUserId/${itemId}/Comments?text=$encodedComment"

                Timber.d("Adding comment to review by $reviewerUserId")

                val request = Request.Builder()
                    .url(url)
                    .post("".toRequestBody("application/json".toMediaType()))
                    .addAuthHeaders()
                    .build()

                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    Timber.d("Comment added successfully")
                    return@withContext true
                } else {
                    Timber.e("Failed to add comment: ${response.code}")
                }
            } catch (e: IOException) {
                Timber.e(e, "Network error adding comment")
            } catch (e: Exception) {
                Timber.e(e, "Error adding comment")
            }
            false
        }
    }

    /**
     * Add authentication headers to the request.
     */
    private fun Request.Builder.addAuthHeaders(): Request.Builder {
        val token = api.accessToken
        val deviceInfo = api.deviceInfo
        val clientInfo = api.clientInfo

        if (token != null && deviceInfo != null && clientInfo != null) {
            val authHeader = buildString {
                append("MediaBrowser ")
                append("Client=\"${clientInfo.name}\", ")
                append("Device=\"${deviceInfo.name}\", ")
                append("DeviceId=\"${deviceInfo.id}\", ")
                append("Version=\"${clientInfo.version}\", ")
                append("Token=\"$token\"")
            }
            addHeader("X-Emby-Authorization", authHeader)
        }

        return this
    }
}
