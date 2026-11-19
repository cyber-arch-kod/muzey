package com.example.data.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.example.config.PricingConfig
import com.example.data.remote.SecretsConfig
import com.example.domain.model.RoomStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Audio API (AudioManager + AudioAttributes + AudioFocusRequest + MediaPlayer)
 * orqali 3D viewer fon musiqasini dinamik yuklaydigan va yoqish/o'chirishni boshqaradigan sinf.
 */
class MuseumAudioManager(
    context: Context,
    private val onStateChanged: (isLoading: Boolean, isPlaying: Boolean) -> Unit,
    private val onUnavailable: () -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var isPrepared = false
    private var isLoading = false
    private var isPlaying = false
    private var loadedSourceKey: String? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(0.65f, 0.65f)
            }
        }
    }

    fun toggle(roomStyle: RoomStyle, customAudioUrl: String? = null) {
        if (isLoading) return
        if (isPlaying) {
            pause()
        } else {
            play(roomStyle, customAudioUrl)
        }
    }

    fun play(roomStyle: RoomStyle, customAudioUrl: String? = null) {
        if (isLoading || isPlaying) return

        val sourceKey = "${roomStyle.key}:${customAudioUrl.orEmpty()}"
        if (isPrepared && mediaPlayer != null && loadedSourceKey == sourceKey) {
            if (requestFocus()) {
                mediaPlayer?.setVolume(0.65f, 0.65f)
                mediaPlayer?.start()
                isPlaying = true
                onStateChanged(false, true)
            }
            return
        }

        isLoading = true
        onStateChanged(true, false)

        scope.launch {
            val preparedPlayer = withContext(Dispatchers.IO) {
                prepareDynamicPlayer(roomStyle, customAudioUrl)
            }

            isLoading = false
            if (preparedPlayer == null) {
                isPlaying = false
                isPrepared = false
                onStateChanged(false, false)
                onUnavailable()
                return@launch
            }

            releasePlayerOnly()
            mediaPlayer = preparedPlayer
            loadedSourceKey = sourceKey
            isPrepared = true

            if (requestFocus()) {
                preparedPlayer.setVolume(0.65f, 0.65f)
                preparedPlayer.start()
                isPlaying = true
                onStateChanged(false, true)
            } else {
                isPlaying = false
                onStateChanged(false, false)
            }
        }
    }

    fun pause() {
        val mp = mediaPlayer
        if (mp != null && isPrepared) {
            runCatching {
                if (mp.isPlaying) mp.pause()
            }
        }
        isPlaying = false
        abandonFocus()
        onStateChanged(false, false)
    }

    fun release() {
        scope.cancel()
        abandonFocus()
        releasePlayerOnly()
        isPlaying = false
        isLoading = false
    }

    private fun releasePlayerOnly() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
        isPrepared = false
    }

    private fun prepareDynamicPlayer(roomStyle: RoomStyle, customAudioUrl: String?): MediaPlayer? {
        // 1. Avval mahalliy assets/audio/ ichidagi fayllarni tekshiramiz
        for (assetPath in PricingConfig.musicAssetCandidates(roomStyle)) {
            val mp = tryPrepareFromAsset(assetPath)
            if (mp != null) return mp
        }

        // 2. Maxsus berilgan yoki Supabase 'museum-audio' bucket manzillaridan dinamik yuklaymiz
        val remoteCandidates = buildList {
            if (!customAudioUrl.isNullOrBlank()) add(customAudioUrl.trim())
            if (SecretsConfig.isSupabaseConfigured) {
                val base = "${SecretsConfig.supabaseUrl}/storage/v1/object/public/museum-audio"
                val styleFile = when (roomStyle) {
                    RoomStyle.ROMANTIC -> "romantic.mp3"
                    RoomStyle.WEDDING -> "wedding.mp3"
                    else -> "museum_bg.mp3"
                }
                add("$base/$styleFile")
                if (styleFile != "museum_bg.mp3") {
                    add("$base/museum_bg.mp3")
                }
            }
        }

        for (url in remoteCandidates) {
            val mp = tryPrepareFromUrl(url)
            if (mp != null) return mp
        }
        return null
    }

    private fun tryPrepareFromAsset(assetPath: String): MediaPlayer? {
        var player: MediaPlayer? = null
        return runCatching {
            val afd = appContext.assets.openFd(assetPath)
            player = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                prepare()
            }
            player
        }.onFailure {
            player?.runCatching { release() }
        }.getOrNull()
    }

    private fun tryPrepareFromUrl(url: String): MediaPlayer? {
        var player: MediaPlayer? = null
        return runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(url)
                isLooping = true
                prepare()
            }
            player
        }.onFailure {
            player?.runCatching { release() }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }
}
