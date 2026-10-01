package com.kartoteka.app.voice

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kartoteka.app.KartotekaApp
import com.kartoteka.app.data.VoiceNote
import com.kartoteka.app.i18n.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Расшифровка в фоне и прослушивание голосовых заметок. */
object VoiceNotes {
    /** Статус расшифровки по id заметки («Расшифровываю…», причина ошибки). */
    val status = mutableStateMapOf<Long, String>()

    var playingId by mutableStateOf<Long?>(null)
        private set
    private var player: MediaPlayer? = null

    fun transcribe(app: KartotekaApp, note: VoiceNote) {
        status[note.id] = t("Расшифровываю…")
        app.appScope.launch {
            val result = runCatching {
                val pcm = withContext(Dispatchers.IO) { app.repository.voices.pcm(note.file) }
                VoiceTranscriber.transcribe(app, pcm)
            }.getOrElse { VoiceTranscriber.Result.Unavailable(t("Не удалось прочитать запись")) }
            when (result) {
                is VoiceTranscriber.Result.Text ->
                    if (result.text.isBlank()) status[note.id] = t("Речь не распознана")
                    else { app.repository.setVoiceText(note.id, result.text); status.remove(note.id) }
                is VoiceTranscriber.Result.Unavailable -> status[note.id] = result.reason
            }
        }
    }

    fun toggle(app: KartotekaApp, note: VoiceNote) {
        if (playingId == note.id) return stop(app)
        stop(app)
        app.appScope.launch {
            val file = runCatching { withContext(Dispatchers.IO) { app.repository.voices.playbackFile(note.file) } }.getOrNull()
                ?: return@launch
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop(app) }
                prepare()
                start()
            }
            playingId = note.id
        }
    }

    fun stop(app: KartotekaApp) {
        player?.runCatching { stop(); release() }
        player = null
        playingId = null
        app.repository.voices.clearPlayback()
    }
}
