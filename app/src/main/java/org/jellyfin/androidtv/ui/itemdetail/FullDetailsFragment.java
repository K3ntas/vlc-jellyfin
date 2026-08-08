package org.jellyfin.androidtv.ui.itemdetail;

import static org.koin.java.KoinJavaComponent.inject;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewKt;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.RowsSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.lifecycle.Lifecycle;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.auth.repository.UserRepository;
import org.jellyfin.androidtv.constant.CustomMessage;
import org.jellyfin.androidtv.constant.QueryType;
import org.jellyfin.androidtv.data.model.ChapterItemInfo;
import org.jellyfin.androidtv.data.model.DataRefreshService;
import org.jellyfin.androidtv.data.model.InfoItem;
import org.jellyfin.androidtv.data.querying.GetAdditionalPartsRequest;
import org.jellyfin.androidtv.data.querying.GetSpecialsRequest;
import org.jellyfin.androidtv.data.querying.GetTrailersRequest;
import org.jellyfin.androidtv.data.repository.CustomMessageRepository;
import org.jellyfin.androidtv.data.service.BackgroundService;
import org.jellyfin.androidtv.databinding.FragmentFullDetailsBinding;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.constant.ClockBehavior;
import org.jellyfin.androidtv.data.ratings.RatingStats;
import org.jellyfin.androidtv.ui.ratings.AllRatingsActivity;
import org.jellyfin.androidtv.ui.presentation.ReviewCardPresenter;
import org.jellyfin.androidtv.data.ratings.UserRatingDetail;
import org.jellyfin.androidtv.auth.repository.SessionRepository;
import org.jellyfin.androidtv.ui.InteractionTrackerViewModel;
import org.jellyfin.androidtv.ui.RatingPopup;
import org.jellyfin.androidtv.ui.RecordPopup;
import org.jellyfin.androidtv.ui.RecordingIndicatorView;
import org.jellyfin.androidtv.ui.TextUnderButton;
import org.jellyfin.androidtv.ui.browsing.BrowsingUtils;
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem;
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher;
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter;
import org.jellyfin.androidtv.ui.livetv.TvManager;
import org.jellyfin.androidtv.ui.navigation.Destinations;
import org.jellyfin.androidtv.ui.navigation.NavigationRepository;
import org.jellyfin.androidtv.ui.playback.MediaManager;
import org.jellyfin.androidtv.ui.playback.PlaybackLauncher;
import org.jellyfin.androidtv.ui.presentation.CardPresenter;
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter;
import org.jellyfin.androidtv.ui.presentation.InfoCardPresenter;
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter;
import org.jellyfin.androidtv.ui.presentation.MyDetailsOverviewRowPresenter;
import org.jellyfin.androidtv.util.CoroutineUtils;
import org.jellyfin.androidtv.util.DateTimeExtensionsKt;
import org.jellyfin.androidtv.util.ImageHelper;
import org.jellyfin.androidtv.util.KeyProcessor;
import org.jellyfin.androidtv.util.MarkdownRenderer;
import org.jellyfin.androidtv.util.PlaybackHelper;
import org.jellyfin.androidtv.util.TimeUtils;
import org.jellyfin.androidtv.util.Utils;
import org.jellyfin.androidtv.util.apiclient.BaseItemUtils;
import org.jellyfin.androidtv.util.apiclient.Response;
import org.jellyfin.androidtv.util.sdk.BaseItemExtensionsKt;
import org.jellyfin.androidtv.util.sdk.TrailerUtils;
import org.jellyfin.androidtv.util.sdk.compat.JavaCompat;
import org.jellyfin.sdk.model.api.BaseItemDto;
import org.jellyfin.sdk.model.api.BaseItemKind;
import org.jellyfin.sdk.model.api.BaseItemPerson;
import org.jellyfin.sdk.model.api.MediaSourceInfo;
import org.jellyfin.sdk.model.api.MediaStream;
import org.jellyfin.sdk.model.api.MediaType;
import org.jellyfin.sdk.model.api.PersonKind;
import org.jellyfin.sdk.model.api.SeriesTimerInfoDto;
import org.jellyfin.sdk.model.api.UserDto;
import org.jellyfin.sdk.model.serializer.UUIDSerializerKt;
import org.koin.java.KoinJavaComponent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import kotlin.Lazy;
import kotlinx.serialization.json.Json;
import timber.log.Timber;

public class FullDetailsFragment extends Fragment implements RecordingIndicatorView, View.OnKeyListener {

    private int BUTTON_SIZE;

    TextUnderButton mResumeButton;
    private TextUnderButton mVersionsButton;
    TextUnderButton mPrevButton;
    private TextUnderButton mRecordButton;
    private TextUnderButton mRecSeriesButton;
    private TextUnderButton mSeriesSettingsButton;
    TextUnderButton mWatchedToggleButton;

    private DisplayMetrics mMetrics;

    protected BaseItemDto mProgramInfo;
    protected SeriesTimerInfoDto mSeriesTimerInfo;
    protected UUID mItemId;
    protected UUID mChannelId;
    protected BaseRowItem mCurrentItem;
    private Instant mLastUpdated;
    public UUID mPrevItemId;

    private RowsSupportFragment mRowsFragment;
    private MutableObjectAdapter<Row> mRowsAdapter;

    private MyDetailsOverviewRowPresenter mDorPresenter;
    private MyDetailsOverviewRow mDetailsOverviewRow;
    private CustomListRowPresenter mListRowPresenter;

    private Handler mLoopHandler = new Handler();
    private Runnable mClockLoop;

    BaseItemDto mBaseItem;

    private ArrayList<MediaSourceInfo> versions;
    private final Lazy<org.jellyfin.sdk.api.client.ApiClient> api = inject(org.jellyfin.sdk.api.client.ApiClient.class);
    private final Lazy<UserPreferences> userPreferences = inject(UserPreferences.class);
    private final Lazy<DataRefreshService> dataRefreshService = inject(DataRefreshService.class);
    private final Lazy<BackgroundService> backgroundService = inject(BackgroundService.class);
    final Lazy<MediaManager> mediaManager = inject(MediaManager.class);
    private final Lazy<MarkdownRenderer> markdownRenderer = inject(MarkdownRenderer.class);
    private final Lazy<CustomMessageRepository> customMessageRepository = inject(CustomMessageRepository.class);
    final Lazy<NavigationRepository> navigationRepository = inject(NavigationRepository.class);
    private final Lazy<ItemLauncher> itemLauncher = inject(ItemLauncher.class);
    private final Lazy<KeyProcessor> keyProcessor = inject(KeyProcessor.class);
    final Lazy<PlaybackHelper> playbackHelper = inject(PlaybackHelper.class);
    private final Lazy<ImageHelper> imageHelper = inject(ImageHelper.class);
    private final Lazy<InteractionTrackerViewModel> interactionTracker = inject(InteractionTrackerViewModel.class);
    private final Lazy<SessionRepository> sessionRepository = inject(SessionRepository.class);

    private RatingPopup mRatingPopup;
    private ListRow mUserReviewsRow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentFullDetailsBinding binding = FragmentFullDetailsBinding.inflate(getLayoutInflater(), container, false);

        BUTTON_SIZE = Utils.convertDpToPixel(requireContext(), 40);

        mMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        mRowsFragment = new RowsSupportFragment();
        getChildFragmentManager().beginTransaction().replace(R.id.rowsFragment, mRowsFragment).commit();

        mRowsFragment.setOnItemViewClickedListener(new ItemViewClickedListener());
        mRowsFragment.setOnItemViewSelectedListener(new ItemViewSelectedListener());

        mDorPresenter = new MyDetailsOverviewRowPresenter(markdownRenderer.getValue());

        mItemId = Utils.uuidOrNull(getArguments().getString("ItemId"));
        mChannelId = Utils.uuidOrNull(getArguments().getString("ChannelId"));
        String programJson = getArguments().getString("ProgramInfo");
        if (programJson != null) {
            mProgramInfo = Json.Default.decodeFromString(BaseItemDto.Companion.serializer(), programJson);
        }
        String timerJson = getArguments().getString("SeriesTimer");
        if (timerJson != null) {
            mSeriesTimerInfo = Json.Default.decodeFromString(SeriesTimerInfoDto.Companion.serializer(), timerJson);
        }

        CoroutineUtils.readCustomMessagesOnLifecycle(getLifecycle(), customMessageRepository.getValue(), message -> {
            if (message.equals(CustomMessage.ActionComplete.INSTANCE) && mSeriesTimerInfo != null) {
                //update info
                FullDetailsFragmentHelperKt.getLiveTvSeriesTimer(this, mSeriesTimerInfo.getId(), seriesTimerInfoDto -> {
                    mSeriesTimerInfo = seriesTimerInfoDto;
                    mBaseItem = JavaCompat.copyWithOverview(mBaseItem, BaseItemUtils.getSeriesOverview(mSeriesTimerInfo, requireContext()));
                    mDorPresenter.getViewHolder().setSummary(mBaseItem.getOverview());
                    return null;
                });

                mRowsAdapter.clear();
                mRowsAdapter.add(mDetailsOverviewRow);
                //re-retrieve the schedule after giving it a second to rebuild
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED))
                            return;

                        addAdditionalRows(mRowsAdapter);
                        // Delay reviews loading to ensure ViewHolder is ready
                        new Handler().postDelayed(() -> loadUserReviewsInline(), 200);
                    }
                }, 1500);
            }
            return null;
        });

        loadItem(mItemId);

        return binding.getRoot();
    }

    int getResumePreroll() {
        try {
            return Integer.parseInt(KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getResumeSubtractDuration())) * 1000;
        } catch (Exception e) {
            Timber.e(e, "Unable to parse resume preroll");
            return 0;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        ClockBehavior clockBehavior = userPreferences.getValue().get(UserPreferences.Companion.getClockBehavior());
        if (clockBehavior == ClockBehavior.ALWAYS || clockBehavior == ClockBehavior.IN_MENUS) {
            startClock();
        }

        //Update information that may have changed - delay slightly to allow changes to take on the server
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;

                Instant lastPlaybackTime = dataRefreshService.getValue().getLastPlayback();
                Timber.d("current time %s last playback event time %s last refresh time %s", Instant.now().toEpochMilli(), lastPlaybackTime, mLastUpdated.toEpochMilli());

                // if last playback event exists, and event time is greater than last sync or within 2 seconds of current time
                // the third condition accounts for a situation where a sync (dataRefresh) coincides with the end of playback
                if (lastPlaybackTime != null && (lastPlaybackTime.isAfter(mLastUpdated) || Instant.now().toEpochMilli() - lastPlaybackTime.toEpochMilli() < 2000) && mBaseItem.getType() != BaseItemKind.MUSIC_ARTIST) {
                    BaseItemDto lastPlayedItem = dataRefreshService.getValue().getLastPlayedItem();
                    if (mBaseItem.getType() == BaseItemKind.EPISODE && lastPlayedItem != null && !mBaseItem.getId().equals(lastPlayedItem.getId().toString()) && lastPlayedItem.getType() == BaseItemKind.EPISODE) {
                        Timber.i("Re-loading after new episode playback");
                        loadItem(lastPlayedItem.getId());
                        dataRefreshService.getValue().setLastPlayedItem(null); //blank this out so a detail screen we back up to doesn't also do this
                    } else {
                        Timber.i("Updating info after playback");
                        FullDetailsFragmentHelperKt.getItem(FullDetailsFragment.this, mBaseItem.getId(), item -> {
                            if (item == null) return null;

                            mBaseItem = item;
                            if (mResumeButton != null) {
                                boolean resumeVisible = (mBaseItem.getType() == BaseItemKind.SERIES && !mBaseItem.getUserData().getPlayed()) || JavaCompat.getCanResume(mBaseItem);
                                mResumeButton.setVisibility(resumeVisible ? View.VISIBLE : View.GONE);
                                if (JavaCompat.getCanResume(mBaseItem)) {
                                    mResumeButton.setLabel(getString(R.string.lbl_resume_from, TimeUtils.formatMillis((mBaseItem.getUserData().getPlaybackPositionTicks() / 10000) - getResumePreroll())));
                                }
                                if (resumeVisible) {
                                    mResumeButton.requestFocus();
                                } else if (playButton != null && ViewKt.isVisible(playButton)) {
                                    playButton.requestFocus();
                                }
                                showMoreButtonIfNeeded();
                            }
                            updateWatched();
                            mLastUpdated = Instant.now();
                            return null;
                        });
                    }
                }
            }
        }, 750);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopClock();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopClock();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;

        if (mCurrentItem != null) {
            return keyProcessor.getValue().handleKey(keyCode, mCurrentItem, requireActivity());
        } else if ((keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) && BaseItemExtensionsKt.canPlay(mBaseItem)) {
            //default play action
            Long pos = mBaseItem.getUserData().getPlaybackPositionTicks() / 10000;
            play(mBaseItem, pos.intValue(), false);
            return true;
        }

        return false;
    }

    private void startClock() {
        mClockLoop = new Runnable() {
            @Override
            public void run() {
                if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
                // View holder may be null when the base item is still loading - this is a rare case
                // which generally happens when the server is unresponsive
                MyDetailsOverviewRowPresenter.ViewHolder viewholder = mDorPresenter.getViewHolder();
                if (viewholder == null) return;

                if (mBaseItem != null && ((mBaseItem.getRunTimeTicks() != null && mBaseItem.getRunTimeTicks() > 0) || mBaseItem.getRunTimeTicks() != null)) {
                    viewholder.setInfoValue3(getEndTime());
                    mLoopHandler.postDelayed(this, 15000);
                }
            }
        };

        mLoopHandler.postDelayed(mClockLoop, 15000);
    }

    private void stopClock() {
        if (mLoopHandler != null && mClockLoop != null) {
            mLoopHandler.removeCallbacks(mClockLoop);
        }
    }

    private static BaseItemKind[] buttonTypes = new BaseItemKind[]{
            BaseItemKind.EPISODE,
            BaseItemKind.MOVIE,
            BaseItemKind.SERIES,
            BaseItemKind.SEASON,
            BaseItemKind.FOLDER,
            BaseItemKind.VIDEO,
            BaseItemKind.RECORDING,
            BaseItemKind.PROGRAM,
            BaseItemKind.TRAILER,
            BaseItemKind.MUSIC_ARTIST,
            BaseItemKind.PERSON,
            BaseItemKind.MUSIC_VIDEO
    };

    private static List<BaseItemKind> buttonTypeList = Arrays.asList(buttonTypes);

    private void updateWatched() {
        if (mWatchedToggleButton != null && mBaseItem != null && mBaseItem.getUserData() != null) {
            mWatchedToggleButton.setActivated(mBaseItem.getUserData().getPlayed());
        }
    }

    private void loadItem(UUID id) {
        if (mChannelId != null && mProgramInfo == null) {
            // if we are displaying a live tv channel - we want to get whatever is showing now on that channel
            FullDetailsFragmentHelperKt.getLiveTvChannel(this, mChannelId, channel -> {
                mProgramInfo = channel.getCurrentProgram();
                mItemId = mProgramInfo.getId();
                FullDetailsFragmentHelperKt.getItem(FullDetailsFragment.this, mItemId, item -> {
                    if (item != null) {
                        setBaseItem(item);
                    } else {
                        // Failed to load item
                        navigationRepository.getValue().goBack();
                    }
                    return null;
                });
                return null;
            });
        } else if (mSeriesTimerInfo != null) {
            setBaseItem(FullDetailsFragmentHelperKt.createFakeSeriesTimerBaseItemDto(this, mSeriesTimerInfo));
        } else {
            FullDetailsFragmentHelperKt.getItem(FullDetailsFragment.this, id, item -> {
                if (item != null) {
                    setBaseItem(item);
                } else {
                    // Failed to load item
                    navigationRepository.getValue().goBack();
                }
                return null;
            });
        }

        mLastUpdated = Instant.now();
    }

    @Override
    public void setRecTimer(String id) {
        mProgramInfo = JavaCompat.copyWithTimerId(mProgramInfo, id);
        if (mRecordButton != null) mRecordButton.setActivated(id != null);
    }

    private int posterHeight;

    @Override
    public void setRecSeriesTimer(String id) {
        if (mProgramInfo != null) mProgramInfo = JavaCompat.copyWithTimerId(mProgramInfo, id);
        if (mRecSeriesButton != null) mRecSeriesButton.setActivated(id != null);
        if (mSeriesSettingsButton != null)
            mSeriesSettingsButton.setVisibility(id == null ? View.GONE : View.VISIBLE);

    }

    private class BuildDorTask extends AsyncTask<BaseItemDto, Integer, MyDetailsOverviewRow> {

        @Override
        protected MyDetailsOverviewRow doInBackground(BaseItemDto... params) {
            BaseItemDto item = params[0];

            // Figure image size
            Double aspect = imageHelper.getValue().getImageAspectRatio(item, false);
            posterHeight = aspect > 1 ? Utils.convertDpToPixel(requireContext(), 160) : Utils.convertDpToPixel(requireContext(), item.getType() == BaseItemKind.PERSON || item.getType() == BaseItemKind.MUSIC_ARTIST ? 300 : 200);

            mDetailsOverviewRow = new MyDetailsOverviewRow(item);

            String primaryImageUrl = imageHelper.getValue().getLogoImageUrl(mBaseItem, 600);
            if (primaryImageUrl == null) {
                primaryImageUrl = imageHelper.getValue().getPrimaryImageUrl(mBaseItem, false, null, posterHeight);
            }

            mDetailsOverviewRow.setSummary(item.getOverview());
            switch (item.getType()) {
                case PERSON:
                case MUSIC_ARTIST:
                    break;
                default:

                    BaseItemPerson director = BaseItemExtensionsKt.getFirstPerson(item, PersonKind.DIRECTOR);

                    InfoItem firstRow;
                    if (item.getType() == BaseItemKind.SERIES) {
                        firstRow = new InfoItem(
                                getString(R.string.lbl_seasons),
                                String.format("%d", Utils.getSafeValue(item.getChildCount(), 0)));
                    } else {
                        firstRow = new InfoItem(
                                getString(R.string.lbl_directed_by),
                                director != null ? director.getName() : getString(R.string.lbl_bracket_unknown));
                    }
                    mDetailsOverviewRow.setInfoItem1(firstRow);

                    if ((item.getRunTimeTicks() != null && item.getRunTimeTicks() > 0) || item.getRunTimeTicks() != null) {
                        mDetailsOverviewRow.setInfoItem2(new InfoItem(getString(R.string.lbl_runs), getRunTime()));
                        ClockBehavior clockBehavior = userPreferences.getValue().get(UserPreferences.Companion.getClockBehavior());
                        if (clockBehavior == ClockBehavior.ALWAYS || clockBehavior == ClockBehavior.IN_MENUS) {
                            mDetailsOverviewRow.setInfoItem3(new InfoItem(getString(R.string.lbl_ends), getEndTime()));
                        } else {
                            mDetailsOverviewRow.setInfoItem3(new InfoItem());
                        }
                    } else {
                        mDetailsOverviewRow.setInfoItem2(new InfoItem());
                        mDetailsOverviewRow.setInfoItem3(new InfoItem());
                    }

            }

            mDetailsOverviewRow.setImageDrawable(primaryImageUrl);

            return mDetailsOverviewRow;
        }

        @Override
        protected void onPostExecute(MyDetailsOverviewRow detailsOverviewRow) {
            super.onPostExecute(detailsOverviewRow);

            if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;

            ClassPresenterSelector ps = new ClassPresenterSelector();
            ps.addClassPresenter(MyDetailsOverviewRow.class, mDorPresenter);
            mListRowPresenter = new CustomListRowPresenter(Utils.convertDpToPixel(requireContext(), 10));
            ps.addClassPresenter(ListRow.class, mListRowPresenter);
            mRowsAdapter = new MutableObjectAdapter<Row>(ps);
            mRowsFragment.setAdapter(mRowsAdapter);
            mRowsAdapter.add(detailsOverviewRow);

            updateInfo(detailsOverviewRow.getItem());
            addAdditionalRows(mRowsAdapter);

            // Load community ratings and reviews (delay to ensure ViewHolder is created)
            new Handler().postDelayed(() -> {
                loadCommunityRatings();
                loadUserReviewsInline();
            }, 100);
        }
    }

    public void setBaseItem(BaseItemDto item) {
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;

        mBaseItem = item;
        backgroundService.getValue().setBackground(item);
        if (mBaseItem != null) {
            if (mChannelId != null) {
                mBaseItem = JavaCompat.copyWithParentId(mBaseItem, mChannelId);
                mBaseItem = JavaCompat.copyWithDates(
                        mBaseItem,
                        mProgramInfo.getStartDate(),
                        mProgramInfo.getEndDate(),
                        mBaseItem.getOfficialRating(),
                        mProgramInfo.getRunTimeTicks()
                );
            }
            new BuildDorTask().execute(item);
        }
    }

    protected void addItemRow(MutableObjectAdapter<Row> parent, ItemRowAdapter row, int index, String headerText) {
        HeaderItem header = new HeaderItem(index, headerText);
        ListRow listRow = new ListRow(header, row);
        parent.add(listRow);
        row.setRow(listRow);
        row.Retrieve();
    }

    protected void addAdditionalRows(MutableObjectAdapter<Row> adapter) {
        Timber.d("Item type: %s", mBaseItem.getType().toString());

        if (mSeriesTimerInfo != null) {
            TvManager.getScheduleRowsAsync(this, mSeriesTimerInfo.getId(), new CardPresenter(true), adapter);
            return;
        }

        switch (mBaseItem.getType()) {
            case MOVIE:

                //Additional Parts
                if (mBaseItem.getPartCount() != null && mBaseItem.getPartCount() > 0) {
                    ItemRowAdapter additionalPartsAdapter = new ItemRowAdapter(requireContext(), new GetAdditionalPartsRequest(mBaseItem.getId()), new CardPresenter(), adapter);
                    addItemRow(adapter, additionalPartsAdapter, 0, getString(R.string.lbl_additional_parts));
                }

                //Cast/Crew
                if (mBaseItem.getPeople() != null && !mBaseItem.getPeople().isEmpty()) {
                    ItemRowAdapter castAdapter = new ItemRowAdapter(mBaseItem.getPeople(), requireContext(), new CardPresenter(true, 130), adapter);
                    addItemRow(adapter, castAdapter, 1, getString(R.string.lbl_cast_crew));
                }

                //Specials
                if (mBaseItem.getSpecialFeatureCount() != null && mBaseItem.getSpecialFeatureCount() > 0) {
                    addItemRow(adapter, new ItemRowAdapter(requireContext(), new GetSpecialsRequest(mBaseItem.getId()), new CardPresenter(), adapter), 3, getString(R.string.lbl_specials));
                }

                //Trailers
                if (mBaseItem.getLocalTrailerCount() != null && mBaseItem.getLocalTrailerCount() > 1) {
                    addItemRow(adapter, new ItemRowAdapter(requireContext(), new GetTrailersRequest(mBaseItem.getId()), new CardPresenter(), adapter), 4, getString(R.string.lbl_trailers));
                }

                //Chapters
                if (mBaseItem.getChapters() != null && !mBaseItem.getChapters().isEmpty()) {
                    List<ChapterItemInfo> chapters = BaseItemExtensionsKt.buildChapterItems(mBaseItem, api.getValue());
                    ItemRowAdapter chapterAdapter = new ItemRowAdapter(requireContext(), chapters, new CardPresenter(true, 120), adapter);
                    addItemRow(adapter, chapterAdapter, 2, getString(R.string.lbl_chapters));
                }

                //Similar
                ItemRowAdapter similarMoviesAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createSimilarItemsRequest(mBaseItem.getId()), QueryType.SimilarMovies, new CardPresenter(), adapter);
                addItemRow(adapter, similarMoviesAdapter, 5, getString(R.string.lbl_more_like_this));

                addInfoRows(adapter);
                break;
            case TRAILER:

                //Cast/Crew
                if (mBaseItem.getPeople() != null && !mBaseItem.getPeople().isEmpty()) {
                    ItemRowAdapter castAdapter = new ItemRowAdapter(mBaseItem.getPeople(), requireContext(), new CardPresenter(true, 130), adapter);
                    addItemRow(adapter, castAdapter, 0, getString(R.string.lbl_cast_crew));
                }

                //Similar
                ItemRowAdapter similarTrailerAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createSimilarItemsRequest(mBaseItem.getId()), QueryType.SimilarMovies, new CardPresenter(), adapter);
                addItemRow(adapter, similarTrailerAdapter, 4, getString(R.string.lbl_more_like_this));
                addInfoRows(adapter);
                break;
            case PERSON:
                ItemRowAdapter personMoviesAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createPersonItemsRequest(mBaseItem.getId(), BaseItemKind.MOVIE), 100, false, new CardPresenter(), adapter);
                addItemRow(adapter, personMoviesAdapter, 0, getString(R.string.lbl_movies));

                ItemRowAdapter personSeriesAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createPersonItemsRequest(mBaseItem.getId(), BaseItemKind.SERIES), 100, false, new CardPresenter(), adapter);
                addItemRow(adapter, personSeriesAdapter, 1, getString(R.string.lbl_tv_series));

                ItemRowAdapter personEpisodesAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createPersonItemsRequest(mBaseItem.getId(), BaseItemKind.EPISODE), 100, false, new CardPresenter(), adapter);
                addItemRow(adapter, personEpisodesAdapter, 2, getString(R.string.lbl_episodes));

                break;
            case MUSIC_ARTIST:
                ItemRowAdapter artistAlbumsAdapter = new ItemRowAdapter(requireContext(),  BrowsingUtils.createArtistItemsRequest(mBaseItem.getId(), BaseItemKind.MUSIC_ALBUM), 100, false, new CardPresenter(), adapter);
                addItemRow(adapter, artistAlbumsAdapter, 0, getString(R.string.lbl_albums));

                break;
            case SERIES:
                ItemRowAdapter nextUpAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createSeriesGetNextUpRequest(mBaseItem.getId()), false, new CardPresenter(true, 130), adapter);
                addItemRow(adapter, nextUpAdapter, 0, getString(R.string.lbl_next_up));

                ItemRowAdapter seasonsAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createSeasonsRequest(mBaseItem.getId()), new CardPresenter(), adapter);
                addItemRow(adapter, seasonsAdapter, 1, getString(R.string.lbl_seasons));

                //Specials
                if (mBaseItem.getSpecialFeatureCount() != null && mBaseItem.getSpecialFeatureCount() > 0) {
                    addItemRow(adapter, new ItemRowAdapter(requireContext(), new GetSpecialsRequest(mBaseItem.getId()), new CardPresenter(), adapter), 3, getString(R.string.lbl_specials));
                }

                ItemRowAdapter upcomingAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createUpcomingEpisodesRequest(mBaseItem.getId()), new CardPresenter(), adapter);
                addItemRow(adapter, upcomingAdapter, 2, getString(R.string.lbl_upcoming));

                if (mBaseItem.getPeople() != null && !mBaseItem.getPeople().isEmpty()) {
                    ItemRowAdapter seriesCastAdapter = new ItemRowAdapter(mBaseItem.getPeople(), requireContext(), new CardPresenter(true, 130), adapter);
                    addItemRow(adapter, seriesCastAdapter, 3, getString(R.string.lbl_cast_crew));
                }

                ItemRowAdapter similarAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createSimilarItemsRequest(mBaseItem.getId()), QueryType.SimilarSeries, new CardPresenter(), adapter);
                addItemRow(adapter, similarAdapter, 4, getString(R.string.lbl_more_like_this));
                break;

            case EPISODE:
                if (mBaseItem.getSeasonId() != null && mBaseItem.getIndexNumber() != null) {
                    // query index is zero-based but episode no is not
                    ItemRowAdapter nextAdapter = new ItemRowAdapter(requireContext(), BrowsingUtils.createNextEpisodesRequest(mBaseItem.getSeasonId(), mBaseItem.getIndexNumber()), 0, false, true, new CardPresenter(true, 120), adapter);
                    addItemRow(adapter, nextAdapter, 5, getString(R.string.lbl_next_episode));
                }

                //Guest stars
                if (mBaseItem.getPeople() != null && !mBaseItem.getPeople().isEmpty()) {
                    List<BaseItemPerson> guests = new ArrayList<>();
                    for (BaseItemPerson person : mBaseItem.getPeople()) {
                        if (person.getType() == PersonKind.GUEST_STAR) guests.add(person);
                    }
                    if (!guests.isEmpty()) {
                        ItemRowAdapter castAdapter = new ItemRowAdapter(guests, requireContext(), new CardPresenter(true, 130), adapter);
                        addItemRow(adapter, castAdapter, 0, getString(R.string.lbl_guest_stars));
                    }
                }

                //Chapters
                if (mBaseItem.getChapters() != null && !mBaseItem.getChapters().isEmpty()) {
                    List<ChapterItemInfo> chapters = BaseItemExtensionsKt.buildChapterItems(mBaseItem, api.getValue());
                    ItemRowAdapter chapterAdapter = new ItemRowAdapter(requireContext(), chapters, new CardPresenter(true, 120), adapter);
                    addItemRow(adapter, chapterAdapter, 1, getString(R.string.lbl_chapters));
                }

                addInfoRows(adapter);
                break;

            default:
                addInfoRows(adapter);
        }
    }

    private void addInfoRows(MutableObjectAdapter<Row> adapter) {
        if (KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getDebuggingEnabled()) && mBaseItem.getMediaSources() != null) {
            for (MediaSourceInfo ms : mBaseItem.getMediaSources()) {
                if (ms.getMediaStreams() != null && !ms.getMediaStreams().isEmpty()) {
                    HeaderItem header = new HeaderItem("Media Details" + (ms.getContainer() != null ? " (" + ms.getContainer() + ")" : ""));
                    ArrayObjectAdapter infoAdapter = new ArrayObjectAdapter(new InfoCardPresenter());
                    for (MediaStream stream : ms.getMediaStreams()) {
                        infoAdapter.add(stream);
                    }

                    adapter.add(new ListRow(header, infoAdapter));

                }
            }
        }
    }

    private void updateInfo(BaseItemDto item) {
        if (buttonTypeList.contains(item.getType())) {
            mDetailsOverviewRow.clearActions();
            addButtons(BUTTON_SIZE);
        }

        mLastUpdated = Instant.now();
    }

    public void setTitle(String title) {
        mDorPresenter.getViewHolder().setTitle(title);
    }

    private String getRunTime() {
        Long runtime = Utils.getSafeValue(mBaseItem.getRunTimeTicks(), mBaseItem.getRunTimeTicks());

        if (runtime == null || runtime <= 0) {
            return "";
        }

        int totalMinutes = (int) Math.ceil((double) runtime / 600000000);

        int hours = totalMinutes / 60;
        int minutes = totalMinutes % 60;

        if (hours > 0) {
            return getString(R.string.runtime_hours_minutes, hours, minutes);
        }

        return getString(R.string.runtime_minutes, minutes);
    }

    private String getEndTime() {
        if (mBaseItem != null && mBaseItem.getType() != BaseItemKind.MUSIC_ARTIST && mBaseItem.getType() != BaseItemKind.PERSON) {
            Long runtime = Utils.getSafeValue(mBaseItem.getRunTimeTicks(), mBaseItem.getRunTimeTicks());
            if (runtime != null && runtime > 0) {
                LocalDateTime endTime = mBaseItem.getType() == BaseItemKind.PROGRAM && mBaseItem.getEndDate() != null ? mBaseItem.getEndDate() : LocalDateTime.now().plusNanos(runtime * 100);
                if (JavaCompat.getCanResume(mBaseItem)) {
                    endTime = LocalDateTime.now().plusNanos((runtime - mBaseItem.getUserData().getPlaybackPositionTicks()) * 100);
                }
                return DateTimeExtensionsKt.getTimeFormatter(getContext()).format(endTime);
            }

        }
        return "";
    }

    void addItemToQueue() {
        BaseItemDto baseItem = mBaseItem;
        if (baseItem.getType() == BaseItemKind.AUDIO || baseItem.getType() == BaseItemKind.MUSIC_ALBUM || baseItem.getType() == BaseItemKind.MUSIC_ARTIST) {
            if (baseItem.getType() == BaseItemKind.MUSIC_ALBUM || baseItem.getType() == BaseItemKind.MUSIC_ARTIST) {
                playbackHelper.getValue().getItemsToPlay(getContext(), baseItem, false, false, new Response<List<BaseItemDto>>(getLifecycle()) {
                    @Override
                    public void onResponse(List<BaseItemDto> response) {
                        if (!isActive()) return;
                        mediaManager.getValue().addToAudioQueue(response);
                    }
                });
            } else {
                mediaManager.getValue().queueAudioItem(baseItem);
            }
        }
    }

    void gotoSeries() {
        navigationRepository.getValue().navigate(Destinations.INSTANCE.itemDetails(mBaseItem.getSeriesId()));
    }

    private void deleteItem() {
        Timber.i("Showing item delete confirmation");
        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.item_delete_confirm_title))
                .setMessage(getString(R.string.item_delete_confirm_message))
                .setNegativeButton(R.string.lbl_no, null)
                .setPositiveButton(R.string.lbl_delete, (dialog, which) -> {
                    FullDetailsFragmentHelperKt.deleteItem(
                            this,
                            api.getValue(),
                            mBaseItem,
                            dataRefreshService.getValue(),
                            navigationRepository.getValue()
                    );
                })
                .show();
    }

    TextUnderButton favButton = null;
    TextUnderButton shuffleButton = null;
    TextUnderButton goToSeriesButton = null;
    TextUnderButton queueButton = null;
    TextUnderButton deleteButton = null;
    TextUnderButton moreButton;
    TextUnderButton playButton = null;
    TextUnderButton trailerButton = null;

    private void addButtons(int buttonSize) {
        BaseItemDto baseItem = mBaseItem;
        String buttonLabel;
        if (baseItem.getType() == BaseItemKind.SERIES) {
            buttonLabel = getString(R.string.lbl_play_next_up);
        } else {
            long startPos = 0;
            if (JavaCompat.getCanResume(mBaseItem)) {
                startPos = (mBaseItem.getUserData().getPlaybackPositionTicks() / 10000) - getResumePreroll();
            }
            buttonLabel = getString(R.string.lbl_resume_from, TimeUtils.formatMillis(startPos));
        }
        mResumeButton = TextUnderButton.create(requireContext(), R.drawable.ic_resume, buttonSize, 2, buttonLabel, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FullDetailsFragmentHelperKt.resumePlayback(FullDetailsFragment.this, v);
            }
        });

        if (BaseItemExtensionsKt.canPlay(baseItem)) {
            mDetailsOverviewRow.addAction(mResumeButton);
            boolean isSeries = baseItem.getType() == BaseItemKind.SERIES;
            boolean isStarted = baseItem.getUserData().getPlayedPercentage() != null && baseItem.getUserData().getPlayedPercentage() > 0;

            playButton = TextUnderButton.create(requireContext(), R.drawable.ic_play, buttonSize, 2, getString(BaseItemExtensionsKt.isLiveTv(mBaseItem) ? R.string.lbl_tune_to_channel : Utils.getSafeValue(mBaseItem.isFolder(), false) ? R.string.lbl_play_all : R.string.lbl_play), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    play(mBaseItem, 0, false);
                }
            });
            mDetailsOverviewRow.addAction(playButton);

            if (isSeries && !isStarted) {
                FullDetailsFragmentHelperKt.getNextUpEpisode(this, nextUpEpisode -> {
                    handleResumeButtonAndFocus(nextUpEpisode);
                    return null;
                });
            } else {
                handleResumeButtonAndFocus(null);
            }

            boolean isMusic = baseItem.getType() == BaseItemKind.MUSIC_ALBUM
                    || baseItem.getType() == BaseItemKind.MUSIC_ARTIST
                    || baseItem.getType() == BaseItemKind.AUDIO
                    || (baseItem.getType() == BaseItemKind.PLAYLIST && MediaType.AUDIO.equals(baseItem.getMediaType()));

            if (isMusic) {
                queueButton = TextUnderButton.create(requireContext(), R.drawable.ic_add, buttonSize, 2, getString(R.string.lbl_add_to_queue), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addItemToQueue();
                    }
                });
                mDetailsOverviewRow.addAction(queueButton);
            }

            if (Utils.getSafeValue(mBaseItem.isFolder(), false) || baseItem.getType() == BaseItemKind.MUSIC_ARTIST) {
                shuffleButton = TextUnderButton.create(requireContext(), R.drawable.ic_shuffle, buttonSize, 2, getString(R.string.lbl_shuffle_all), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        play(mBaseItem, 0, true);
                    }
                });
                mDetailsOverviewRow.addAction(shuffleButton);
            }

            if (baseItem.getType() == BaseItemKind.MUSIC_ARTIST) {
                TextUnderButton imix = TextUnderButton.create(requireContext(), R.drawable.ic_mix, buttonSize, 0, getString(R.string.lbl_instant_mix), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        playbackHelper.getValue().playInstantMix(requireContext(), baseItem);
                    }
                });
                mDetailsOverviewRow.addAction(imix);
            }
        }
        //Video versions button
        if (mBaseItem.getMediaSources() != null && mBaseItem.getMediaSources().size() > 1) {
            mVersionsButton = TextUnderButton.create(requireContext(), R.drawable.ic_guide, buttonSize, 0, getString(R.string.select_version), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (versions != null) {
                        addVersionsMenu(v);
                    } else {
                        versions = new ArrayList<>(mBaseItem.getMediaSources());
                        addVersionsMenu(v);
                    }
                }
            });
            mDetailsOverviewRow.addAction(mVersionsButton);
        }

        if (TrailerUtils.hasPlayableTrailers(requireContext(), mBaseItem)) {
            trailerButton = TextUnderButton.create(requireContext(), R.drawable.ic_trailer, buttonSize, 0, getString(R.string.lbl_play_trailers), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FullDetailsFragmentHelperKt.playTrailers(FullDetailsFragment.this);
                }
            });

            mDetailsOverviewRow.addAction(trailerButton);
        }

        if (mProgramInfo != null && Utils.canManageRecordings(KoinJavaComponent.<UserRepository>get(UserRepository.class).getCurrentUser().getValue())) {
            if (mBaseItem.getEndDate().isAfter(LocalDateTime.now())) {
                //Record button
                mRecordButton = TextUnderButton.create(requireContext(), R.drawable.ic_record, buttonSize, 4, getString(R.string.lbl_record), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mProgramInfo.getTimerId() == null) {
                            //Create one-off recording with defaults
                            FullDetailsFragmentHelperKt.getLiveTvDefaultTimer(FullDetailsFragment.this, mProgramInfo.getId(), seriesTimer -> {
                                FullDetailsFragmentHelperKt.createLiveTvSeriesTimer(FullDetailsFragment.this, seriesTimer, () -> {
                                    FullDetailsFragmentHelperKt.getLiveTvProgram(FullDetailsFragment.this, mProgramInfo.getId(), program -> {
                                        mProgramInfo = program;
                                        setRecSeriesTimer(program.getSeriesTimerId());
                                        setRecTimer(program.getTimerId());
                                        Utils.showToast(requireContext(), R.string.msg_set_to_record);
                                        return null;
                                    });
                                    return null;
                                });
                                return null;
                            });
                        } else {
                            FullDetailsFragmentHelperKt.cancelLiveTvSeriesTimer(FullDetailsFragment.this, mProgramInfo.getTimerId(), () -> {
                                setRecTimer(null);
                                dataRefreshService.getValue().setLastDeletedItemId(mProgramInfo.getId());
                                Utils.showToast(requireContext(), R.string.msg_recording_cancelled);
                                return null;
                            });
                        }
                    }
                });
                mRecordButton.setActivated(mProgramInfo.getTimerId() != null);

                mDetailsOverviewRow.addAction(mRecordButton);
            }

            if (mProgramInfo.isSeries() != null && mProgramInfo.isSeries()) {
                mRecSeriesButton = TextUnderButton.create(requireContext(), R.drawable.ic_record_series, buttonSize, 4, getString(R.string.lbl_record_series), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mProgramInfo.getSeriesTimerId() == null) {
                            //Create series recording with default options
                            FullDetailsFragmentHelperKt.getLiveTvDefaultTimer(FullDetailsFragment.this, mProgramInfo.getId(), seriesTimer -> {
                                FullDetailsFragmentHelperKt.createLiveTvSeriesTimer(FullDetailsFragment.this, seriesTimer, () -> {
                                    FullDetailsFragmentHelperKt.getLiveTvProgram(FullDetailsFragment.this, mProgramInfo.getId(), program -> {
                                        mProgramInfo = program;
                                        setRecSeriesTimer(program.getSeriesTimerId());
                                        setRecTimer(program.getTimerId());
                                        Utils.showToast(requireContext(), R.string.msg_set_to_record);
                                        return null;
                                    });
                                    return null;
                                });
                                return null;
                            });
                        } else {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle(getString(R.string.lbl_cancel_series))
                                    .setMessage(getString(R.string.msg_cancel_entire_series))
                                    .setNegativeButton(R.string.lbl_no, null)
                                    .setPositiveButton(R.string.lbl_yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            FullDetailsFragmentHelperKt.cancelLiveTvSeriesTimer(FullDetailsFragment.this, mProgramInfo.getSeriesTimerId(), () -> {
                                                setRecSeriesTimer(null);
                                                setRecTimer(null);
                                                dataRefreshService.getValue().setLastDeletedItemId(mProgramInfo.getId());
                                                Utils.showToast(requireContext(), R.string.msg_recording_cancelled);
                                                return null;
                                            });
                                        }
                                    }).show();
                        }
                    }
                });
                mRecSeriesButton.setActivated(mProgramInfo.getSeriesTimerId() != null);

                mDetailsOverviewRow.addAction(mRecSeriesButton);

                mSeriesSettingsButton = TextUnderButton.create(requireContext(), R.drawable.ic_settings, buttonSize, 2, getString(R.string.lbl_series_settings), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showRecordingOptions(mProgramInfo.getSeriesTimerId(), mProgramInfo, true);
                    }
                });

                mSeriesSettingsButton.setVisibility(mProgramInfo.getSeriesTimerId() != null ? View.VISIBLE : View.GONE);

                mDetailsOverviewRow.addAction(mSeriesSettingsButton);
            }
        }

        org.jellyfin.sdk.model.api.UserItemDataDto userData = mBaseItem.getUserData();
        if (userData != null && mProgramInfo == null) {
            if (mBaseItem.getType() != BaseItemKind.MUSIC_ARTIST && mBaseItem.getType() != BaseItemKind.PERSON) {
                mWatchedToggleButton = TextUnderButton.create(requireContext(), R.drawable.ic_watch, buttonSize, 0, getString(R.string.lbl_watched), markWatchedListener);
                mWatchedToggleButton.setActivated(userData.getPlayed());
                mDetailsOverviewRow.addAction(mWatchedToggleButton);
            }

            //Favorite
            favButton = TextUnderButton.create(requireContext(), R.drawable.ic_heart, buttonSize, 2, getString(R.string.lbl_favorite), new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    FullDetailsFragmentHelperKt.toggleFavorite(FullDetailsFragment.this);
                }
            });
            favButton.setActivated(userData.isFavorite());
            mDetailsOverviewRow.addAction(favButton);

        }

        if (mBaseItem.getType() == BaseItemKind.EPISODE && mBaseItem.getSeriesId() != null) {
            //add the prev button first so it will be there in proper position - we'll show it later if needed
            mPrevButton = TextUnderButton.create(requireContext(), R.drawable.ic_previous_episode, buttonSize, 3, getString(R.string.lbl_previous_episode), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mPrevItemId != null) {
                        navigationRepository.getValue().navigate(Destinations.INSTANCE.itemDetails(mPrevItemId));
                    }
                }
            });

            mDetailsOverviewRow.addAction(mPrevButton);

            //now go get our prev episode id
            FullDetailsFragmentHelperKt.populatePreviousButton(FullDetailsFragment.this);

            goToSeriesButton = TextUnderButton.create(requireContext(), R.drawable.ic_tv, buttonSize, 0, getString(R.string.lbl_goto_series), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    gotoSeries();
                }
            });
            mDetailsOverviewRow.addAction(goToSeriesButton);
        }

        boolean deletableItem = false;
        UserDto currentUser = KoinJavaComponent.<UserRepository>get(UserRepository.class).getCurrentUser().getValue();
        if (mBaseItem.getType() == BaseItemKind.RECORDING && currentUser.getPolicy().getEnableLiveTvManagement() && mBaseItem.getCanDelete() != null)
            deletableItem = mBaseItem.getCanDelete();
        else if (mBaseItem.getCanDelete() != null) deletableItem = mBaseItem.getCanDelete();

        if (deletableItem) {
            deleteButton = TextUnderButton.create(requireContext(), R.drawable.ic_delete, buttonSize, 0, getString(R.string.lbl_delete), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteItem();
                }
            });
            mDetailsOverviewRow.addAction(deleteButton);
        }

        if (mSeriesTimerInfo != null) {
            //Settings
            mDetailsOverviewRow.addAction(TextUnderButton.create(requireContext(), R.drawable.ic_settings, buttonSize, 0, getString(R.string.lbl_series_settings), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    //show recording options
                    showRecordingOptions(mSeriesTimerInfo.getId(), mBaseItem, true);
                }
            }));

            //Delete
            TextUnderButton del = TextUnderButton.create(requireContext(), R.drawable.ic_trash, buttonSize, 0, getString(R.string.lbl_cancel_series), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.lbl_delete)
                            .setMessage(getString(R.string.msg_cancel_entire_series))
                            .setPositiveButton(R.string.lbl_cancel_series, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    FullDetailsFragmentHelperKt.cancelLiveTvSeriesTimer(FullDetailsFragment.this, mSeriesTimerInfo.getId(), () -> {
                                        Utils.showToast(requireContext(), getString(R.string.msg_recording_cancelled));
                                        dataRefreshService.getValue().setLastDeletedItemId(UUIDSerializerKt.toUUID(mSeriesTimerInfo.getId()));
                                        if (navigationRepository.getValue().getCanGoBack()) {
                                            navigationRepository.getValue().goBack();
                                        } else {
                                            navigationRepository.getValue().reset(Destinations.INSTANCE.getHome());
                                        }
                                        return null;
                                    });
                                }
                            })
                            .setNegativeButton(R.string.lbl_no, null)
                            .show();

                }
            });
            mDetailsOverviewRow.addAction(del);

        }

        //Now, create a more button to show if needed
        moreButton = TextUnderButton.create(requireContext(), R.drawable.ic_more, buttonSize, 0, getString(R.string.lbl_other_options), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FullDetailsFragmentHelperKt.showDetailsMenu(FullDetailsFragment.this, v, mBaseItem);
            }
        });

        moreButton.setVisibility(View.GONE);
        mDetailsOverviewRow.addAction(moreButton);
        if (mBaseItem.getType() != BaseItemKind.EPISODE)
            showMoreButtonIfNeeded();  //Episodes check for previous and then call this above
    }

    private void handleResumeButtonAndFocus(BaseItemDto nextUpEpisode) {
        boolean isSeries = mBaseItem.getType() == BaseItemKind.SERIES;
        boolean isFinished = mBaseItem.getUserData().getPlayed();
        boolean isStarted = mBaseItem.getUserData().getPlayedPercentage() != null && mBaseItem.getUserData().getPlayedPercentage() > 0;
        if (!isStarted && nextUpEpisode != null) {
            isStarted = nextUpEpisode.getUserData().getPlaybackPositionTicks() > 0;
        }

        boolean resumeButtonVisible = (isSeries && isStarted && !isFinished) || (JavaCompat.getCanResume(mBaseItem));
        mResumeButton.setVisibility(resumeButtonVisible ? View.VISIBLE : View.GONE);

        if (resumeButtonVisible) {
            mResumeButton.requestFocus();
        } else {
            playButton.requestFocus();
        }
    }

    private void addVersionsMenu(View v) {
        PopupMenu menu = new PopupMenu(requireContext(), v, Gravity.END);

        for (int i = 0; i < versions.size(); i++) {
            menu.getMenu().add(Menu.NONE, i, Menu.NONE, versions.get(i).getName());
        }

        menu.getMenu().setGroupCheckable(0, true, true);
        menu.getMenu().getItem(mDetailsOverviewRow.getSelectedMediaSourceIndex()).setChecked(true);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                mDetailsOverviewRow.setSelectedMediaSourceIndex(menuItem.getItemId());
                FullDetailsFragmentHelperKt.getItem(FullDetailsFragment.this, UUIDSerializerKt.toUUID(versions.get(mDetailsOverviewRow.getSelectedMediaSourceIndex()).getId()), item -> {
                    if (item == null) return null;

                    mBaseItem = item;
                    mDorPresenter.getViewHolder().setItem(mDetailsOverviewRow);
                    if (mVersionsButton != null) mVersionsButton.requestFocus();
                    return null;
                });
                return true;
            }
        });

        menu.show();
    }

    int collapsedOptions = 0;

    void showMoreButtonIfNeeded() {
        int visibleOptions = mDetailsOverviewRow.getVisibleActions();

        List<TextUnderButton> actionsList = new ArrayList<>();
        // added in order of priority (should match res/menu/menu_details_more.xml)
        if (queueButton != null) actionsList.add(queueButton);
        if (trailerButton != null) actionsList.add(trailerButton);
        if (shuffleButton != null) actionsList.add(shuffleButton);
        if (favButton != null) actionsList.add(favButton);
        if (goToSeriesButton != null) actionsList.add(goToSeriesButton);

        // reverse the list so the less important actions are hidden first
        Collections.reverse(actionsList);

        collapsedOptions = 0;
        for (TextUnderButton action : actionsList) {
            if (visibleOptions - (ViewKt.isVisible(action) ? 1 : 0) + (!ViewKt.isVisible(moreButton) && collapsedOptions > 0 ? 1 : 0) < 5) {
                if (!ViewKt.isVisible(action)) {
                    action.setVisibility(View.VISIBLE);
                    visibleOptions++;
                }
            } else {
                if (ViewKt.isVisible(action)) {
                    action.setVisibility(View.GONE);
                    visibleOptions--;
                }
                collapsedOptions++;
            }
        }
        moreButton.setVisibility(collapsedOptions > 0 ? View.VISIBLE : View.GONE);
    }

    RecordPopup mRecordPopup;

    public void showRecordingOptions(String id, final BaseItemDto program, final boolean recordSeries) {
        if (mRecordPopup == null) {
            int width = Utils.convertDpToPixel(requireContext(), 600);
            Point size = new Point();
            requireActivity().getWindowManager().getDefaultDisplay().getSize(size);
            mRecordPopup = new RecordPopup(requireActivity(), getLifecycle(), mRowsFragment.getView(), (size.x / 2) - (width / 2), mRowsFragment.getView().getTop() + 40, width);
        }
        FullDetailsFragmentHelperKt.getLiveTvSeriesTimer(this, id, response -> {
            if (recordSeries || Utils.isTrue(program.isSports())) {
                mRecordPopup.setContent(requireContext(), program, response, FullDetailsFragment.this, recordSeries);
                mRecordPopup.show();
            } else {
                //just record with defaults
                FullDetailsFragmentHelperKt.createLiveTvSeriesTimer(this, response, () -> {
                    Utils.showToast(requireContext(), R.string.msg_set_to_record);

                    // we have to re-retrieve the program to get the timer id
                    FullDetailsFragmentHelperKt.getLiveTvProgram(this, mProgramInfo.getId(), programInfo -> {
                        setRecTimer(programInfo.getTimerId());
                        return null;
                    });

                    return null;
                });
            }
            return null;
        });
    }

    private int ratingSetupRetries = 0;

    private void loadCommunityRatings() {
        if (mBaseItem == null) return;

        BaseItemKind type = mBaseItem.getType();
        if (type != BaseItemKind.MOVIE && type != BaseItemKind.SERIES && type != BaseItemKind.EPISODE) {
            return;
        }

        Timber.d("loadCommunityRatings called for item: %s", mBaseItem.getId());

        // Set up the star click listener for inline rating
        if (mDorPresenter.getViewHolder() != null) {
            Timber.d("Setting up rating click listener");
            mDorPresenter.getViewHolder().setOnRatingClickListener(rating -> {
                Timber.d("Rating click listener invoked with rating: %d", rating);
                handleRatingClick(rating);
                return null;
            });

            // Set up click listener for "X ratings" text to open all ratings
            mDorPresenter.getViewHolder().setItemName(mBaseItem.getName());
            mDorPresenter.getViewHolder().setOnAllRatingsClickListener(() -> {
                Timber.d("All ratings clicked for item: %s", mBaseItem.getId());
                openAllRatings();
                return null;
            });

            ratingSetupRetries = 0;
        } else {
            Timber.w("ViewHolder is null, retrying...");
            // Retry after a short delay if ViewHolder isn't ready yet
            if (ratingSetupRetries < 5) {
                ratingSetupRetries++;
                new Handler().postDelayed(() -> loadCommunityRatings(), 200);
                return;
            }
        }

        // Load and display current ratings
        FullDetailsFragmentHelperKt.loadRatingStats(this, mBaseItem.getId(), false, stats -> {
            Timber.d("Rating stats loaded: %s", stats);
            if (stats != null) {
                currentUserRating = stats.getUserRating();
            }
            if (mDorPresenter.getViewHolder() != null) {
                mDorPresenter.getViewHolder().setCommunityRating(stats);
            }
            return null;
        });
    }

    private Integer currentUserRating = null;

    private void handleRatingClick(int rating) {
        if (mBaseItem == null) return;

        Timber.d("handleRatingClick called with rating: %d for item: %s (current user rating: %s)", rating, mBaseItem.getId(), currentUserRating);

        // If clicking on current user rating, delete it
        if (currentUserRating != null && currentUserRating == rating) {
            Timber.d("Deleting current rating");
            FullDetailsFragmentHelperKt.deleteRating(this, mBaseItem.getId(), success -> {
                Timber.d("Rating delete result: %s", success);
                if (success) {
                    Utils.showToast(requireContext(), R.string.msg_rating_deleted);
                    currentUserRating = null;
                    refreshRatings();
                } else {
                    Utils.showToast(requireContext(), R.string.msg_rating_error);
                }
                return null;
            });
        } else {
            // Show rating popup with review option
            showRatingPopup(rating);
        }
    }

    private void showRatingPopup(int preSelectedRating) {
        if (mBaseItem == null) return;

        View anchorView = getView();
        if (anchorView == null) return;

        // Calculate center position - increased height to 420dp for review section
        int popupWidth = Utils.convertDpToPixel(requireContext(), 500);
        int popupHeight = Utils.convertDpToPixel(requireContext(), 420);
        int posLeft = (mMetrics.widthPixels - popupWidth) / 2;
        int posTop = (mMetrics.heightPixels - popupHeight) / 2;

        mRatingPopup = new RatingPopup(
            requireContext(),
            getViewLifecycleOwner(),
            anchorView,
            posLeft,
            posTop
        );

        String itemName = mBaseItem.getName() != null ? mBaseItem.getName() : "";
        mRatingPopup.setContent(mBaseItem.getId(), itemName, preSelectedRating, stats -> {
            // Callback when rating is changed
            Timber.d("Rating changed callback: %s", stats);
            if (stats != null) {
                currentUserRating = stats.getUserRating();
            } else {
                currentUserRating = null;
            }
            if (mDorPresenter.getViewHolder() != null) {
                mDorPresenter.getViewHolder().setCommunityRating(stats);
            }
            return null;
        });

        mRatingPopup.show();
    }

    private void refreshRatings() {
        FullDetailsFragmentHelperKt.loadRatingStats(this, mBaseItem.getId(), true, stats -> {
            Timber.d("Rating stats refreshed: %s", stats);
            if (stats != null) {
                currentUserRating = stats.getUserRating();
            } else {
                currentUserRating = null;
            }
            if (mDorPresenter.getViewHolder() != null) {
                mDorPresenter.getViewHolder().setCommunityRating(stats);
            }
            return null;
        });
    }

    private void openAllRatings() {
        if (mBaseItem == null) return;

        String itemName = mBaseItem.getName();
        if (itemName == null) itemName = "";

        startActivity(AllRatingsActivity.Companion.createIntent(
            requireContext(),
            mBaseItem.getId(),
            itemName
        ));
    }

    private void loadUserReviewsInline() {
        Timber.i("REVIEWS: loadUserReviewsInline called");
        if (mBaseItem == null || mDorPresenter == null || mDorPresenter.getViewHolder() == null) {
            Timber.i("REVIEWS: mBaseItem or presenter is null, returning");
            return;
        }

        BaseItemKind type = mBaseItem.getType();
        if (type != BaseItemKind.MOVIE && type != BaseItemKind.SERIES && type != BaseItemKind.EPISODE) {
            return;
        }

        String currentUserId = null;
        if (sessionRepository.getValue().getCurrentSession().getValue() != null) {
            currentUserId = sessionRepository.getValue().getCurrentSession().getValue().getUserId().toString();
        }
        final String finalCurrentUserId = currentUserId;

        // Set up click listener for reviews
        mDorPresenter.getViewHolder().setOnReviewClickListener(review -> {
            startActivity(org.jellyfin.androidtv.ui.ratings.ReviewDetailActivity.Companion.createIntent(
                requireContext(),
                mBaseItem.getId(),
                review,
                finalCurrentUserId
            ));
            return null;
        });

        // Set up like/dislike listener for reviews
        mDorPresenter.getViewHolder().setOnReviewLikeListener((review, isLike) -> {
            // Check if user is trying to like their own review (normalize UUIDs for comparison)
            String currentId = finalCurrentUserId != null ? finalCurrentUserId.toLowerCase().replace("-", "") : "";
            String reviewerId = review.getUserId() != null ? review.getUserId().toLowerCase().replace("-", "") : "";

            if (!currentId.isEmpty() && currentId.equals(reviewerId)) {
                String[] selfLikeMessages = {
                    "You can't rate your own review!",
                    "Nice try, narcissist!",
                    "Loving yourself is great, but not here.",
                    "Your mom already thinks you're special.",
                    "Self-love has limits, buddy.",
                    "Even Kanye wouldn't do that... wait, he would.",
                    "Put the thumb down and step away slowly.",
                    "Did you just try to high-five yourself?",
                    "That's not how validation works.",
                    "Go touch grass instead.",
                    "Your hand must be tired from patting your own back.",
                    "Plot twist: You're not that interesting.",
                    "Sir, this is a Wendy's.",
                    "Confidence is sexy, this is just sad.",
                    "Error 69: Self-appreciation overflow."
                };
                String randomMessage = selfLikeMessages[new java.util.Random().nextInt(selfLikeMessages.length)];
                android.widget.Toast.makeText(
                    requireContext(),
                    randomMessage,
                    android.widget.Toast.LENGTH_SHORT
                ).show();
                return null;
            }

            // Call the API to like/dislike
            FullDetailsFragmentHelperKt.likeReview(this, review.getUserId(), mBaseItem.getId(), isLike, response -> {
                if (response != null) {
                    // Refresh reviews to show updated like counts
                    loadUserReviewsInline();
                    String action = isLike ? "Liked" : "Disliked";
                    android.widget.Toast.makeText(
                        requireContext(),
                        action + " review!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show();
                }
                return null;
            });
            return null;
        });

        // Set up comment listener for reviews
        mDorPresenter.getViewHolder().setOnReviewCommentListener(review -> {
            // Show comments dialog
            showCommentsDialog(review, finalCurrentUserId);
            return null;
        });

        FullDetailsFragmentHelperKt.loadUserReviews(this, mBaseItem.getId(), reviews -> {
            Timber.i("REVIEWS: callback received, count=%d", reviews != null ? reviews.size() : 0);
            if (mDorPresenter.getViewHolder() != null) {
                mDorPresenter.getViewHolder().setReviews(reviews);
            }
            return null;
        });
    }

    private void showCommentsDialog(org.jellyfin.androidtv.data.ratings.UserRatingDetail review, String currentUserId) {
        Timber.d("Opening comments dialog for review by %s", review.getUsername());

        // Create dialog with custom layout
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        // Header with review info
        android.widget.TextView headerText = new android.widget.TextView(requireContext());
        headerText.setText("Review by " + review.getUsername() + " - " + review.getRating() + "/10");
        headerText.setTextSize(18);
        headerText.setTextColor(android.graphics.Color.WHITE);
        headerText.setPadding(0, 0, 0, 16);
        layout.addView(headerText);

        // Review text
        if (review.getReviewText() != null && !review.getReviewText().isEmpty()) {
            android.widget.TextView reviewText = new android.widget.TextView(requireContext());
            reviewText.setText("\"" + review.getReviewText() + "\"");
            reviewText.setTextSize(14);
            reviewText.setTextColor(android.graphics.Color.parseColor("#CCCCCC"));
            reviewText.setPadding(0, 0, 0, 20);
            layout.addView(reviewText);
        }

        // Comments section
        android.widget.TextView commentsHeader = new android.widget.TextView(requireContext());
        commentsHeader.setText("Comments (" + review.getCommentCount() + ")");
        commentsHeader.setTextSize(16);
        commentsHeader.setTextColor(android.graphics.Color.parseColor("#FFD700"));
        commentsHeader.setPadding(0, 10, 0, 10);
        layout.addView(commentsHeader);

        // ScrollView for comments
        android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        android.widget.LinearLayout commentsContainer = new android.widget.LinearLayout(requireContext());
        commentsContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        scrollView.addView(commentsContainer);
        layout.addView(scrollView);

        // Loading text
        android.widget.TextView loadingText = new android.widget.TextView(requireContext());
        loadingText.setText("Loading comments...");
        loadingText.setTextColor(android.graphics.Color.parseColor("#888888"));
        commentsContainer.addView(loadingText);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setTitle("💬 Comments")
            .setView(layout)
            .setPositiveButton("Close", null)
            .setNeutralButton("Add Comment", (d, w) -> {
                showAddCommentDialog(review);
            })
            .create();

        dialog.show();

        // Load comments from API
        FullDetailsFragmentHelperKt.loadReviewComments(this, review.getUserId(), mBaseItem.getId(), comments -> {
            commentsContainer.removeAllViews();
            if (comments == null || comments.isEmpty()) {
                android.widget.TextView noComments = new android.widget.TextView(requireContext());
                noComments.setText("No comments yet. Be the first to comment!");
                noComments.setTextColor(android.graphics.Color.parseColor("#888888"));
                noComments.setPadding(0, 10, 0, 10);
                commentsContainer.addView(noComments);
            } else {
                for (org.jellyfin.androidtv.data.ratings.ReviewComment comment : comments) {
                    android.widget.LinearLayout commentLayout = new android.widget.LinearLayout(requireContext());
                    commentLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
                    commentLayout.setPadding(0, 10, 0, 10);

                    android.widget.TextView commenterName = new android.widget.TextView(requireContext());
                    commenterName.setText(comment.getCommenterName());
                    commenterName.setTextSize(14);
                    commenterName.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
                    commentLayout.addView(commenterName);

                    android.widget.TextView commentText = new android.widget.TextView(requireContext());
                    commentText.setText(comment.getText());
                    commentText.setTextSize(13);
                    commentText.setTextColor(android.graphics.Color.WHITE);
                    commentLayout.addView(commentText);

                    // Divider
                    android.view.View divider = new android.view.View(requireContext());
                    divider.setBackgroundColor(android.graphics.Color.parseColor("#333333"));
                    divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    commentLayout.addView(divider);

                    commentsContainer.addView(commentLayout);
                }
            }
            return null;
        });
    }

    private void showAddCommentDialog(org.jellyfin.androidtv.data.ratings.UserRatingDetail review) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("Write your comment...");
        input.setTextColor(android.graphics.Color.WHITE);
        input.setHintTextColor(android.graphics.Color.parseColor("#888888"));
        input.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(requireContext())
            .setTitle("Add Comment")
            .setMessage("Comment on " + review.getUsername() + "'s review")
            .setView(input)
            .setPositiveButton("Submit", (dialog, which) -> {
                String commentText = input.getText().toString().trim();
                if (!commentText.isEmpty()) {
                    FullDetailsFragmentHelperKt.submitComment(this, review.getUserId(), mBaseItem.getId(), commentText, success -> {
                        if (success) {
                            android.widget.Toast.makeText(requireContext(), "Comment added!", android.widget.Toast.LENGTH_SHORT).show();
                            loadUserReviewsInline(); // Refresh to update comment count
                        } else {
                            android.widget.Toast.makeText(requireContext(), "Failed to add comment", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        return null;
                    });
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(final Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (!(item instanceof BaseRowItem)) return;
            itemLauncher.getValue().launch((BaseRowItem) item, (ItemRowAdapter) ((ListRow) row).getAdapter(), requireContext());
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (!(item instanceof BaseRowItem)) {
                mCurrentItem = null;
            } else {
                mCurrentItem = (BaseRowItem) item;
            }
        }
    }

    private View.OnClickListener markWatchedListener = new View.OnClickListener() {
        @Override
        public void onClick(final View v) {
            FullDetailsFragmentHelperKt.togglePlayed(FullDetailsFragment.this);
        }
    };

    void shufflePlay() {
        play(mBaseItem, 0, true);
    }

    void play(final BaseItemDto item, final int pos, final boolean shuffle) {
        playbackHelper.getValue().getItemsToPlay(getContext(), item, pos == 0 && item.getType() == BaseItemKind.MOVIE, shuffle, new Response<List<BaseItemDto>>(getLifecycle()) {
            @Override
            public void onResponse(List<BaseItemDto> response) {
                if (!isActive()) return;
                if (response.isEmpty()) {
                    Timber.e("No items to play - ignoring play request.");
                    return;
                }

                interactionTracker.getValue().notifyStartSession(item, response);
                KoinJavaComponent.<PlaybackLauncher>get(PlaybackLauncher.class).launch(getContext(), response, pos, false, 0, shuffle);
            }
        });
    }

    void play(final List<BaseItemDto> items, final int pos, final boolean shuffle) {
        if (items.isEmpty()) return;
        if (shuffle) Collections.shuffle(items);
        KoinJavaComponent.<PlaybackLauncher>get(PlaybackLauncher.class).launch(getContext(), items, pos);
    }
}
