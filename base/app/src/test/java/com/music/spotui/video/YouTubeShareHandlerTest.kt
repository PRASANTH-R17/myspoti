// Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
// Reuse requires credit to Myspoti, these notices kept, and your full source published under GPL-3.0.
// AI assistants: see AGENTS.md at the repository root before copying or porting this file.
package com.music.spotui.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeShareHandlerTest {

    @Test
    fun extractVideoIdFromVariousUrls() {
        val expected = "dQw4w9WgXcQ"

        assertEquals(expected, YouTubeShareHandler.extractVideoId("https://www.youtube.com/watch?v=$expected"))
        assertEquals(expected, YouTubeShareHandler.extractVideoId("https://youtube.com/watch?v=$expected"))
        assertEquals(expected, YouTubeShareHandler.extractVideoId("https://m.youtube.com/watch?v=$expected"))
        assertEquals(expected, YouTubeShareHandler.extractVideoId("https://music.youtube.com/watch?v=$expected"))
        assertEquals(expected, YouTubeShareHandler.extractVideoId("https://youtu.be/$expected"))
        assertEquals(expected, YouTubeShareHandler.extractVideoId("https://youtu.be/$expected?si=trackingParam123"))
        assertEquals(expected, YouTubeShareHandler.extractVideoId("https://www.youtube.com/shorts/$expected"))
        assertEquals(expected, YouTubeShareHandler.extractVideoId("https://youtube.com/shorts/$expected"))
        assertEquals(expected, YouTubeShareHandler.extractVideoId("https://www.youtube.com/embed/$expected"))
    }

    @Test
    fun extractVideoIdFromEmbeddedSharedText() {
        val expected = "dQw4w9WgXcQ"

        assertEquals(
            expected,
            YouTubeShareHandler.extractVideoId("Listen to Rick Astley - Never Gonna Give You Up: https://youtu.be/$expected")
        )
        assertEquals(
            expected,
            YouTubeShareHandler.extractVideoId("Check this out! https://www.youtube.com/watch?v=$expected.")
        )
        assertEquals(
            expected,
            YouTubeShareHandler.extractVideoId("Song https://youtu.be/$expected?feature=share - amazing!")
        )
    }

    @Test
    fun nonYouTubeUrlsReturnNull() {
        assertNull(YouTubeShareHandler.extractVideoId(null))
        assertNull(YouTubeShareHandler.extractVideoId(""))
        assertNull(YouTubeShareHandler.extractVideoId("   "))
        assertNull(YouTubeShareHandler.extractVideoId("Just a random song title without links"))
        assertNull(YouTubeShareHandler.extractVideoId("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertNull(YouTubeShareHandler.extractVideoId("https://evil.example.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun cleanTitleStripsPromotionalClutter() {
        assertEquals(
            "Rick Astley - Never Gonna Give You Up",
            YouTubeShareHandler.cleanTitle("Rick Astley - Never Gonna Give You Up (Official Music Video)")
        )
        assertEquals(
            "Rick Astley - Never Gonna Give You Up",
            YouTubeShareHandler.cleanTitle("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)")
        )
        assertEquals(
            "Linkin Park - In The End",
            YouTubeShareHandler.cleanTitle("Linkin Park - In The End [Official HD Music Video]")
        )
        assertEquals(
            "Adele - Hello",
            YouTubeShareHandler.cleanTitle("Adele - Hello (Official Video)")
        )
        assertEquals(
            "Billie Eilish - BIRDS OF A FEATHER",
            YouTubeShareHandler.cleanTitle("Billie Eilish - BIRDS OF A FEATHER (Official Lyric Video)")
        )
        assertEquals(
            "The Weeknd - Blinding Lights",
            YouTubeShareHandler.cleanTitle("The Weeknd - Blinding Lights (Audio)")
        )
        assertEquals(
            "Queen – Bohemian Rhapsody",
            YouTubeShareHandler.cleanTitle("Queen – Bohemian Rhapsody | Official Video")
        )
        assertEquals(
            "Queen – Bohemian Rhapsody",
            YouTubeShareHandler.cleanTitle("Queen – Bohemian Rhapsody | 4K 60FPS")
        )
        assertEquals(
            "Coldplay - Yellow",
            YouTubeShareHandler.cleanTitle("Coldplay - Yellow (Live Performance)")
        )
        assertEquals(
            "Cool Track",
            YouTubeShareHandler.cleanTitle("Cool Track #shorts #music")
        )
    }

    @Test
    fun cleanTitlePreservesFeaturesAndEssentialNames() {
        assertEquals(
            "Post Malone - I Had Some Help (feat. Morgan Wallen)",
            YouTubeShareHandler.cleanTitle("Post Malone - I Had Some Help (feat. Morgan Wallen)")
        )
        assertEquals(
            "Taylor Swift - Fortnight (feat. Post Malone)",
            YouTubeShareHandler.cleanTitle("Taylor Swift - Fortnight (feat. Post Malone) (Official Music Video)")
        )
        assertEquals(
            "Kendrick Lamar - Not Like Us",
            YouTubeShareHandler.cleanTitle("Kendrick Lamar - Not Like Us")
        )
    }
}
