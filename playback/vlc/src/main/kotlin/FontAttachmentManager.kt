package org.jellyfin.playback.vlc

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Manages font attachments for ASS/SSA subtitle rendering in VLC.
 *
 * Font attachments are commonly embedded in MKV files for anime content
 * and are required for proper subtitle styling.
 */
class FontAttachmentManager(
	private val context: Context,
) {
	private val fontCacheDir: File by lazy {
		context.cacheDir.resolve("fonts").also { it.mkdirs() }
	}

	/**
	 * Get the font directory path for VLC configuration.
	 */
	fun getFontDirectory(): String = fontCacheDir.absolutePath

	/**
	 * Download and cache font attachments from a Jellyfin server.
	 *
	 * @param serverUrl The base URL of the Jellyfin server
	 * @param itemId The item ID
	 * @param attachments List of attachment info (index, name, mimeType)
	 */
	suspend fun downloadFonts(
		serverUrl: String,
		itemId: String,
		attachments: List<FontAttachment>,
	) = withContext(Dispatchers.IO) {
		attachments.forEach { attachment ->
			try {
				downloadFont(serverUrl, itemId, attachment)
			} catch (e: Exception) {
				Timber.e(e, "Failed to download font attachment: ${attachment.fileName}")
			}
		}
	}

	private suspend fun downloadFont(
		serverUrl: String,
		itemId: String,
		attachment: FontAttachment,
	) = withContext(Dispatchers.IO) {
		val targetFile = fontCacheDir.resolve(attachment.fileName)

		// Skip if font already cached
		if (targetFile.exists()) {
			Timber.d("Font already cached: ${attachment.fileName}")
			return@withContext
		}

		// Build the attachment URL
		// Jellyfin API: /Videos/{itemId}/Attachments/{index}
		val attachmentUrl = "${serverUrl.trimEnd('/')}/Videos/$itemId/Attachments/${attachment.index}"

		Timber.d("Downloading font: ${attachment.fileName} from $attachmentUrl")

		try {
			URL(attachmentUrl).openStream().use { input ->
				FileOutputStream(targetFile).use { output ->
					input.copyTo(output)
				}
			}
			Timber.i("Downloaded font: ${attachment.fileName}")
		} catch (e: Exception) {
			// Clean up partial file on failure
			targetFile.delete()
			throw e
		}
	}

	/**
	 * Clear the font cache.
	 */
	fun clearCache() {
		fontCacheDir.listFiles()?.forEach { it.delete() }
		Timber.d("Font cache cleared")
	}

	/**
	 * Get the current cache size in bytes.
	 */
	fun getCacheSize(): Long {
		return fontCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
	}
}

/**
 * Information about a font attachment in a media file.
 */
data class FontAttachment(
	val index: Int,
	val fileName: String,
	val mimeType: String,
) {
	companion object {
		// MIME types for font files
		private val FONT_MIME_TYPES = setOf(
			"application/x-truetype-font",
			"application/x-font-ttf",
			"font/ttf",
			"font/otf",
			"application/vnd.ms-opentype",
			"application/font-sfnt",
			"font/sfnt",
		)

		/**
		 * Check if a MIME type represents a font file.
		 */
		fun isFontMimeType(mimeType: String?): Boolean {
			return mimeType?.lowercase() in FONT_MIME_TYPES
		}
	}
}
