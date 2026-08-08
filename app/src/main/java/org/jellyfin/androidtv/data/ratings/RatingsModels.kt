package org.jellyfin.androidtv.data.ratings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Individual user rating for a media item.
 */
@Serializable
data class UserRating(
    @SerialName("Id") val id: String = "",
    @SerialName("UserId") val userId: String = "",
    @SerialName("ItemId") val itemId: String = "",
    @SerialName("Rating") val rating: Int = 0, // 1-10
    @SerialName("CreatedAt") val createdAt: String = "",
    @SerialName("UpdatedAt") val updatedAt: String = "",
)

/**
 * Aggregated rating statistics for a media item.
 * Supports both PascalCase (from .NET API) and camelCase field names.
 */
@Serializable
data class RatingStats(
    @SerialName("ItemId") val itemId: String = "",
    @SerialName("AverageRating") val averageRating: Double = 0.0,
    @SerialName("TotalRatings") val totalRatings: Int = 0,
    @SerialName("UserRating") val userRating: Int? = null, // Current user's rating (nullable)
    @SerialName("Distribution") val distribution: List<Int> = emptyList(), // Count distribution for ratings 1-10
)

/**
 * Display format for user ratings with username and review.
 */
@Serializable
data class UserRatingDetail(
    @SerialName("UserId") val userId: String,
    @SerialName("Username") val username: String,
    @SerialName("Rating") val rating: Int,
    @SerialName("CreatedAt") val createdAt: String,
    @SerialName("ReviewText") val reviewText: String? = null,
    @SerialName("HasReview") val hasReview: Boolean = false,
    @SerialName("LikeCount") val likeCount: Int = 0,
    @SerialName("DislikeCount") val dislikeCount: Int = 0,
    @SerialName("UserLiked") val userLiked: Boolean? = null,  // true=liked, false=disliked, null=no vote
    @SerialName("CommentCount") val commentCount: Int = 0
)

/**
 * Comment on a review.
 */
@Serializable
data class ReviewComment(
    @SerialName("Id") val id: String = "",
    @SerialName("CommenterId") val commenterId: String = "",
    @SerialName("CommenterName") val commenterName: String = "",
    @SerialName("Text") val text: String = "",
    @SerialName("CreatedAt") val createdAt: String = ""
)

/**
 * Response from like/dislike endpoint.
 */
@Serializable
data class LikeResponse(
    @SerialName("LikeCount") val likeCount: Int,
    @SerialName("DislikeCount") val dislikeCount: Int,
    @SerialName("UserLiked") val userLiked: Boolean?
)

/**
 * Response from rating submission with review.
 */
@Serializable
data class RatingSubmitResponse(
    @SerialName("Id") val id: String = "",
    @SerialName("UserId") val userId: String = "",
    @SerialName("ItemId") val itemId: String = "",
    @SerialName("Rating") val rating: Int = 0,
    @SerialName("ReviewText") val reviewText: String? = null,
    @SerialName("CreatedAt") val createdAt: String = "",
    @SerialName("UpdatedAt") val updatedAt: String = ""
)

/**
 * Plugin configuration from server.
 */
@Serializable
data class RatingsConfig(
    val enableRatings: Boolean = true,
    val minRating: Int = 1,
    val maxRating: Int = 10,
)
