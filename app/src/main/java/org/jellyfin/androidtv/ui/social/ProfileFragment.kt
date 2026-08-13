package org.jellyfin.androidtv.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbar
import org.jellyfin.androidtv.ui.shared.toolbar.MainToolbarActiveButton
import java.util.UUID

/**
 * Hosts [ProfileScreen]. Without [EXTRA_USER_ID] it shows the signed-in user's own profile.
 */
class ProfileFragment : Fragment() {
	companion object {
		const val EXTRA_USER_ID = "user_id"
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		val userId = arguments?.getString(EXTRA_USER_ID)
			?.let { runCatching { UUID.fromString(it) }.getOrNull() }

		JellyfinTheme {
			Column(modifier = Modifier.fillMaxSize()) {
				MainToolbar(MainToolbarActiveButton.None)
				ProfileScreen(userId, Modifier.weight(1f))
			}
		}
	}
}
