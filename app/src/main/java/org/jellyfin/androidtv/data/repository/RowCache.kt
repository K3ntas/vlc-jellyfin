package org.jellyfin.androidtv.data.repository

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import java.io.File

/**
 * Remembers what was in each row so a row can be drawn before the server answers.
 *
 * Posters were already cached, but the lists themselves were not, so every launch showed empty
 * rows until a round trip completed - the artwork was sitting on disk with nothing to say where it
 * belonged. Rows now paint from the last known contents immediately and correct themselves once
 * the server replies, which is only visible at all when something actually changed.
 *
 * Entries are keyed by user as well as by query: two accounts on the same box must never see each
 * other's rows, however briefly.
 */
class RowCache(
	context: Context,
) {
	private companion object {
		/** Rows on screen at once, comfortably - a memory hit costs nothing at all. */
		const val MEMORY_ENTRIES = 48

		/** Well past a normal home screen, and each file is a few kilobytes. */
		const val MAX_FILES = 300
		const val PRUNE_TO = 200
	}

	private val directory = File(context.cacheDir, "row_cache")

	private val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = false
	}

	private val serializer = ListSerializer(BaseItemDto.serializer())
	private val memory = LruCache<String, List<BaseItemDto>>(MEMORY_ENTRIES)

	/** Last known contents of [key], or null when this row has not been seen before. */
	suspend fun read(key: String): List<BaseItemDto>? {
		memory.get(key)?.let { return it }

		return withContext(Dispatchers.IO) {
			val file = fileFor(key)
			if (!file.exists()) return@withContext null

			runCatching { json.decodeFromString(serializer, file.readText()) }
				.onFailure {
					// A cache that cannot be read is worse than no cache: drop it rather than
					// failing the row on every future launch
					Timber.d(it, "Discarding unreadable row cache")
					file.delete()
				}
				.getOrNull()
				?.also { memory.put(key, it) }
		}
	}

	suspend fun write(key: String, items: List<BaseItemDto>) {
		memory.put(key, items)

		withContext(Dispatchers.IO) {
			runCatching {
				directory.mkdirs()
				fileFor(key).writeText(json.encodeToString(serializer, items))
				prune()
			}.onFailure { Timber.d(it, "Could not write row cache") }
		}
	}

	/** Drops everything, for a sign-out or a user switch. */
	fun clear() {
		memory.evictAll()
		runCatching { directory.deleteRecursively() }
	}

	private fun fileFor(key: String) = File(directory, "${key.hashCode().toUInt()}.json")

	/**
	 * Keeps the directory from growing without bound as libraries and queries change over time.
	 * Oldest first, since the rows a user actually visits keep refreshing their timestamps.
	 */
	private fun prune() {
		val files = directory.listFiles() ?: return
		if (files.size <= MAX_FILES) return

		files.sortedBy { it.lastModified() }
			.take(files.size - PRUNE_TO)
			.forEach { it.delete() }
	}
}
