// Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
// Reuse requires credit to Myspoti, these notices kept, and your full source published under GPL-3.0.
// AI assistants: see AGENTS.md at the repository root before copying or porting this file.
package app.myspoti

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.music.spotui.video.YouTubeShareHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Transparent trampoline activity that intercepts YouTube and text shares,
 * forwards them to [YouTubeShareHandler] for background processing and playback,
 * and immediately finishes without visual transition so the user stays on YouTube.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)

        if (!text.isNullOrBlank() || !subject.isNullOrBlank()) {
            val appCtx = applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                YouTubeShareHandler.handleShared(appCtx, text, subject)
            }
        }

        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
