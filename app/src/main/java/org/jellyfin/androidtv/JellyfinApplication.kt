package org.jellyfin.androidtv

import android.app.Application
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.acra.ACRA
import org.jellyfin.androidtv.data.eventhandling.SocketHandler
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.integration.LeanbackChannelWorker
import org.jellyfin.androidtv.telemetry.TelemetryService
import org.jellyfin.androidtv.ui.card.TrailerPreviewPlayer
import org.jellyfin.androidtv.ui.shared.toolbar.EpisodePreviewPlayer
import org.koin.android.ext.android.inject
import java.util.concurrent.TimeUnit

@Suppress("unused")
class JellyfinApplication : Application() {
	override fun onCreate() {
		super.onCreate()

		// Don't run in ACRA service
		if (ACRA.isACRASenderServiceProcess()) return

		val notificationsRepository by inject<NotificationsRepository>()
		notificationsRepository.addDefaultNotifications()

		// Both preview players keep a decoder and native memory for as long as they exist, and
		// nothing else ever tears them down - once a preview had run, the app sat on a hardware
		// decoder for the rest of its life, including in the background where another app wants it.
		ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
			override fun onStop(owner: LifecycleOwner) {
				TrailerPreviewPlayer.release()
				EpisodePreviewPlayer.release()
			}
		})
	}

	/**
	 * Called from the StartupActivity when the user session is started.
	 */
	suspend fun onSessionStart() = withContext(Dispatchers.IO) {
		val workManager by inject<WorkManager>()
		val socketListener by inject<SocketHandler>()

		// Update background worker
		launch {
			// Cancel all current workers
			workManager.cancelAllWork().await()

			// Recreate periodic workers
			workManager.enqueueUniquePeriodicWork(
				LeanbackChannelWorker.PERIODIC_UPDATE_REQUEST_NAME,
				ExistingPeriodicWorkPolicy.UPDATE,
				PeriodicWorkRequestBuilder<LeanbackChannelWorker>(1, TimeUnit.HOURS)
					.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
					.build()
			).await()
		}

		// Update WebSockets
		launch { socketListener.updateSession() }
	}

	override fun attachBaseContext(base: Context?) {
		super.attachBaseContext(base)

		TelemetryService.init(this)
	}
}
