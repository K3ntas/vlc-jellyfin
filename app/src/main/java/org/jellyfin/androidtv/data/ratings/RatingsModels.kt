package org.jellyfin.androidtv.data.ratings

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Models for the rating endpoints of the Jellyfin Ratings plugin (`/Ratings/...`).
 *
 * The plugin answers these in **camelCase**, not the PascalCase a .NET API is usually assumed to
 * produce. Every field therefore names the camelCase form and accepts the PascalCase one as an
 * alternative, so the client keeps working whichever way a given plugin build serialises.
 *
 * Getting this wrong is silent rather than loud: the reader is configured with
 * `ignoreUnknownKeys`, so a model whose names do not match does not fail - it returns an object
 * with every field left at its default. That reads as "this item has no ratings" and looks
 * identical to a server that genuinely has none.
 *
 * Every field keeps a default so an unexpected shape yields empty values rather than throwing.
 */

/** Individual user rating for a media item. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UserRating(
    @SerialName("id") @JsonNames("Id") val id: String = "",
    @SerialName("userId") @JsonNames("UserId") val userId: String = "",
    @SerialName("itemId") @JsonNames("ItemId") val itemId: String = "",
    @SerialName("rating") @JsonNames("Rating") val rating: Int = 0, // 1-10
    @SerialName("createdAt") @JsonNames("CreatedAt") val createdAt: String = "",
    @SerialName("updatedAt") @JsonNames("UpdatedAt") val updatedAt: String = "",
)

/** Aggregated rating statistics for a media item. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RatingStats(
    @SerialName("itemId") @JsonNames("ItemId") val itemId: String = "",
    @SerialName("averageRating") @JsonNames("AverageRating") val averageRating: Double = 0.0,
    @SerialName("totalRatings") @JsonNames("TotalRatings") val totalRatings: Int = 0,
    // Current user's rating, absent from the response entirely when they have not rated
    @SerialName("userRating") @JsonNames("UserRating") val userRating: Int? = null,
    // Count per rating value, 1-10
    @SerialName("distribution") @JsonNames("Distribution") val distribution: List<Int> = emptyList(),
)

/** Display format for user ratings with username and review. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UserRatingDetail(
    @SerialName("userId") @JsonNames("UserId") val userId: String = "",
    @SerialName("username") @JsonNames("Username") val username: String = "",
    @SerialName("rating") @JsonNames("Rating") val rating: Int = 0,
    @SerialName("createdAt") @JsonNames("CreatedAt") val createdAt: String = "",
    @SerialName("reviewText") @JsonNames("ReviewText") val reviewText: String? = null,
    @SerialName("hasReview") @JsonNames("HasReview") val hasReview: Boolean = false,
    @SerialName("likeCount") @JsonNames("LikeCount") val likeCount: Int = 0,
    @SerialName("dislikeCount") @JsonNames("DislikeCount") val dislikeCount: Int = 0,
    // true = liked, false = disliked, null = no vote
    @SerialName("userLiked") @JsonNames("UserLiked") val userLiked: Boolean? = null,
    @SerialName("commentCount") @JsonNames("CommentCount") val commentCount: Int = 0,
)

/** Comment on a review. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ReviewComment(
    @SerialName("id") @JsonNames("Id") val id: String = "",
    @SerialName("commenterId") @JsonNames("CommenterId") val commenterId: String = "",
    @SerialName("commenterName") @JsonNames("CommenterName") val commenterName: String = "",
    @SerialName("text") @JsonNames("Text") val text: String = "",
    @SerialName("createdAt") @JsonNames("CreatedAt") val createdAt: String = "",
)

/** Response from like/dislike endpoint. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LikeResponse(
    @SerialName("likeCount") @JsonNames("LikeCount") val likeCount: Int = 0,
    @SerialName("dislikeCount") @JsonNames("DislikeCount") val dislikeCount: Int = 0,
    @SerialName("userLiked") @JsonNames("UserLiked") val userLiked: Boolean? = null,
)

/** Response from rating submission with review. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RatingSubmitResponse(
    @SerialName("id") @JsonNames("Id") val id: String = "",
    @SerialName("userId") @JsonNames("UserId") val userId: String = "",
    @SerialName("itemId") @JsonNames("ItemId") val itemId: String = "",
    @SerialName("rating") @JsonNames("Rating") val rating: Int = 0,
    @SerialName("reviewText") @JsonNames("ReviewText") val reviewText: String? = null,
    @SerialName("createdAt") @JsonNames("CreatedAt") val createdAt: String = "",
    @SerialName("updatedAt") @JsonNames("UpdatedAt") val updatedAt: String = "",
)

/** Plugin configuration from server. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RatingsConfig(
    @SerialName("enableRatings") @JsonNames("EnableRatings") val enableRatings: Boolean = true,
    @SerialName("minRating") @JsonNames("MinRating") val minRating: Int = 1,
    @SerialName("maxRating") @JsonNames("MaxRating") val maxRating: Int = 10,
)
