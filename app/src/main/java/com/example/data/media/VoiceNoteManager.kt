package com.example.data.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

data class RecordingState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val amplitudes: List<Float> = emptyList()
)

data class PlaybackState(
    val activeMessageId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class VoiceNoteManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentRecordFile: File? = null
    private var recordStartTimeMs: Long = 0L
    private var meteringJob: Job? = null

    private var player: MediaPlayer? = null
    private var playbackJob: Job? = null

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val audioDir: File by lazy {
        File(context.filesDir, "circlechat_audio").apply { mkdirs() }
    }

    fun startRecording(scope: CoroutineScope): Result<Unit> {
        return try {
            stopRecording(save = false)
            val file = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(22_050)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            currentRecordFile = file
            recordStartTimeMs = System.currentTimeMillis()
            _recordingState.value = RecordingState(isRecording = true, elapsedMs = 0L, amplitudes = emptyList())

            meteringJob?.cancel()
            meteringJob = scope.launch(Dispatchers.Main) {
                while (isActive && _recordingState.value.isRecording) {
                    val amp = try {
                        ((recorder?.maxAmplitude ?: 0) / 32767f).coerceIn(0.08f, 1f)
                    } catch (_: Exception) {
                        0.1f
                    }
                    val elapsed = System.currentTimeMillis() - recordStartTimeMs
                    val nextAmps = (_recordingState.value.amplitudes + amp).takeLast(32)
                    _recordingState.value = RecordingState(
                        isRecording = true,
                        elapsedMs = elapsed,
                        amplitudes = nextAmps
                    )
                    delay(85L)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            stopRecording(save = false)
            Result.failure(e)
        }
    }

    data class RecordedVoiceNote(
        val file: File,
        val durationMs: Long,
        val base64Audio: String
    )

    fun stopRecording(save: Boolean): RecordedVoiceNote? {
        meteringJob?.cancel()
        meteringJob = null
        val duration = (System.currentTimeMillis() - recordStartTimeMs).coerceAtLeast(500L)
        val file = currentRecordFile
        try {
            recorder?.stop()
        } catch (_: Exception) {
        } finally {
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
        }
        _recordingState.value = RecordingState()
        currentRecordFile = null

        if (!save || file == null || !file.exists()) {
            file?.delete()
            return null
        }
        val b64 = try {
            val bytes = file.readBytes()
            if (bytes.size <= 160_000) Base64.encodeToString(bytes, Base64.NO_WRAP) else ""
        } catch (_: Exception) {
            ""
        }
        return RecordedVoiceNote(file = file, durationMs = duration, base64Audio = b64)
    }

    fun togglePlayAudio(
        messageId: String,
        localPath: String,
        base64Audio: String,
        scope: CoroutineScope
    ) {
        val current = _playbackState.value
        if (current.activeMessageId == messageId && player != null) {
            val p = player!!
            if (p.isPlaying) {
                p.pause()
                _playbackState.value = current.copy(isPlaying = false)
            } else {
                p.start()
                _playbackState.value = current.copy(isPlaying = true)
            }
            return
        }

        stopPlayback()
        val playableFile = resolvePlayableFile(messageId, localPath, base64Audio) ?: return
        try {
            val mp = MediaPlayer().apply {
                setDataSource(playableFile.absolutePath)
                prepare()
                start()
            }
            player = mp
            _playbackState.value = PlaybackState(
                activeMessageId = messageId,
                isPlaying = true,
                positionMs = 0L,
                durationMs = mp.duration.toLong().coerceAtLeast(1L)
            )
            mp.setOnCompletionListener {
                stopPlayback()
            }
            playbackJob = scope.launch(Dispatchers.Main) {
                while (isActive && player != null) {
                    val p = player ?: break
                    if (p.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            positionMs = p.currentPosition.toLong(),
                            durationMs = p.duration.toLong().coerceAtLeast(1L),
                            isPlaying = true
                        )
                    }
                    delay(120L)
                }
            }
        } catch (_: Exception) {
            stopPlayback()
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            player?.seekTo(positionMs.toInt())
            _playbackState.value = _playbackState.value.copy(positionMs = positionMs)
        } catch (_: Exception) {}
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            player?.stop()
        } catch (_: Exception) {}
        try {
            player?.release()
        } catch (_: Exception) {}
        player = null
        _playbackState.value = PlaybackState()
    }

    private fun resolvePlayableFile(messageId: String, localPath: String, base64Audio: String): File? {
        if (localPath.isNotBlank()) {
            val f = File(localPath)
            if (f.exists() && f.length() > 0) return f
        }
        if (base64Audio.isNotBlank()) {
            val cached = File(audioDir, "cache_${messageId.take(24)}.m4a")
            if (!cached.exists()) {
                val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
                FileOutputStream(cached).use { it.write(bytes) }
            }
            return cached
        }
        return null
    }
}
