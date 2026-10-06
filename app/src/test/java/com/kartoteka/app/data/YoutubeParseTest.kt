package com.kartoteka.app.data

import com.kartoteka.app.assistant.NoaMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Разбор настоящих страниц YouTube (сохранённые выжимки): канал и его видео. */
class YoutubeParseTest {
    private fun res(name: String) = javaClass.getResource("/yt/$name")!!.readText()

    @Test fun findsChannelFromSearchPage() {
        val ch = NoaMedia.parseChannel(res("channel_search.txt"))!!
        assertTrue(ch.id.startsWith("UC") && ch.id.length == 24)
    }

    @Test fun listsChannelVideosWithTitles() {
        val v = NoaMedia.parseChannelVideos(res("channel_videos.txt"))
        assertTrue("видео: ${v.size}", v.size >= 10)
        assertTrue(v.all { it.videoId!!.length == 11 })
        assertEquals(true, v.first().title?.isNotBlank())
    }
}
