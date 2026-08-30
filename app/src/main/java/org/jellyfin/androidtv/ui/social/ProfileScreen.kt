package org.jellyfin.androidtv.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import org.jellyfin.androidtv.data.social.FavoriteItem
import org.jellyfin.androidtv.data.social.Friend
import org.jellyfin.androidtv.data.social.FriendRequest
import org.jellyfin.androidtv.data.social.GenreSlice
import org.jellyfin.androidtv.data.social.ProfileStats
import org.jellyfin.androidtv.data.social.ProfileStyle
import org.jellyfin.androidtv.data.social.SocialNotification
import org.jellyfin.androidtv.ui.base.ProfilePicture
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import androidx.compose.foundation.Canvas
import org.jellyfin.androidtv.data.social.DirectoryUser
import org.jellyfin.androidtv.data.social.RatedItem
import org.jellyfin.androidtv.data.social.SimilarUser
import org.jellyfin.androidtv.data.social.SocialRepository
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.shared.toolbar.ToolbarSearchViewModel
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.model.serializer.toUUID
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.util.UUID

/**
 * Profile screen backed by the ratings plugin's social API.
 *
 * This deliberately does not copy the plugin's web layout. A TV is driven by a D-pad with one
 * focused element, so the page is laid out as vertically stacked, horizontally scrolling rows.
 * The user's own colours from [ProfileStyle] are honoured; the parts of that style which are
 * purely CSS - shadows, font stacks, hover effects - have no equivalent here and are ignored.
 */
/** Room for the content list once the header has been scrolled past. */
private val CONTENT_HEIGHT = 620.dp

@Composable
fun ProfileScreen(userId: UUID? = null, modifier: Modifier = Modifier) {
	val viewModel = koinViewModel<ProfileViewModel>()
	val socialRepository = koinInject<SocialRepository>()
	val state by viewModel.state.collectAsStateWithLifecycle()

	LaunchedEffect(userId) { viewModel.load(userId) }

	val style = state.style
	val background = style.backgroundColor.toColorOr(Color(0xFF1A1A2E))

	// The page scrolls as a whole. The header alone is most of a screen, so with a fixed page the
	// content below it had almost no height left: focus walked down into cards that were never
	// brought on screen, and the page appeared stuck at the top.
	Column(
		modifier = modifier
			.fillMaxSize()
			.background(background)
			.verticalScroll(rememberScrollState())
	) {
		if (state.loading) {
			Text(
				text = "Loading profile...",
				color = style.bioColor.toColorOr(Color.Gray),
				modifier = Modifier.padding(48.dp),
			)
			return@Column
		}

		val profile = state.profile
		if (profile == null) {
			Text(
				text = if (state.unreachable) {
					"Could not load this profile. The server answered, but the profile request failed - check the server log."
				} else {
					"Profile unavailable. This server does not have the ratings plugin installed."
				},
				color = style.bioColor.toColorOr(Color.Gray),
				modifier = Modifier.padding(48.dp),
			)
			return@Column
		}

		ProfileHeader(
			username = profile.username,
			bio = profile.bio,
			onlineStatus = state.onlineStatus,
			memberSince = profile.createdAt,
			// Both come back server-relative, so they need the server address in front
			avatarUrl = socialRepository.imageUrl(profile.avatarUrl, maxHeight = 200).orEmpty(),
			headerUrl = socialRepository.imageUrl(profile.headerMediaUrl, maxHeight = 480).orEmpty(),
			style = style,
			likeCount = state.likeCount,
			userLiked = state.userLiked,
			showLike = state.isOtherUser,
			onToggleLike = viewModel::toggleLike,
		)

		TopStatsBar(
			ratings = state.stats?.ratingsCount ?: 0,
			reviews = state.stats?.reviewsCount ?: 0,
			following = state.followingCount,
			followers = state.followersCount,
			likes = state.likeCount,
			style = style,
		)

		val tabs = buildList {
			add("OVERVIEW")
			if (state.ratings.isNotEmpty()) add("RATINGS")
			if (state.reviews.isNotEmpty()) add("REVIEWS")
			if (state.activity.isNotEmpty() || state.notifications.isNotEmpty()) add("ACTIVITY")
			if (state.following.isNotEmpty()) add("FOLLOWING")
			if (state.followers.isNotEmpty()) add("FOLLOWERS")
			if (state.friends.isNotEmpty()) add("FRIENDS")
			if (!state.isOtherUser && state.incomingRequests.isNotEmpty()) add("REQUESTS")
			if (state.otherUsers.isNotEmpty()) add("OTHER USERS")
		}

		var selectedTab by remember { mutableIntStateOf(0) }
		val activeTab = tabs.getOrNull(selectedTab)

		Row(
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			modifier = Modifier.padding(start = 48.dp, top = 4.dp, bottom = 12.dp),
		) {
			tabs.forEachIndexed { index, title ->
				val selected = index == selectedTab
				Box(
					modifier = Modifier
						.profileFocusable(
							accent = style.accentColor.toColorOr(Color(0xFF00D4FF)),
							cornerRadius = 20,
							onClick = { selectedTab = index },
						)
						.clip(RoundedCornerShape(20.dp))
						.background(
							if (selected) style.accentColor.toColorOr(Color(0xFF00D4FF))
							else Color(0x22FFFFFF)
						)
						.padding(horizontal = 18.dp, vertical = 8.dp)
				) {
					Text(
						text = title,
						color = if (selected) Color.Black else style.cardTextColor.toColorOr(Color.White),
						fontSize = 15.sp,
						fontWeight = FontWeight.Bold,
					)
				}
			}
		}

		// An explicit height rather than fillMaxSize: inside a scrolling column the available
		// height is unbounded, and a lazy list needs a real one to measure against.
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(CONTENT_HEIGHT)
		) {
			// The content list is the only unbounded part of the page, so it is the part that has to
			// be lazy. A plain scrolling Column composed and measured every favourite row, every
			// review and every user even when far off screen.
			LazyColumn(
				modifier = Modifier.weight(1f),
				contentPadding = PaddingValues(bottom = 32.dp),
			) {
				when (activeTab) {
					"RATINGS" -> {
						item { SectionHeader("Ratings (" + state.ratings.size + ")", style) }
						ratedRows(state.ratings, style)
					}

					"REVIEWS" -> {
						item { SectionHeader("Reviews (" + state.reviews.size + ")", style) }
						items(state.reviews) { ReviewRow(it, style) }
					}

					"OTHER USERS" -> {
						item { SectionHeader("Other users (" + state.otherUsers.size + ")", style) }
						items(state.otherUsers) { DirectoryUserRow(it, style) }
					}

					"FOLLOWING" -> {
						item { SectionHeader("Following (" + state.following.size + ")", style) }
						item { FriendsRow(state.following, style) }
					}

					"FOLLOWERS" -> {
						item { SectionHeader("Followers (" + state.followers.size + ")", style) }
						item { FriendsRow(state.followers, style) }
					}

					"FRIENDS" -> {
						item { SectionHeader("Friends (" + state.friends.size + ")", style) }
						item { FriendsRow(state.friends, style) }
					}

					"REQUESTS" -> {
						item { SectionHeader("Friend requests", style) }
						items(state.incomingRequests) { request ->
							FriendRequestRow(
								request = request,
								style = style,
								onAccept = { viewModel.acceptRequest(request) },
								onReject = { viewModel.rejectRequest(request) },
							)
						}
					}

					"ACTIVITY" -> {
						if (state.activity.isNotEmpty()) {
							item { SectionHeader("Recent activity", style) }
							ratedRows(state.activity, style)
						}

						if (state.notifications.isNotEmpty()) {
							item { SectionHeader("Notifications (" + state.unreadCount + " unread)", style) }
							item {
								Button(
									onClick = viewModel::markAllRead,
									modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
								) {
									Text("Mark all read")
								}
							}
							items(state.notifications.take(15)) { NotificationRow(it, style) }
						}
					}

					else -> {
						val rows = profile.favoriteRows.filter { it.items.isNotEmpty() }

						if (rows.isEmpty() && profile.favorites.isEmpty()) {
							item {
								Text(
									text = "Nothing pinned to this profile yet.",
									color = style.bioColor.toColorOr(Color.Gray),
									modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp),
								)
							}
						}

						items(rows) { row ->
							Column {
								SectionHeader(row.title.ifBlank { "Favourites" } + "  (" + row.items.size + ")", style)
								PosterRow(row.items, style)
							}
						}

						if (rows.isEmpty() && profile.favorites.isNotEmpty()) {
							item {
								Column {
									SectionHeader("Favourites  (" + profile.favorites.size + ")", style)
									PosterRow(profile.favorites, style)
								}
							}
						}

						if (state.activity.isNotEmpty()) {
							item {
								Column {
									SectionHeader("RECENT ACTIVITY", style)
									RatedPosterRow(state.activity.take(10), style)
								}
							}
						}

						if (state.reviews.isNotEmpty()) {
							item { SectionHeader("RECENT REVIEWS", style) }
							items(state.reviews.take(4)) { ReviewRow(it, style) }
						}
					}
				}
			}

			// The sidebar is a fixed handful of cards, so it stays eager. It only belongs on the
			// overview, exactly as on the web page.
			if (activeTab == null || activeTab == "OVERVIEW") {
				Column(
					verticalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier
						.width(320.dp)
						.padding(end = 48.dp, top = 10.dp)
						.verticalScroll(rememberScrollState()),
				) {
					AddAFilmCard(style)

					state.stats?.let { SidebarStats(it, style) }

					if (state.ratingDistribution.any { c -> c > 0 }) {
						SidebarCard("RATINGS", style) {
							RatingsBarChart(
								distribution = state.ratingDistribution,
								barColor = style.accentColor.toColorOr(Color(0xFF00D4FF)),
								labelColor = style.statsLabelColor.toColorOr(Color.Gray),
							)
						}
					}

					if (state.topGenres.isNotEmpty()) {
						SidebarCard("TASTE", style) {
							TasteDonut(
								genres = state.topGenres,
								palette = donutPalette,
								textColor = style.cardTextColor.toColorOr(Color.White),
								labelColor = style.statsLabelColor.toColorOr(Color.Gray),
							)
						}
					} else if (state.genres.isNotEmpty()) {
						SidebarTaste(state.genres, state.totalGenreMinutes, style)
					}

					if (state.similarUsers.isNotEmpty()) {
						SidebarCard("SIMILAR TASTE", style) {
							state.similarUsers.forEach { SimilarUserRow(it, style) }
						}
					}

					if (state.activity.isNotEmpty()) {
						SidebarCard("ACTIVITY", style) {
							state.activity.take(8).forEach { ActivityLine(it, style) }
						}
					}
				}
			}
		}
	}
}

/** Emits rated posters nine to a row, as lazy items rather than one eager block. */
private fun LazyListScope.ratedRows(rated: List<RatedItem>, style: ProfileStyle) {
	val rows = rated.chunked(9)
	items(rows.size) { index -> RatedPosterRow(rows[index], style) }
}

@Composable
private fun ProfileHeader(
	username: String,
	bio: String,
	onlineStatus: String,
	memberSince: String,
	avatarUrl: String,
	headerUrl: String,
	style: ProfileStyle,
	likeCount: Int,
	userLiked: Boolean,
	showLike: Boolean,
	onToggleLike: () -> Unit,
) {
	Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
		if (headerUrl.isNotBlank()) {
			Image(
				painter = rememberAsyncImagePainter(headerUrl),
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
			)
		}

		Row(
			verticalAlignment = Alignment.Bottom,
			modifier = Modifier
				.align(Alignment.BottomStart)
				.padding(start = 48.dp, bottom = 16.dp, end = 48.dp),
		) {
			if (avatarUrl.isBlank()) {
				// The API returns no avatar url; the web client draws an initial instead of a
				// placeholder, so do the same rather than showing an empty silhouette.
				InitialAvatar(username, style, size = 96)
			} else {
				ProfilePicture(
					url = avatarUrl,
					contentDescription = username,
					modifier = Modifier
						.size(96.dp)
						.clip(RoundedCornerShape(48.dp)),
				)
			}

			Spacer(Modifier.width(20.dp))

			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = username,
					color = style.usernameColor.toColorOr(Color.White),
					fontSize = 30.sp,
					fontWeight = FontWeight.Bold,
				)

				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(top = 4.dp),
				) {
					if (onlineStatus.isNotBlank()) {
						Text(
							text = onlineStatus.uppercase(),
							color = Color.Black,
							fontSize = 11.sp,
							fontWeight = FontWeight.Bold,
							modifier = Modifier
								.clip(RoundedCornerShape(4.dp))
								.background(
									if (onlineStatus.equals("Online", ignoreCase = true)) Color(0xFF22C55E)
									else Color(0xFF9CA3AF)
								)
								.padding(horizontal = 7.dp, vertical = 2.dp),
						)
					}

					val since = remember(memberSince) { formatMemberSince(memberSince) }
					since?.let {
						Text(
							text = "  $it",
							color = style.bioColor.toColorOr(Color.Gray),
							fontSize = 13.sp,
						)
					}
				}

				if (bio.isNotBlank()) {
					Text(
						text = bio,
						color = style.bioColor.toColorOr(Color.Gray),
						fontSize = 15.sp,
						maxLines = 2,
						modifier = Modifier.padding(top = 4.dp),
					)
				}
			}

			if (showLike) {
				Button(
					onClick = onToggleLike,
					colors = ButtonDefaults.colors(
						containerColor = if (userLiked) style.likeColor.toColorOr(Color.Red) else Color(0x33FFFFFF),
					),
				) {
					Text(if (userLiked) "♥ $likeCount" else "♡ $likeCount")
				}
			}
		}
	}
}

@Composable
private fun StatsRow(stats: ProfileStats, style: ProfileStyle) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(36.dp),
		modifier = Modifier.padding(horizontal = 48.dp, vertical = 20.dp),
	) {
		Stat(stats.ratingsCount.toString(), "Ratings", style)
		Stat(stats.reviewsCount.toString(), "Reviews", style)
		Stat(stats.friendsCount.toString(), "Friends", style)
		Stat(String.format("%.1f", stats.averageRating), "Avg rating", style)
		Stat(stats.moviesWatched.toString(), "Movies", style)
		Stat(stats.seriesWatched.toString(), "Series", style)
		Stat("${stats.totalWatchHours}h", "Watched", style)
		Stat(stats.memberDays.toString(), "Days member", style)
	}
}

@Composable
private fun Stat(value: String, label: String, style: ProfileStyle) {
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		Text(
			text = value,
			color = style.statsNumberColor.toColorOr(Color.White),
			fontSize = 24.sp,
			fontWeight = FontWeight.Bold,
		)
		Text(text = label, color = style.statsLabelColor.toColorOr(Color.Gray), fontSize = 12.sp)
	}
}

@Composable
private fun SectionHeader(title: String, style: ProfileStyle) {
	Text(
		text = title,
		color = style.sectionHeaderColor.toColorOr(Color.Gray),
		fontSize = 18.sp,
		fontWeight = FontWeight.Bold,
		modifier = Modifier.padding(start = 48.dp, top = 20.dp, bottom = 10.dp),
	)
}

@Composable
private fun PosterRow(items: List<FavoriteItem>, style: ProfileStyle) {
	val socialRepository = koinInject<SocialRepository>()

	LazyRow(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		contentPadding = PaddingValues(end = 48.dp),
		modifier = Modifier.padding(start = 48.dp),
	) {
		items(items) { item ->
			Column(modifier = Modifier.width(110.dp)) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.aspectRatio(2f / 3f)
						.profileFocusable(
							accent = style.accentColor.toColorOr(Color(0xFF00D4FF)),
							cornerRadius = style.cardBorderRadius,
						)
						.clip(RoundedCornerShape(style.cardBorderRadius.dp))
						.background(style.cardBackgroundColor.toColorOr(Color.DarkGray))
				) {
					val poster = socialRepository.imageUrl(item.imageUrl)
					if (poster != null) {
						Image(
							painter = rememberAsyncImagePainter(poster),
							contentDescription = item.title,
							contentScale = ContentScale.Crop,
							modifier = Modifier.fillMaxSize(),
						)
					}
				}

				Text(
					text = item.title,
					color = style.cardTextColor.toColorOr(Color.White),
					fontSize = 12.sp,
					maxLines = 2,
					modifier = Modifier.padding(top = 6.dp),
				)
			}
		}
	}
}

@Composable
private fun FriendsRow(friends: List<Friend>, style: ProfileStyle) {
	val navigationRepository = koinInject<NavigationRepository>()

	LazyRow(
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		contentPadding = PaddingValues(end = 48.dp),
		modifier = Modifier.padding(start = 48.dp),
	) {
		items(friends) { friend ->
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				modifier = Modifier.width(90.dp),
			) {
				ProfilePicture(
					url = friend.avatarUrl.ifBlank { null },
					contentDescription = friend.username,
					modifier = Modifier
						.size(72.dp)
						.profileFocusable(
							accent = style.accentColor.toColorOr(Color(0xFF00D4FF)),
							cornerRadius = 36,
							onClick = { openProfile(navigationRepository, friend.userId) },
						)
						.clip(RoundedCornerShape(36.dp)),
				)

				Text(
					text = friend.username,
					color = style.cardTextColor.toColorOr(Color.White),
					fontSize = 12.sp,
					maxLines = 1,
					modifier = Modifier.padding(top = 6.dp),
				)
			}
		}
	}
}

@Composable
private fun FriendRequestRow(
	request: FriendRequest,
	style: ProfileStyle,
	onAccept: () -> Unit,
	onReject: () -> Unit,
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp),
	) {
		Text(
			text = request.fromUsername.ifBlank { "Unknown" },
			color = style.cardTextColor.toColorOr(Color.White),
			fontSize = 15.sp,
			modifier = Modifier.width(220.dp),
		)

		Button(
			onClick = onAccept,
			colors = ButtonDefaults.colors(containerColor = style.accentColor.toColorOr(Color(0xFF00D4FF))),
		) {
			Text("Accept")
		}

		Button(onClick = onReject) { Text("Reject") }
	}
}

@Composable
private fun NotificationRow(notification: SocialNotification, style: ProfileStyle) {
	Column(
		modifier = Modifier
			.padding(horizontal = 48.dp, vertical = 4.dp)
			.fillMaxWidth()
			.clip(RoundedCornerShape(style.cardBorderRadius.dp))
			.background(style.cardBackgroundColor.toColorOr(Color.DarkGray))
			.padding(12.dp)
	) {
		Text(
			text = notification.title.ifBlank { notification.type },
			color = if (notification.isRead) {
				style.cardTextColor.toColorOr(Color.White)
			} else {
				style.accentColor.toColorOr(Color(0xFF00D4FF))
			},
			fontSize = 14.sp,
			fontWeight = FontWeight.Bold,
		)

		if (notification.message.isNotBlank()) {
			Text(text = notification.message, color = style.bioColor.toColorOr(Color.Gray), fontSize = 13.sp)
		}
	}
}

/**
 * Parses a CSS hex colour such as "#1a1a2e", falling back when the value is missing or odd.
 *
 * Results are cached: this is called dozens of times per composition, including inside row items,
 * and android.graphics.Color.parseColor does real string work every time otherwise.
 */
private val parsedColors = java.util.concurrent.ConcurrentHashMap<String, Int>()

private fun String.toColorOr(fallback: Color): Color {
	if (isBlank()) return fallback

	val parsed = parsedColors.getOrPut(this) {
		runCatching { android.graphics.Color.parseColor(this) }.getOrDefault(0)
	}

	return if (parsed == 0) fallback else Color(parsed)
}

/** Colours for the taste donut, in the order slices are drawn. */
private val donutPalette = listOf(
	Color(0xFF22C55E),
	Color(0xFF38BDF8),
	Color(0xFFF59E0B),
	Color(0xFFA855F7),
	Color(0xFFEF4444),
	Color(0xFF14B8A6),
)

/** A horizontal row of rated posters with the score underneath, as on the web overview. */
@Composable
private fun RatedPosterRow(items: List<RatedItem>, style: ProfileStyle) {
	val socialRepository = koinInject<SocialRepository>()
	val navigationRepository = koinInject<NavigationRepository>()

	LazyRow(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		contentPadding = PaddingValues(end = 48.dp),
		modifier = Modifier.padding(start = 48.dp),
	) {
		items(items) { item ->
			Column(modifier = Modifier.width(110.dp)) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.aspectRatio(2f / 3f)
						.profileFocusable(
							accent = style.accentColor.toColorOr(Color(0xFF00D4FF)),
							cornerRadius = style.cardBorderRadius,
							onClick = { item.openIn(navigationRepository) },
						)
						.clip(RoundedCornerShape(style.cardBorderRadius.dp))
						.background(style.cardBackgroundColor.toColorOr(Color.DarkGray))
				) {
					socialRepository.imageUrl(item.imageUrl)?.let { url ->
						Image(
							painter = rememberAsyncImagePainter(url),
							contentDescription = item.title,
							contentScale = ContentScale.Crop,
							modifier = Modifier.fillMaxSize(),
						)
					}
				}

				StarRating(
					rating = item.rating,
					color = style.ratingStarsColor.toColorOr(Color(0xFF22C55E)),
					modifier = Modifier.padding(top = 4.dp),
				)
			}
		}
	}
}

/** The RATINGS tab: the same cards, wrapped over several rows. */
@Composable
private fun RatedGrid(items: List<RatedItem>, style: ProfileStyle) {
	val rows = remember(items) { items.chunked(9) }
	rows.forEach { row -> RatedPosterRow(row, style) }
}

/** A review as the web page shows it: thumbnail, title, stars, then the text. */
@Composable
private fun ReviewRow(item: RatedItem, style: ProfileStyle) {
	val socialRepository = koinInject<SocialRepository>()
	val navigationRepository = koinInject<NavigationRepository>()

	Row(
		modifier = Modifier
			.padding(horizontal = 48.dp, vertical = 6.dp)
			.fillMaxWidth()
			.profileFocusable(
				accent = style.accentColor.toColorOr(Color(0xFF00D4FF)),
				cornerRadius = style.cardBorderRadius,
				onClick = { item.openIn(navigationRepository) },
			)
	) {
		Box(
			modifier = Modifier
				.width(56.dp)
				.aspectRatio(2f / 3f)
				.clip(RoundedCornerShape(4.dp))
				.background(style.cardBackgroundColor.toColorOr(Color.DarkGray))
		) {
			socialRepository.imageUrl(item.imageUrl, maxHeight = 120)?.let { url ->
				Image(
					painter = rememberAsyncImagePainter(url),
					contentDescription = item.title,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		Column(modifier = Modifier.padding(start = 14.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = item.title,
					color = style.cardTextColor.toColorOr(Color.White),
					fontSize = 16.sp,
					fontWeight = FontWeight.Bold,
				)
				StarRating(
					rating = item.rating,
					color = style.ratingStarsColor.toColorOr(Color(0xFF22C55E)),
					modifier = Modifier.padding(start = 8.dp),
				)
			}

			if (item.review.isNotBlank()) {
				Text(
					text = item.review,
					color = style.reviewTextColor.toColorOr(Color(0xFFD0D0D0)),
					fontSize = 13.sp,
					maxLines = 3,
					modifier = Modifier.padding(top = 4.dp),
				)
			}
		}
	}
}

/** A SIMILAR TASTE entry: match percentage, shared genres, and a presence dot. */
@Composable
private fun SimilarUserRow(user: SimilarUser, style: ProfileStyle) {
	val navigationRepository = koinInject<NavigationRepository>()

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.profileFocusable(
				accent = style.accentColor.toColorOr(Color(0xFF00D4FF)),
				cornerRadius = 6,
				onClick = { openProfile(navigationRepository, user.userId) },
			)
			.padding(vertical = 5.dp)
	) {
		Canvas(modifier = Modifier.size(8.dp)) {
			drawCircle(color = if (user.isOnline) Color(0xFF22C55E) else Color(0xFF6B7280))
		}

		Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
			Text(
				text = user.username,
				color = style.cardTextColor.toColorOr(Color.White),
				fontSize = 13.sp,
				fontWeight = FontWeight.Bold,
			)

			if (user.sharedGenres.isNotEmpty()) {
				Text(
					text = user.sharedGenres.joinToString(" · "),
					color = style.statsLabelColor.toColorOr(Color.Gray),
					fontSize = 11.sp,
					maxLines = 1,
				)
			}
		}

		Text(
			text = "${user.matchPercent}%",
			color = style.accentColor.toColorOr(Color(0xFF22C55E)),
			fontSize = 13.sp,
			fontWeight = FontWeight.Bold,
		)
	}
}

/** Opens the rated item in the normal detail screen, when it still exists in the library. */
private fun RatedItem.openIn(navigationRepository: NavigationRepository) {
	if (!inLibrary) return
	val id = runCatching { itemId.toUUID() }.getOrNull() ?: return
	navigationRepository.navigate(Destinations.itemDetails(id))
}

/** Turns the ISO timestamp the API returns into the web page's "Member since Apr 11, 2026". */
private fun formatMemberSince(createdAt: String): String? {
	if (createdAt.isBlank()) return null

	return runCatching {
		val date = java.time.OffsetDateTime.parse(createdAt).toLocalDate()
		"Member since " + date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
	}.getOrNull()
}

/** A row in the OTHER USERS list, with a presence dot and a friend marker. */
@Composable
private fun DirectoryUserRow(user: DirectoryUser, style: ProfileStyle) {
	val navigationRepository = koinInject<NavigationRepository>()

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.padding(horizontal = 48.dp, vertical = 5.dp)
			.fillMaxWidth()
			.profileFocusable(
				accent = style.accentColor.toColorOr(Color(0xFF00D4FF)),
				cornerRadius = style.cardBorderRadius,
				onClick = { openProfile(navigationRepository, user.userId) },
			)
			.padding(6.dp)
	) {
		Canvas(modifier = Modifier.size(9.dp)) {
			drawCircle(color = if (user.isOnline) Color(0xFF22C55E) else Color(0xFF6B7280))
		}

		Text(
			text = "  ${user.username}",
			color = style.cardTextColor.toColorOr(Color.White),
			fontSize = 15.sp,
		)

		if (user.isFriend) {
			Text(
				text = "  FRIEND",
				color = style.accentColor.toColorOr(Color(0xFF22C55E)),
				fontSize = 10.sp,
				fontWeight = FontWeight.Bold,
			)
		}
	}
}

/** One line of the sidebar ACTIVITY card: "Rated <title> ★★★☆☆   3 d ago". */
@Composable
private fun ActivityLine(item: RatedItem, style: ProfileStyle) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 4.dp)
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = "Rated ${item.title}",
				color = style.cardTextColor.toColorOr(Color.White),
				fontSize = 12.sp,
				maxLines = 2,
			)
			StarRating(
				rating = item.rating,
				color = style.ratingStarsColor.toColorOr(Color(0xFF22C55E)),
				size = 11,
			)
		}

		val ago = remember(item.createdAt) { relativeDays(item.createdAt) }
		ago?.let {
			Text(
				text = it,
				color = style.statsLabelColor.toColorOr(Color.Gray),
				fontSize = 11.sp,
			)
		}
	}
}

/**
 * The web page has an "add a film" search box here. Typing on a remote is miserable, so this opens
 * the app's own D-pad search overlay instead of putting a text field on the page.
 */
@Composable
private fun AddAFilmCard(style: ProfileStyle) {
	val searchViewModel = koinViewModel<ToolbarSearchViewModel>()

	SidebarCard("ADD A FILM", style) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.profileFocusable(
					accent = style.accentColor.toColorOr(Color(0xFF00D4FF)),
					cornerRadius = style.cardBorderRadius,
					onClick = { searchViewModel.showOverlay() },
				)
				.clip(RoundedCornerShape(style.cardBorderRadius.dp))
				.background(Color(0x22FFFFFF))
				.padding(horizontal = 12.dp, vertical = 10.dp)
		) {
			Text(
				text = "Search title…",
				color = style.statsLabelColor.toColorOr(Color.Gray),
				fontSize = 13.sp,
			)
		}
	}
}

/** "3 d ago" style relative age, as the web activity list shows. */
private fun relativeDays(createdAt: String): String? {
	if (createdAt.isBlank()) return null

	return runCatching {
		val then = java.time.OffsetDateTime.parse(createdAt)
		val days = java.time.Duration.between(then, java.time.OffsetDateTime.now()).toDays()
		when {
			days <= 0L -> "today"
			days == 1L -> "1 d ago"
			else -> "$days d ago"
		}
	}.getOrNull()
}

/** Opens another member's profile. Ids arrive dashless, so parse with the SDK helper. */
private fun openProfile(navigationRepository: NavigationRepository, userId: String) {
	val id = runCatching { userId.toUUID() }.getOrNull() ?: return
	navigationRepository.navigate(Destinations.profile(id))
}

/** The counter strip under the header, matching the web page's order. */
@Composable
private fun TopStatsBar(
	ratings: Int,
	reviews: Int,
	following: Int,
	followers: Int,
	likes: Int,
	style: ProfileStyle,
) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(48.dp),
		modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
	) {
		Stat(ratings.toString(), "RATINGS", style)
		Stat(reviews.toString(), "REVIEWS", style)
		Stat(following.toString(), "FOLLOWING", style)
		Stat(followers.toString(), "FOLLOWERS", style)
		Stat("\u2665 $likes", "LIKES", style)
	}
}

/** The STATS card: films, shows, hours and average, laid out two by two. */
@Composable
private fun SidebarStats(stats: ProfileStats, style: ProfileStyle) {
	SidebarCard("STATS", style) {
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			Stat(stats.moviesWatched.toString(), "FILMS", style)
			Stat(stats.seriesWatched.toString(), "SHOWS", style)
		}
		Spacer(Modifier.height(12.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			Stat(stats.totalWatchHours.toString(), "HOURS", style)
			Stat(String.format("%.1f", stats.averageRating), "AVG", style)
		}
	}
}

/** Fallback taste list for servers that only expose raw genre minutes. */
@Composable
private fun SidebarTaste(genres: List<GenreSlice>, totalMinutes: Long, style: ProfileStyle) {
	SidebarCard("TASTE", style) {
		genres.take(6).forEach { genre ->
			val percent = if (totalMinutes > 0) genre.minutes * 100.0 / totalMinutes else 0.0

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(vertical = 3.dp)
			) {
				Text(
					text = genre.name,
					color = style.cardTextColor.toColorOr(Color.White),
					fontSize = 13.sp,
					modifier = Modifier.weight(1f),
				)
				Text(
					text = String.format("%.1f%%", percent),
					color = style.accentColor.toColorOr(Color(0xFF00D4FF)),
					fontSize = 13.sp,
					fontWeight = FontWeight.Bold,
				)
			}
		}
	}
}

@Composable
private fun SidebarCard(title: String, style: ProfileStyle, content: @Composable ColumnScope.() -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(style.cardBorderRadius.dp))
			.background(style.cardBackgroundColor.toColorOr(Color(0xFF2A2A3E)))
			.padding(14.dp)
	) {
		Text(
			text = title,
			color = style.sectionHeaderColor.toColorOr(Color.Gray),
			fontSize = 12.sp,
			fontWeight = FontWeight.Bold,
			modifier = Modifier.padding(bottom = 10.dp),
		)
		content()
	}
}

/** A coloured circle with the member's initial, matching what the web profile draws. */
@Composable
private fun InitialAvatar(username: String, style: ProfileStyle, size: Int) {
	val accent = style.accentColor.toColorOr(Color(0xFF22C55E))

	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier
			.size(size.dp)
			.clip(RoundedCornerShape(size.dp))
			.background(accent),
	) {
		Text(
			text = username.trim().take(1).uppercase().ifBlank { "?" },
			color = Color.Black,
			fontSize = (size / 2).sp,
			fontWeight = FontWeight.Bold,
		)
	}
}
