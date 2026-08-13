package org.jellyfin.androidtv.ui.card;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.BaseCardView;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.databinding.ViewCardLegacyImageBinding;
import org.jellyfin.androidtv.ui.AsyncImageView;
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem;
import org.jellyfin.androidtv.data.trailer.YouTubeStreamResolver;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.util.ContextExtensionsKt;
import org.jellyfin.androidtv.util.DateTimeExtensionsKt;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.sdk.api.client.ApiClient;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.koin.java.KoinJavaComponent;

import org.jellyfin.androidtv.util.ArtworkPalette;

import java.text.NumberFormat;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Modified ImageCard with no fade on the badge
 * A card view with an {@link ImageView} as its main region.
 */
public class LegacyImageCardView extends BaseCardView {
    private ViewCardLegacyImageBinding binding = ViewCardLegacyImageBinding.inflate(LayoutInflater.from(getContext()), this);
    private ImageView mBanner;
    private int BANNER_SIZE = Utils.convertDpToPixel(getContext(), 50);
    private int noIconMargin = Utils.convertDpToPixel(getContext(), 5);
    private NumberFormat nf = NumberFormat.getInstance();
    private boolean mShowOverlayOnFocus = false;
    private String mOverlayName = null;
    private GradientDrawable mFocusFrame = null;
    private ValueAnimator mFocusFrameAnimator = null;
    private BaseItemDto mPreviewItem = null;
    private TrailerPreviewController mTrailerController = null;
    private ValueAnimator mTrailerExpandAnimator = null;
    /** The artwork's own width, remembered so the card can close back to it after a preview. */
    private int mCollapsedImageWidth = 0;

    /** Trailers are widescreen, so the card takes that shape while one plays. */
    private static final float TRAILER_ASPECT = 16f / 9f;
    private static final long TRAILER_EXPAND_MS = 300L;

    private ValueAnimator mDepthAnimator = null;
    private Runnable mTrailerProgressTick = null;

    /** Base hue of the focus frame, taken from the artwork once it has been analysed. */
    private float mFrameBaseHue = (float) (Math.random() * 360f);

    /** Palette work is short but not free, and never needs to run in parallel with itself. */
    private static final Executor PALETTE_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * How far either side of the artwork's own hue the frame drifts. Wide enough to stay alive,
     * narrow enough that the frame still reads as the colour of the poster it surrounds.
     */
    private static final float FOCUS_FRAME_SWING = 25f;

    /** A brief tilt as focus lands, so the card reads as being pushed rather than just scaled. */
    private static final float DEPTH_TILT_DEGREES = 11f;
    private static final long DEPTH_SETTLE_MS = 560L;

    /** Long enough to be a fade rather than a switch, short enough to keep up with the D-pad. */
    private static final long FOCUS_FRAME_FADE_MS = 220L;

    private static final long TRAILER_PROGRESS_INTERVAL_MS = 500L;

    /**
     * Enough to read the name against a moving picture, not enough to sit on top of it. Low
     * because the shadow behind the text carries most of the legibility, so the glyphs themselves
     * do not have to be bright.
     */
    private static final float TRAILER_TITLE_ALPHA = 0.45f;

    /** One full trip around the colour wheel, slow enough to read as a drift rather than a flash. */
    private static final long FOCUS_FRAME_CYCLE_MS = 12000L;
    /** Held back from fully saturated so the frame stays easy on the eye against artwork. */
    private static final float FOCUS_FRAME_SATURATION = 0.7f;

    public LegacyImageCardView(Context context, boolean showInfo) {
        super(context, null, androidx.leanback.R.attr.imageCardViewStyle);

        if (!showInfo) {
            setCardType(CARD_TYPE_MAIN_ONLY);
        }

        binding.mainImage.setClipToOutline(true);

        // Without a deep camera the settle tilt reads as the card folding rather than leaning
        setCameraDistance(getResources().getDisplayMetrics().density * 8000f);

        // "hack" to trigger KeyProcessor to open the menu for this item on long press
        setOnLongClickListener(v -> {
            Activity activity = ContextExtensionsKt.getActivity(getContext());
            if (activity == null) return false;
            // Make sure the view is focused so the created menu uses it as anchor
            if (!v.requestFocus()) return false;
            return activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));
        });

        setForeground(null);

        // Focus listener for Netflix-style overlay
        setOnFocusChangeListener((v, hasFocus) -> {
            // Frame the artwork so the focused card is obvious while moving along a row. This is
            // driven from here rather than a state selector because focus lands on the card view
            // while the frame belongs on the image inside it.
            //
            // Faded rather than switched: appearing and vanishing outright makes moving along a
            // row feel like a series of jumps, even though the card itself is animating.
            binding.focusFrame.animate().cancel();

            if (hasFocus) {
                binding.focusFrame.setBackground(getFocusFrame());
                binding.focusFrame.animate().alpha(1f).setDuration(FOCUS_FRAME_FADE_MS).start();
            } else {
                binding.focusFrame.animate()
                        .alpha(0f)
                        .setDuration(FOCUS_FRAME_FADE_MS)
                        .withEndAction(() -> binding.focusFrame.setBackground(null))
                        .start();
            }

            if (hasFocus) {
                startFocusFrameAnimation();
                startDepthSettle();
                startTrailerDwell();
            } else {
                stopFocusFrameAnimation();
                stopDepthSettle();
                stopTrailerPreview();
            }

            if (mShowOverlayOnFocus && mOverlayName != null) {
                if (hasFocus) {
                    binding.overlayText.setText(mOverlayName);
                    binding.nameOverlay.setVisibility(VISIBLE);
                    binding.nameOverlay.setAlpha(0f);
                    binding.nameOverlay.animate().alpha(1f).setDuration(200).start();
                    // Enable marquee scrolling
                    binding.overlayText.setSelected(true);
                } else {
                    binding.nameOverlay.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                        binding.nameOverlay.setVisibility(GONE);
                        binding.overlayText.setSelected(false);
                    }).start();
                }
            }
        });
    }

    /** Lazily loaded and cached, since cards are recycled constantly while scrolling rows. */
    private GradientDrawable getFocusFrame() {
        if (mFocusFrame == null) {
            // mutate() so recycled cards never end up sharing one drawable's stroke colour.
            mFocusFrame = (GradientDrawable) getContext().getDrawable(R.drawable.card_focus_frame).mutate();

            // The image clips itself to a rounded outline of ?attr/cardRounding, while
            // GradientDrawable insets its path by half the stroke and keeps the radius as given.
            // That leaves the stroke's outer edge rounder than the outline, so the corners of the
            // artwork sit outside the frame. Pulling the radius in by half the stroke width lands
            // the outer edge exactly on the card's outline.
            float strokeWidth = getResources().getDimensionPixelSize(R.dimen.card_focus_frame_width);
            mFocusFrame.setCornerRadius(Math.max(0f, resolveCardRounding() - strokeWidth / 2f));
        }

        return mFocusFrame;
    }

    /** Card rounding is a theme attribute, so it differs between the bundled themes. */
    private float resolveCardRounding() {
        TypedValue value = new TypedValue();

        if (!getContext().getTheme().resolveAttribute(R.attr.cardRounding, value, true)) return 0f;

        return TypedValue.complexToDimension(value.data, getResources().getDisplayMetrics());
    }

    /**
     * Walks the frame slowly around the colour wheel for as long as the card holds focus, starting
     * from a random hue so it does not look like the same canned loop on every card.
     */
    private void startFocusFrameAnimation() {
        stopFocusFrameAnimation();

        GradientDrawable frame = getFocusFrame();
        int strokeWidth = getResources().getDimensionPixelSize(R.dimen.card_focus_frame_width);

        resolveAccentHue();

        mFocusFrameAnimator = ValueAnimator.ofFloat(0f, 360f);
        mFocusFrameAnimator.setDuration(FOCUS_FRAME_CYCLE_MS);
        mFocusFrameAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mFocusFrameAnimator.setInterpolator(new LinearInterpolator());
        mFocusFrameAnimator.addUpdateListener(animation -> {
            // Swings either side of the artwork's own hue rather than touring the whole wheel, so
            // the frame stays recognisably the colour of what it is framing
            double phase = Math.toRadians((float) animation.getAnimatedValue());
            float hue = (mFrameBaseHue + (float) Math.sin(phase) * FOCUS_FRAME_SWING + 360f) % 360f;
            frame.setStroke(strokeWidth, Color.HSVToColor(new float[]{hue, FOCUS_FRAME_SATURATION, 1f}));
        });
        mFocusFrameAnimator.start();
    }

    /**
     * Takes the frame's base hue from the artwork, so the glow belongs to the poster it surrounds.
     *
     * Extraction runs once per item and off the main thread. Until it lands the previous hue keeps
     * being used: focus moves far faster than a bitmap can be analysed, and a frame that blinks to
     * a default colour on the way past every card would be worse than one that lags a moment.
     */
    private void resolveAccentHue() {
        String key = paletteKey();
        if (key == null) return;

        Integer cached = ArtworkPalette.INSTANCE.cached(key);
        if (cached != null) {
            mFrameBaseHue = hueOf(cached);
            return;
        }

        Drawable artwork = binding.mainImage.getDrawable();
        if (artwork == null) return;

        PALETTE_EXECUTOR.execute(() -> {
            Integer accent = ArtworkPalette.INSTANCE.accentFor(key, artwork);
            if (accent == null) return;

            post(() -> {
                // Cards recycle, so this may now be showing something else entirely
                if (key.equals(paletteKey())) mFrameBaseHue = hueOf(accent);
            });
        });
    }

    private String paletteKey() {
        if (mPreviewItem != null && mPreviewItem.getId() != null) return mPreviewItem.getId().toString();

        // Rows that carry no item still get a stable key for as long as the artwork is loaded
        Drawable artwork = binding.mainImage.getDrawable();
        return artwork == null ? null : String.valueOf(System.identityHashCode(artwork));
    }

    private static float hueOf(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv[0];
    }

    /**
     * Tips the card back a few degrees and lets it settle flat as focus arrives.
     *
     * Driven by a ValueAnimator rather than animate(), because leanback drives the focus scale
     * through its own ViewPropertyAnimator on this same view and the two would fight.
     */
    private void startDepthSettle() {
        stopDepthSettle();

        mDepthAnimator = ValueAnimator.ofFloat(-DEPTH_TILT_DEGREES, 0f);
        mDepthAnimator.setDuration(DEPTH_SETTLE_MS);
        // A gentle overshoot: enough to feel like the card settles, not enough to bounce
        mDepthAnimator.setInterpolator(new OvershootInterpolator(0.7f));
        mDepthAnimator.addUpdateListener(animation -> setRotationX((float) animation.getAnimatedValue()));
        mDepthAnimator.start();
    }

    private void stopDepthSettle() {
        if (mDepthAnimator != null) {
            mDepthAnimator.cancel();
            mDepthAnimator = null;
        }

        setRotationX(0f);
    }

    private void stopFocusFrameAnimation() {
        if (mFocusFrameAnimator != null) {
            mFocusFrameAnimator.cancel();
            mFocusFrameAnimator = null;
        }
    }

    /**
     * Asks for a trailer once the card has held focus long enough. Nothing is requested while
     * moving along a row; see TrailerPreviewController for why resolution is deferred.
     */
    private void startTrailerDwell() {
        if (mPreviewItem == null) return;

        if (mTrailerController == null) {
            ApiClient api = KoinJavaComponent.get(ApiClient.class);
            YouTubeStreamResolver resolver = KoinJavaComponent.get(YouTubeStreamResolver.class);
            mTrailerController = new TrailerPreviewController(api, resolver);
        }

        mTrailerController.onFocused(mPreviewItem, url -> {
            binding.trailerPreview.setVisibility(VISIBLE);
            binding.trailerPreview.setAlpha(0f);

            TrailerPreviewPlayer.INSTANCE.play(getContext(), this, binding.trailerPreview, url, () -> {
                // Only reveal once frames are actually arriving, so the card never flashes black
                // or opens out for a trailer that turns out not to play
                binding.trailerPreview.animate().alpha(1f).setDuration(400).start();
                expandForTrailer();
                showTrailerOverlays();
                return kotlin.Unit.INSTANCE;
            });

            return kotlin.Unit.INSTANCE;
        });
    }

    private void stopTrailerPreview() {
        if (mTrailerController != null) mTrailerController.cancel();

        TrailerPreviewPlayer.INSTANCE.stop(this);
        binding.trailerPreview.setVisibility(GONE);
        binding.trailerPreview.setAlpha(0f);
        hideTrailerOverlays();
        collapseAfterTrailer();
    }

    /**
     * Brings up the furniture that turns a video playing in a card into something that looks
     * intentional: edges pulled down, the name of what is playing, and a hint of how far in it is.
     */
    private void showTrailerOverlays() {
        binding.trailerVignette.setVisibility(VISIBLE);
        binding.trailerVignette.setAlpha(0f);
        binding.trailerVignette.animate().alpha(1f).setDuration(400).start();

        String name = mPreviewItem != null ? mPreviewItem.getName() : null;
        if (name != null) {
            Integer year = mPreviewItem.getProductionYear();
            binding.trailerTitle.setText(year != null ? name + "  ·  " + year : name);
            binding.trailerTitle.setVisibility(VISIBLE);
            binding.trailerTitle.setAlpha(0f);
            // Held below full white: the label is there to say what is playing, not to compete
            // with the picture it sits on. Trails the video slightly so it arrives onto something.
            binding.trailerTitle.animate()
                    .alpha(TRAILER_TITLE_ALPHA)
                    .setStartDelay(250)
                    .setDuration(400)
                    .start();
        }

        binding.trailerProgress.setProgress(0);
        binding.trailerProgress.setVisibility(VISIBLE);
        startTrailerProgressTicker();
    }

    private void hideTrailerOverlays() {
        stopTrailerProgressTicker();

        binding.trailerVignette.animate().cancel();
        binding.trailerVignette.setVisibility(GONE);
        binding.trailerTitle.animate().cancel();
        binding.trailerTitle.setVisibility(GONE);
        binding.trailerProgress.setVisibility(GONE);
    }

    private void startTrailerProgressTicker() {
        stopTrailerProgressTicker();

        mTrailerProgressTick = new Runnable() {
            @Override
            public void run() {
                float progress = TrailerPreviewPlayer.INSTANCE.progressOf(LegacyImageCardView.this);
                binding.trailerProgress.setProgress(Math.round(progress * binding.trailerProgress.getMax()));
                postDelayed(this, TRAILER_PROGRESS_INTERVAL_MS);
            }
        };

        post(mTrailerProgressTick);
    }

    private void stopTrailerProgressTicker() {
        if (mTrailerProgressTick == null) return;

        removeCallbacks(mTrailerProgressTick);
        mTrailerProgressTick = null;
    }

    /**
     * Widens the card to a widescreen shape for the duration of the preview.
     *
     * Trailers are 16:9 while most cards are portrait posters, so the artwork's proportions are
     * wrong for video. The height is left alone and only the width grows: the row's other cards
     * keep their baseline, and the card opens out sideways rather than the whole row shifting.
     */
    private void expandForTrailer() {
        ViewGroup.LayoutParams lp = binding.mainImage.getLayoutParams();

        if (mCollapsedImageWidth == 0) mCollapsedImageWidth = lp.width;

        int target = Math.round(lp.height * TRAILER_ASPECT);
        // Thumbnail cards are already wide enough, and shrinking one would look like a glitch
        if (target <= mCollapsedImageWidth) return;

        animateImageWidth(target);
    }

    private void collapseAfterTrailer() {
        if (mCollapsedImageWidth == 0) return;

        animateImageWidth(mCollapsedImageWidth);
    }

    private void animateImageWidth(int target) {
        cancelTrailerExpansion();

        ViewGroup.LayoutParams lp = binding.mainImage.getLayoutParams();
        if (lp.width == target) return;

        mTrailerExpandAnimator = ValueAnimator.ofInt(lp.width, target);
        mTrailerExpandAnimator.setDuration(TRAILER_EXPAND_MS);
        mTrailerExpandAnimator.setInterpolator(new DecelerateInterpolator());
        mTrailerExpandAnimator.addUpdateListener(animation -> {
            lp.width = (int) animation.getAnimatedValue();
            binding.mainImage.setLayoutParams(lp);
        });
        mTrailerExpandAnimator.start();
    }

    private void cancelTrailerExpansion() {
        if (mTrailerExpandAnimator != null) {
            mTrailerExpandAnimator.cancel();
            mTrailerExpandAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Rows recycle cards while scrolling, so a focused card can be torn down mid-animation.
        stopFocusFrameAnimation();
        stopDepthSettle();
        stopTrailerPreview();
        super.onDetachedFromWindow();
    }

    public void setBanner(int bannerResource) {
        if (mBanner == null) {
            mBanner = new ImageView(getContext());
            mBanner.setLayoutParams(new ViewGroup.LayoutParams(BANNER_SIZE, BANNER_SIZE));

            ((ViewGroup) getRootView()).addView(mBanner);
        }

        mBanner.setImageResource(bannerResource);
        mBanner.setVisibility(VISIBLE);
    }

    public final AsyncImageView getMainImageView() {
        return binding.mainImage;
    }

    public void setPlayingIndicator(boolean playing) {
        if (playing) {
            // TODO use decent animation for equalizer icon
            binding.extraBadge.setBackgroundResource(R.drawable.ic_play);
            binding.extraBadge.setVisibility(VISIBLE);
        } else {
            binding.extraBadge.setBackgroundResource(R.drawable.blank10x10);
        }
    }

    public void setMainImageDimensions(int width, int height) {
        setMainImageDimensions(width, height, ImageView.ScaleType.CENTER_CROP);
    }

    public void setMainImageDimensions(int width, int height, ImageView.ScaleType scaleType) {
        // Cards are recycled, so a card being rebound while expanded must forget the old artwork's
        // width or it would later shrink back to a size that belongs to a different item.
        cancelTrailerExpansion();
        mCollapsedImageWidth = 0;

        ViewGroup.LayoutParams lp = binding.mainImage.getLayoutParams();
        lp.width = Math.round(width * getResources().getDisplayMetrics().density);
        lp.height = Math.round(height * getResources().getDisplayMetrics().density);
        binding.mainImage.setLayoutParams(lp);
        binding.mainImage.setScaleType(scaleType);
        if (mBanner != null) mBanner.setX(lp.width - BANNER_SIZE);
        ViewGroup.LayoutParams lp2 = binding.resumeProgress.getLayoutParams();
        lp2.width = lp.width;
        binding.resumeProgress.setLayoutParams(lp2);
    }

    public void setTitleText(CharSequence text) {
        if (binding.title == null) {
            return;
        }

        if (binding.infoField != null) binding.infoField.setVisibility(VISIBLE);
        binding.title.setVisibility(VISIBLE);
        binding.title.setText(text);
        setTextMaxLines();
    }

    /**
     * Drops the name printed under the artwork.
     *
     * Posters already carry their own title, so the label repeats it in a less legible form and
     * only adds noise to a wall of cards. Cards are recycled, so this pairs with setTitleText
     * putting the label back rather than being a one-way switch.
     */
    public void hideTitleText() {
        if (binding.title != null) binding.title.setVisibility(GONE);
    }

    public void setOverlayText(String text) {
        if (getCardType() == BaseCardView.CARD_TYPE_MAIN_ONLY) {
            // Store for focus-based display
            mOverlayName = text;
            mShowOverlayOnFocus = true;
            binding.nameOverlay.setVisibility(GONE);
        } else {
            binding.nameOverlay.setVisibility(GONE);
        }
    }

    public void setOverlayInfo(BaseRowItem item) {
        // Kept regardless of card type so the trailer preview knows what it is showing
        mPreviewItem = item instanceof BaseItemDtoBaseRowItem ? item.getBaseItem() : null;

        if (binding.overlayText == null) return;

        if (getCardType() == BaseCardView.CARD_TYPE_MAIN_ONLY && item.getShowCardInfoOverlay()) {
            // Store name for focus-based display (Netflix style)
            mOverlayName = item.getFullName(getContext());
            mShowOverlayOnFocus = true;

            // Hide icons for movies/series - just show name on focus
            binding.icon.setVisibility(GONE);
            binding.overlayCount.setText(null);
            binding.nameOverlay.setVisibility(GONE);
        }
    }

    public void insertCardData (@Nullable String fullName, @NonNull int icon, @NonNull boolean iconVisible) {
        binding.overlayText.setText(fullName);
        if (iconVisible) {
            binding.iconImage.setImageResource(icon);
            binding.icon.setVisibility(VISIBLE);
        }
    }

    public CharSequence getTitle() {
        if (binding.title == null) {
            return null;
        }

        return binding.title.getText();
    }

    public void setContentText(CharSequence text) {
        if (binding.contentText == null) {
            return;
        }

        binding.contentText.setVisibility(VISIBLE);
        binding.contentText.setText(text);
        setTextMaxLines();
    }

    /** @see #hideTitleText() */
    public void hideContentText() {
        if (binding.contentText != null) binding.contentText.setVisibility(GONE);
    }

    /**
     * Hides the whole strip under the artwork.
     *
     * Hiding the two labels alone leaves the strip reserving its full height, so rows stayed as
     * far apart as when they carried text and the gap read as wasted space. Collapsing it lets the
     * rows close up.
     */
    public void hideInfoArea() {
        hideTitleText();
        hideContentText();

        if (binding.infoField != null) binding.infoField.setVisibility(GONE);
    }

    public CharSequence getContentText() {
        if (binding.contentText == null) {
            return null;
        }

        return binding.contentText.getText();
    }

    public void setRating(String rating) {
        if (rating != null) {
            binding.badgeText.setText(rating);
            binding.badgeText.setVisibility(VISIBLE);
        } else {
            binding.badgeText.setText("");
            binding.badgeText.setVisibility(GONE);
        }
    }

    public void setBadgeImage(Drawable drawable) {
        if (binding.extraBadge == null) {
            return;
        }

        if (drawable != null) {
            binding.extraBadge.setImageDrawable(drawable);
            binding.extraBadge.setVisibility(View.VISIBLE);
        } else {
            binding.extraBadge.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void setTextMaxLines() {
        if (TextUtils.isEmpty(getTitle())) {
            binding.contentText.setMaxLines(2);
        } else {
            binding.contentText.setMaxLines(1);
        }

        if (TextUtils.isEmpty(getContentText())) {
            binding.title.setMaxLines(2);
        } else {
            binding.title.setMaxLines(1);
        }
    }

    public void clearBanner() {
        if (mBanner != null) {
            mBanner.setVisibility(GONE);
        }
    }

    public void setUnwatchedCount(int count) {
        if (count > 0) {
            binding.unwatchedCount.setText(count > 99 ? getContext().getString(R.string.watch_count_overflow) : nf.format(count));
            binding.unwatchedCount.setVisibility(VISIBLE);
            binding.checkMark.setVisibility(INVISIBLE);
            binding.watchedIndicator.setVisibility(VISIBLE);
        } else if (count == 0) {
            binding.checkMark.setVisibility(VISIBLE);
            binding.unwatchedCount.setVisibility(INVISIBLE);
            binding.watchedIndicator.setVisibility(VISIBLE);
        } else {
            binding.watchedIndicator.setVisibility(GONE);
        }
    }

    public void setProgress(int pct) {
        if (pct > 0) {
            binding.resumeProgress.setProgress(pct);
            binding.resumeProgress.setVisibility(VISIBLE);
        } else {
            binding.resumeProgress.setVisibility(GONE);
        }
    }

    public void showFavIcon(boolean show) {
        binding.favIcon.setVisibility(show ? VISIBLE : GONE);
    }

    /**
     * Set the rating badge overlay (visible on Netflix-style cards).
     * @param rating The rating text to display (e.g., "8.5"), or null to hide.
     */
    public void setRatingBadge(String rating) {
        if (binding.ratingBadgeOverlay == null) {
            return;
        }

        if (rating != null && !rating.isEmpty()) {
            binding.ratingBadgeText.setText(rating);
            binding.ratingBadgeOverlay.setVisibility(VISIBLE);
        } else {
            binding.ratingBadgeOverlay.setVisibility(GONE);
        }
    }

    /**
     * Clear the rating badge overlay.
     */
    public void clearRatingBadge() {
        if (binding.ratingBadgeOverlay != null) {
            binding.ratingBadgeOverlay.setVisibility(GONE);
        }
    }
}
