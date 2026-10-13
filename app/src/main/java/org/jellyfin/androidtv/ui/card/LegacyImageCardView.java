package org.jellyfin.androidtv.ui.card;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.BlurMaskFilter;
import android.os.Build;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;

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
import org.jellyfin.androidtv.util.ElegantStyle;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.constant.FocusFrameStyle;
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
    private GradientDrawable mFocusFrame = null;
    private ValueAnimator mFocusFrameAnimator = null;
    /**
     * ElegantFin's card treatment: a fixed white frame on focus and a thin dark border at rest, in
     * place of the colour-drifting frame. Resolved once from the theme, which cannot change
     * without the activity - and every card in it - being recreated.
     */
    private final boolean mElegant = ElegantStyle.isElegantStyle(getContext());
    private GradientDrawable mRestingFrame = null;
    private Drawable mElegantFocusFrame = null;

    /**
     * The focus outline and glow chosen in settings. Read once per card: cards are rebuilt whenever
     * a screen is, so a changed setting shows on the next screen opened.
     */
    private final FocusFrameStyle mFrameStyle = KoinJavaComponent.<UserPreferences>get(UserPreferences.class)
            .get(UserPreferences.Companion.getFocusFrameStyle());
    private final boolean mFocusGlow = KoinJavaComponent.<UserPreferences>get(UserPreferences.class)
            .get(UserPreferences.Companion.getFocusGlowEnabled());
    /** Whether the frame or glow drawn around the outside of the card is currently showing. */
    private boolean mOuterFocusShown = false;
    private final Paint mOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mArtBounds = new RectF();
    private BlurMaskFilter mGlowFilter = null;
    private BaseItemDto mPreviewItem = null;
    private TrailerPreviewController mTrailerController = null;
    private ValueAnimator mTrailerExpandAnimator = null;
    /** The artwork's own width, remembered so the card can close back to it after a preview. */
    private int mCollapsedImageWidth = 0;
    /** Fire TV's preview surface, created on first use; see {@link #previewSurface()}. */
    private SurfaceView mTrailerSurface = null;

    /** Trailers are widescreen, so the card takes that shape while one plays. */
    private static final float TRAILER_ASPECT = 16f / 9f;
    private static final long TRAILER_EXPAND_MS = 300L;

    private ValueAnimator mDepthAnimator = null;
    private Runnable mTrailerProgressTick = null;
    /** Which badges a running preview is currently covering up, so only those are put back. */
    private boolean mWatchedIndicatorHidden = false;
    private boolean mRatingBadgeHidden = false;

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
    private static final float TRAILER_TITLE_ALPHA = 0.32f;

    private static final long BADGE_FADE_MS = 300L;

    /** Long enough to read the name, then a slow fade rather than a cut. */
    private static final long TRAILER_TITLE_HOLD_MS = 3000L;
    private static final long TRAILER_TITLE_FADE_OUT_MS = 1400L;

    /** One full trip around the colour wheel, slow enough to read as a drift rather than a flash. */
    private static final long FOCUS_FRAME_CYCLE_MS = 12000L;
    /** Held back from fully saturated so the frame stays easy on the eye against artwork. */
    private static final float FOCUS_FRAME_SATURATION = 0.7f;

    /** The focus glow: how far it spreads and how bright it is at the card's edge. */
    private static final float GLOW_RADIUS_DP = 16f;
    private static final int GLOW_ALPHA = 200;

    public LegacyImageCardView(Context context, boolean showInfo) {
        super(context, null, androidx.leanback.R.attr.imageCardViewStyle);

        if (!showInfo) {
            setCardType(CARD_TYPE_MAIN_ONLY);
        }

        binding.mainImage.setClipToOutline(true);

        // The video draws on its own surface, which ignores the artwork's rounded background, so
        // a playing trailer showed square corners poking out past the rounded focus frame
        clipToCardCorners(binding.trailerPreview);

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

        if (mElegant && getRestingFrame() != null) {
            binding.focusFrame.setBackground(getRestingFrame());
            binding.focusFrame.setAlpha(1f);
        }

        setOnFocusChangeListener((v, hasFocus) -> {
            // The glow and any outline from settings are drawn around the card in dispatchDraw
            mOuterFocusShown = hasFocus;
            if (usesOuterFocus()) invalidate();

            if (mFrameStyle != FocusFrameStyle.THEME_DEFAULT) {
                // An outline from settings replaces the theme's frame on the artwork, which then
                // only keeps ElegantFin's resting border
                binding.focusFrame.animate().cancel();
                Drawable resting = mElegant && !hasFocus ? getRestingFrame() : null;
                binding.focusFrame.setBackground(resting);
                binding.focusFrame.setAlpha(resting != null ? 1f : 0f);

                if (hasFocus) {
                    startDepthSettle();
                    startTrailerDwell();
                } else {
                    stopDepthSettle();
                    stopTrailerPreview();
                }
                return;
            }

            // Frame the artwork so the focused card is obvious while moving along a row. This is
            // driven from here rather than a state selector because focus lands on the card view
            // while the frame belongs on the image inside it.
            //
            // Faded rather than switched: appearing and vanishing outright makes moving along a
            // row feel like a series of jumps, even though the card itself is animating.
            binding.focusFrame.animate().cancel();

            if (mElegant) {
                // Always visible, so it is swapped rather than faded: a fade would blink the
                // resting border out on the way to the focus frame.
                Drawable frame = hasFocus ? getElegantFocusFrame() : getRestingFrame();
                binding.focusFrame.setBackground(frame);
                binding.focusFrame.setAlpha(frame != null ? 1f : 0f);
            } else if (hasFocus) {
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

        });
    }

    /** A frame style or glow from settings replaces the theme's own focus frame. */
    private boolean usesOuterFocus() {
        return mFrameStyle != FocusFrameStyle.THEME_DEFAULT || mFocusGlow;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        // The outline and glow sit outside the card, in the gap between it and its neighbours.
        // The row would otherwise clip them away at the card's own edge.
        if (usesOuterFocus()) {
            // Leanback's rows also clip each card to a rounded outline of its own bounds, which
            // cuts away anything drawn outside it. The artwork keeps its own clip regardless.
            setClipToOutline(false);

            // Rows wrap each card in a container that clips too, and that container sits in the
            // row's grid; both have to let the drawing through
            ViewParent parent = getParent();
            for (int level = 0; level < 2 && parent instanceof ViewGroup; level++) {
                ViewGroup group = (ViewGroup) parent;
                group.setClipChildren(false);
                group.setClipToPadding(false);
                group.setClipToOutline(false);
                parent = group.getParent();
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean outer = mOuterFocusShown && usesOuterFocus() && artBounds(mArtBounds);
        float density = getResources().getDisplayMetrics().density;

        // Glow first, so the artwork covers it and only the halo around the card shows
        if (outer && mFocusGlow) drawGlow(canvas, density);

        super.dispatchDraw(canvas);

        if (outer && (mFrameStyle == FocusFrameStyle.GOOGLE_TV_RING || mFrameStyle == FocusFrameStyle.HUGGING_RING)) {
            // Google TV leaves a strip of page background between the artwork and the ring, so the
            // ring never has to stand out against the poster; the hugging ring sits right on it
            float stroke = 3f * density;
            float gap = mFrameStyle == FocusFrameStyle.GOOGLE_TV_RING ? 4f * density : 0f;
            float offset = gap + stroke / 2f;
            mOuterPaint.setStyle(Paint.Style.STROKE);
            mOuterPaint.setStrokeWidth(stroke);
            mOuterPaint.setColor(Color.WHITE);
            canvas.drawRect(mArtBounds.left - offset, mArtBounds.top - offset,
                    mArtBounds.right + offset, mArtBounds.bottom + offset, mOuterPaint);
        }
    }

    /**
     * A soft white halo around the artwork. A real blur from Android 9, where hardware drawing
     * supports it; before that, widening translucent outlines that approximate one.
     */
    private void drawGlow(Canvas canvas, float density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (mGlowFilter == null) mGlowFilter = new BlurMaskFilter(GLOW_RADIUS_DP * density, BlurMaskFilter.Blur.OUTER);
            mOuterPaint.setMaskFilter(mGlowFilter);
            mOuterPaint.setStyle(Paint.Style.FILL);
            mOuterPaint.setColor(Color.WHITE);
            mOuterPaint.setAlpha(GLOW_ALPHA);
            canvas.drawRect(mArtBounds, mOuterPaint);
            mOuterPaint.setMaskFilter(null);
            return;
        }

        mOuterPaint.setStyle(Paint.Style.STROKE);
        mOuterPaint.setColor(Color.WHITE);
        float step = 1.5f * density;
        mOuterPaint.setStrokeWidth(step);
        int layers = (int) (GLOW_RADIUS_DP / 1.5f);
        for (int i = 1; i <= layers; i++) {
            float spread = i * step - step / 2f;
            mOuterPaint.setAlpha((int) (GLOW_ALPHA * 0.6f * (1f - (float) (i - 1) / layers)));
            canvas.drawRect(mArtBounds.left - spread, mArtBounds.top - spread,
                    mArtBounds.right + spread, mArtBounds.bottom + spread, mOuterPaint);
        }
    }

    /** The artwork's rectangle in this view's coordinates; false while it has no size yet. */
    private boolean artBounds(RectF out) {
        View image = binding.mainImage;
        if (image.getWidth() == 0 || image.getHeight() == 0) return false;

        float left = image.getLeft();
        float top = image.getTop();
        for (View parent = (View) image.getParent(); parent != null && parent != this; parent = (View) parent.getParent()) {
            left += parent.getLeft();
            top += parent.getTop();
        }

        out.set(left, top, left + image.getWidth(), top + image.getHeight());
        return true;
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

    /**
     * ElegantFin's focus frame: white on the card's edge with a thin black line just inside it.
     * Plain white vanished against white posters; the pair stays visible on any artwork - white
     * shows on dark images, black on light ones. Square and flush with the edge, like the card.
     */
    private Drawable getElegantFocusFrame() {
        if (mElegantFocusFrame == null) {
            int white = getResources().getDimensionPixelSize(R.dimen.card_focus_frame_width);
            int dark = Utils.convertDpToPixel(getContext(), 2);

            // Drawn first and wider, so only its inner edge shows past the white line on top
            GradientDrawable inner = new GradientDrawable();
            inner.setShape(GradientDrawable.RECTANGLE);
            inner.setStroke(white + dark, Color.BLACK);

            GradientDrawable outer = new GradientDrawable();
            outer.setShape(GradientDrawable.RECTANGLE);
            outer.setStroke(white, Color.WHITE);

            mElegantFocusFrame = new android.graphics.drawable.LayerDrawable(new Drawable[]{inner, outer});
        }

        return mElegantFocusFrame;
    }

    /** The thin border every card wears at rest in ElegantFin, or null when the theme has none. */
    private GradientDrawable getRestingFrame() {
        int color = ElegantStyle.cardRestingBorderColor(getContext());
        if (color == 0) return null;

        if (mRestingFrame == null) {
            int strokeWidth = Utils.convertDpToPixel(getContext(), 2);
            mRestingFrame = new GradientDrawable();
            mRestingFrame.setShape(GradientDrawable.RECTANGLE);
            mRestingFrame.setStroke(strokeWidth, color);
            mRestingFrame.setCornerRadius(Math.max(0f, resolveCardRounding() - strokeWidth / 2f));
        }

        return mRestingFrame;
    }

    /** Clips [view] to the same rounded rectangle as the artwork. */
    private void clipToCardCorners(View view) {
        float radius = resolveCardRounding();
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
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

        // ElegantFin's focus is a plain white outline. Nothing to animate, and no reason to
        // analyse the artwork for a hue that would not be used.
        if (mElegant) {
            frame.setStroke(strokeWidth, Color.WHITE);
            return;
        }

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
            View surface = previewSurface();
            surface.setVisibility(VISIBLE);
            surface.setAlpha(0f);

            TrailerPreviewPlayer.INSTANCE.play(getContext(), this, surface, url, () -> {
                // Only reveal once frames are actually arriving, so the card never flashes black
                // or opens out for a trailer that turns out not to play
                surface.animate().alpha(1f).setDuration(400).start();
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
        View surface = mTrailerSurface != null ? mTrailerSurface : binding.trailerPreview;
        surface.animate().cancel();
        surface.setVisibility(GONE);
        surface.setAlpha(0f);
        hideTrailerOverlays();
        collapseAfterTrailer();
    }

    /**
     * The view the trailer renders into.
     *
     * Everywhere but Fire TV this is the layout's TextureView. A TextureView draws video through
     * the app's own GPU pass, so every video frame redraws the whole screen - trivial on the
     * Xiaomi, but more than a Fire TV Stick's GPU can manage, and the preview dropped to around
     * ten frames a second there. A SurfaceView hands frames straight to the display compositor
     * instead, so on Fire TV one is created in the TextureView's place the first time it is
     * needed. The overlays and focus frame are later siblings, so they still draw over it.
     */
    private View previewSurface() {
        if (!isFireTv()) return binding.trailerPreview;

        if (mTrailerSurface == null) {
            View texture = binding.trailerPreview;
            ViewGroup parent = (ViewGroup) texture.getParent();

            mTrailerSurface = new SurfaceView(getContext());
            mTrailerSurface.setVisibility(GONE);
            // Honoured from Android 12; older Fire OS draws the surface square regardless
            clipToCardCorners(mTrailerSurface);
            // A copy, so it pins to the artwork exactly as the TextureView does
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams) texture.getLayoutParams());
            parent.addView(mTrailerSurface, parent.indexOfChild(texture) + 1, lp);
        }

        return mTrailerSurface;
    }

    private boolean isFireTv() {
        return getContext().getPackageManager().hasSystemFeature("amazon.hardware.fire_tv");
    }

    /**
     * Brings up the furniture that turns a video playing in a card into something that looks
     * intentional: edges pulled down, the name of what is playing, and a hint of how far in it is.
     */
    private void showTrailerOverlays() {
        binding.trailerVignette.setVisibility(VISIBLE);
        binding.trailerVignette.setAlpha(0f);
        binding.trailerVignette.animate()
                .alpha(1f)
                .setStartDelay(0)
                .setDuration(400)
                .withEndAction(() -> fadeOutTrailerFurniture(binding.trailerVignette, 1f))
                .start();

        String name = mPreviewItem != null ? mPreviewItem.getName() : null;
        if (name != null) {
            Integer year = mPreviewItem.getProductionYear();
            binding.trailerTitle.setText(year != null ? name + "  ·  " + year : name);
            binding.trailerTitle.setVisibility(VISIBLE);
            binding.trailerTitle.setAlpha(0f);
            // Held below full white: the label is there to say what is playing, not to compete
            // with the picture it sits on. Trails the video slightly so it arrives onto something,
            // then dims away entirely - it has done its job by then, and the trailer is the point.
            binding.trailerTitle.animate()
                    .alpha(TRAILER_TITLE_ALPHA)
                    .setStartDelay(250)
                    .setDuration(400)
                    .withEndAction(() -> fadeOutTrailerFurniture(binding.trailerTitle, TRAILER_TITLE_ALPHA))
                    .start();
        }

        binding.trailerProgress.setProgress(0);
        binding.trailerProgress.setVisibility(VISIBLE);
        binding.trailerProgress.animate().cancel();
        fadeOutTrailerFurniture(binding.trailerProgress, 1f);
        startTrailerProgressTicker();

        // Both badges describe the artwork, not the trailer now playing over it, and they sit in
        // the corners where the picture is usually busiest. Remembered individually so each comes
        // back only if it was there to begin with.
        mWatchedIndicatorHidden = hideBadge(binding.watchedIndicator);
        mRatingBadgeHidden = hideBadge(binding.ratingBadgeOverlay);
    }

    /** Fades [badge] out if it is showing. Returns whether it had anything to hide. */
    private boolean hideBadge(View badge) {
        if (badge.getVisibility() != VISIBLE) return false;

        badge.animate()
                .alpha(0f)
                .setDuration(BADGE_FADE_MS)
                .withEndAction(() -> badge.setVisibility(GONE))
                .start();

        return true;
    }

    private void hideTrailerOverlays() {
        stopTrailerProgressTicker();
        restoreArtworkBadges();

        binding.trailerVignette.animate().cancel();
        binding.trailerVignette.setVisibility(GONE);
        binding.trailerTitle.animate().cancel();
        binding.trailerTitle.setVisibility(GONE);
        binding.trailerProgress.animate().cancel();
        binding.trailerProgress.setVisibility(GONE);
    }

    /**
     * Sends the furniture away once the trailer has settled in, leaving nothing but the picture.
     *
     * The vignette goes with it rather than staying: it exists to frame a video that has just
     * appeared inside a poster, and once the eye has accepted that, it is only darkening the
     * corners of something the user is trying to watch.
     */
    private void fadeOutTrailerFurniture(View view, float from) {
        view.setAlpha(from);
        view.animate()
                .alpha(0f)
                .setStartDelay(TRAILER_TITLE_HOLD_MS)
                .setDuration(TRAILER_TITLE_FADE_OUT_MS)
                .start();
    }

    /**
     * Puts the badges back after a preview. Also called when the card is rebound, since a card
     * recycled mid-preview would otherwise carry a transparent badge onto its next item.
     */
    private void restoreArtworkBadges() {
        if (mWatchedIndicatorHidden) {
            mWatchedIndicatorHidden = false;
            restoreBadge(binding.watchedIndicator);
        }

        if (mRatingBadgeHidden) {
            mRatingBadgeHidden = false;
            restoreBadge(binding.ratingBadgeOverlay);
        }
    }

    private void restoreBadge(View badge) {
        badge.animate().cancel();
        badge.setAlpha(1f);
        badge.setVisibility(VISIBLE);
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
        restoreArtworkBadges();
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

    /**
     * The name overlay is no longer shown on focus. Cards are artwork alone now, and a title
     * printed over a poster that already carries one was only ever noise - the trailer preview
     * puts the name up while it plays, which is the one moment the artwork is not visible.
     */
    public void setOverlayText(String text) {
        binding.nameOverlay.setVisibility(GONE);
    }

    /**
     * Marks artwork the server only recently learned about.
     *
     * Recency is judged on when the item was added to the library rather than when it was
     * released, since that is what makes it new to the person looking at the shelf.
     */
    public void setNewBadgeVisible(boolean visible) {
        binding.newBadge.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setOverlayInfo(BaseRowItem item) {
        // Kept regardless of card type so the trailer preview knows what it is showing
        mPreviewItem = item instanceof BaseItemDtoBaseRowItem ? item.getBaseItem() : null;

        if (binding.overlayText == null) return;

        if (getCardType() == BaseCardView.CARD_TYPE_MAIN_ONLY && item.getShowCardInfoOverlay()) {
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
