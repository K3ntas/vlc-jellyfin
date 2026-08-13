package org.jellyfin.androidtv.preference

import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.ApiClient
import kotlin.collections.set

/**
 * Repository to access special preference stores.
 */
class PreferencesRepository(
	private val api: ApiClient,
	private val liveTvPreferences: LiveTvPreferences,
	private val userSettingPreferences: UserSettingPreferences,
) {
	private val libraryPreferences = mutableMapOf<String, LibraryPreferences>()

	/**
	 * Warms the store for a library ahead of time.
	 *
	 * [getLibraryPreferences] has to stay synchronous while its callers are Java and Compose
	 * composition, and it blocks on a network round trip whenever the store is cold - on the main
	 * thread, because that is where those callers run. Fetching ahead of time means the blocking
	 * path finds the data already present and returns immediately.
	 */
	suspend fun prepareLibraryPreferences(preferencesId: String) {
		val store = libraryPreferences.getOrPut(preferencesId) { LibraryPreferences(preferencesId, api) }

		if (store.shouldUpdate) store.update()
	}

	fun getLibraryPreferences(preferencesId: String): LibraryPreferences {
		val store = libraryPreferences[preferencesId] ?: LibraryPreferences(preferencesId, api)

		libraryPreferences[preferencesId] = store

		// FIXME: Make [getLibraryPreferences] suspended when usages are converted to Kotlin
		if (store.shouldUpdate) runBlocking { store.update() }

		return store
	}

	suspend fun onSessionChanged() {
		// Note: Do not run parallel as the server can't deal with that
		// Relevant server issue: https://github.com/jellyfin/jellyfin/issues/5261
		liveTvPreferences.update()
		userSettingPreferences.update()

		libraryPreferences.clear()
	}
}
