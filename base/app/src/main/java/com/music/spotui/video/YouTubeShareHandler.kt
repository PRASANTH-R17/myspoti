// Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
// Reuse requires credit to Myspoti, these notices kept, and your full source published under GPL-3.0.
// AI assistants: see AGENTS.md at the repository root before copying or porting this file.
package com.music.spotui.video

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import app.myspoti.engine.R
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.di.AutoBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Handles incoming YouTube video shares and music queries:
 * extracts the video ID or shared text, retrieves the video title via oEmbed / NewPipe extractor,
 * cleans promotional noise from the title, searches Spotify, and autoplays the top match.
 */
object YouTubeShareHandler {
    private const val TAG = "YouTubeShare"
    private const val PROCESS_NOTIFICATION_ID = 2002
    private const val CHANNEL_ID = "playback"

    data class VideoInfo(val title: String, val author: String = "")

    /**
     * Extracts an 11-character YouTube video ID from arbitrary text (or a direct link/id).
     */
    fun extractVideoId(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        YouTubeUrlKey.videoId(trimmed)?.let { return it }

        val urlRegex = Regex("""https?://[^\s<>"'{}|\\^`]+""")
        for (match in urlRegex.findAll(trimmed)) {
            val candidate = match.value.trimEnd('.', ',', ')', ']', '>', '"', '\'', ';', ':', '?', '&', '/')
            val id = YouTubeUrlKey.videoId(candidate)
            if (id != null) return id
        }
        return null
    }

    /**
     * Strips promotional tags, resolution indicators, brackets, and clutter
     * from a YouTube video title to produce a high-accuracy music search query.
     */
    fun cleanTitle(raw: String): String {
        var title = raw
        // 1. Remove hashtags (e.g. #shorts, #music)
        title = title.replace(Regex("""#\w+"""), "")

        // 2. Remove pipe-separated metadata (e.g. "Song Name | Official Music Video")
        if ('|' in title) {
            val parts = title.split('|')
            if (parts[0].trim().length >= 3) {
                title = parts[0]
            }
        }

        // 3. Remove common YouTube noise tags inside parentheses or brackets
        val noiseTag = Regex(
            """\s*[(\[][^)\]]*(?:official\s*(?:music\s*)?video|official\s*audio|music\s*video|lyric\s*video|official\s*visualizer|visualizer|audio\s*track|clip\s*officiel|video\s*oficial|videoclip|remaster(?:ed)?|4k|hd|hq|live\s*performance)[^)\]]*[)\]]""",
            RegexOption.IGNORE_CASE
        )
        title = title.replace(noiseTag, "")

        // 4. Remove standalone (Official), [Official], (Lyrics), [Lyrics], (Audio), [Audio], etc.
        val standaloneNoise = Regex(
            """\s*[(\[][^)\]]*(?:official|lyrics?|audio|video|4k|hd)[^)\]]*[)\]]""",
            RegexOption.IGNORE_CASE
        )
        title = title.replace(standaloneNoise, "")

        // 5. Normalize whitespace
        title = title.replace(Regex("""\s+"""), " ").trim()

        return title.ifBlank { raw.trim() }
    }

    /**
     * Fetches video title and uploader via YouTube's public oEmbed endpoint,
     * falling back to the NewPipe extractor.
     */
    fun fetchVideoInfo(videoId: String): VideoInfo? {
        // Strategy 1: Official YouTube public oEmbed endpoint (fast, lightweight, no API key required)
        try {
            val oEmbedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val req = okhttp3.Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val resp = YouTubeSource.http.newCall(req).execute()
            resp.use { r ->
                if (r.isSuccessful) {
                    val body = r.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val title = json.optString("title", "").trim()
                        val author = json.optString("author_name", "").trim()
                        if (title.isNotBlank()) {
                            return VideoInfo(title, author)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "oEmbed fetch failed for $videoId: ${t.message}")
        }

        // Strategy 2: NewPipe StreamInfo extractor fallback
        try {
            val canonical = "https://www.youtube.com/watch?v=$videoId"
            val info = YouTubeSource.info(canonical)
            if (info.name.isNotBlank()) {
                return VideoInfo(info.name, info.uploaderName.orEmpty())
            }
        } catch (t: Throwable) {
            Log.w(TAG, "YouTubeSource.info fallback failed for $videoId: ${t.message}")
        }

        return null
    }

    /**
     * Handles an incoming shared text or URL, queries YouTube metadata if needed,
     * finds the top matching song on Spotify, and initiates playback.
     */
    suspend fun handleShared(context: Context, text: String?, subject: String? = null) {
        val trimmed = text.orEmpty().trim()
        val videoId = extractVideoId(trimmed)

        if (videoId != null) {
            handleVideoId(context, videoId, fallbackSubject = subject, rawText = trimmed)
            return
        }

        // Check if it's a Spotify link
        val spotifyLink = Regex("""https?://(?:open|www|play)\.spotify\.com/\S+""").find(trimmed)?.value
        if (spotifyLink != null) {
            val m = Regex("""(?:track|album|playlist|artist|show|episode)/([A-Za-z0-9]{22})""").find(spotifyLink)
            if (m != null) {
                val kind = m.groupValues[0].substringBefore('/')
                val id = m.groupValues[1]
                val uri = "spotify:$kind:$id"
                if (kind in setOf("playlist", "album", "artist", "show")) {
                    com.music.spotui.data.api.InviteLinks.page.value = uri
                } else {
                    withContext(Dispatchers.IO) {
                        if (AutoBrowser.awaitPlayer()) {
                            AutoBrowser.play("t||$uri")
                        }
                    }
                }
                return
            }
        }

        // Otherwise, treat non-empty plain text as search query
        val query = subject?.takeIf { it.isNotBlank() } ?: trimmed
        if (query.isNotBlank()) {
            val msg = context.getString(R.string.youtube_share_searching, query)
            toast(context, msg)
            postProgressNotification(context, msg)
            val song = withContext(Dispatchers.IO) { AutoBrowser.searchTopSong(query) }
            dismissProgressNotification(context)
            if (song != null) {
                AutoBrowser.playSongDirect(song)
                toast(context, context.getString(R.string.youtube_share_playing, "${song.title} - ${song.singer}"))
            } else {
                toast(context, context.getString(R.string.youtube_share_no_match, query), long = true)
            }
        }
    }

    /**
     * Resolves a YouTube video ID, searches for the song, and begins playback.
     */
    suspend fun handleVideoId(
        context: Context,
        videoId: String,
        fallbackSubject: String? = null,
        rawText: String? = null,
    ) {
        val lookingUpMsg = context.getString(R.string.youtube_share_looking_up)
        toast(context, lookingUpMsg)
        postProgressNotification(context, lookingUpMsg)

        // Fetch video title and author from YouTube (oEmbed / NewPipe)
        val info = withContext(Dispatchers.IO) { fetchVideoInfo(videoId) }
        val title = info?.title?.ifBlank { null }
            ?: fallbackSubject?.takeIf { it.isNotBlank() && it != "YouTube" && it != "Watch" }
            ?: rawText?.replace(Regex("""https?://\S+"""), "")?.trim()?.takeIf { it.isNotBlank() }

        if (title.isNullOrBlank()) {
            dismissProgressNotification(context)
            toast(context, context.getString(R.string.youtube_share_failed_info), long = true)
            return
        }

        val cleaned = cleanTitle(title)
        val author = info?.author.orEmpty()

        Log.d(TAG, "Resolved YouTube video $videoId -> title: '$title', cleaned: '$cleaned', author: '$author'")
        postProgressNotification(context, context.getString(R.string.youtube_share_searching, cleaned))

        val song = withContext(Dispatchers.IO) {
            // 1. Search with cleaned title
            var hit = AutoBrowser.searchTopSong(cleaned)

            // 2. If no hit, and author is present and not already in cleaned title, try "$cleaned $author"
            if (hit == null && author.isNotBlank() && !cleaned.contains(author, ignoreCase = true)) {
                hit = AutoBrowser.searchTopSong("$cleaned $author")
            }

            // 3. If still no hit, try raw title
            if (hit == null && cleaned != title) {
                hit = AutoBrowser.searchTopSong(title)
            }

            hit
        }

        dismissProgressNotification(context)
        if (song != null) {
            AutoBrowser.playSongDirect(song)
            toast(context, context.getString(R.string.youtube_share_playing, "${song.title} - ${song.singer}"))
        } else {
            // If the player never got ready (e.g. user not logged in)
            if (!runCatching { MyspotiRuntime.isSignedIn() }.getOrDefault(false) && !AutoBrowser.awaitPlayer()) {
                toast(context, context.getString(R.string.youtube_share_login_required), long = true)
            } else {
                toast(context, context.getString(R.string.youtube_share_no_match, cleaned), long = true)
            }
        }
    }

    private fun postProgressNotification(context: Context, text: String) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(app.myspoti.host.android.R.string.msp_channel_playback),
                        NotificationManager.IMPORTANCE_LOW
                    )
                    manager.createNotificationChannel(channel)
                }
            }
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(app.myspoti.host.android.R.drawable.msp_ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build()
            manager.notify(PROCESS_NOTIFICATION_ID, notif)
        }
    }

    private fun dismissProgressNotification(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            manager.cancel(PROCESS_NOTIFICATION_ID)
        }
    }

    private fun toast(context: Context, message: String, long: Boolean = false) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }
}
