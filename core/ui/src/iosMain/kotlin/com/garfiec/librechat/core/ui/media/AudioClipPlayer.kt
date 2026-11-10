package com.garfiec.librechat.core.ui.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.Foundation.NSError
import platform.darwin.NSObject

/** Plays encoded audio clips (mp3, aac, wav) one at a time, such as a server's text-to-speech reply. */
@OptIn(ExperimentalForeignApi::class)
class AudioClipPlayer {
    private var player: AVAudioPlayer? = null
    private var done: CompletableDeferred<Unit>? = null

    // AVAudioPlayer holds its delegate weakly, so this instance keeps it alive.
    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            done?.complete(Unit)
        }

        override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
            done?.complete(Unit)
        }
    }

    /** Suspends until [audio] has played or [stop] ends it; false when iOS can't play these bytes. */
    suspend fun play(audio: ByteArray): Boolean {
        stop()
        val clip = AVAudioPlayer(data = audio.toNSData(), error = null) ?: return false
        val finished = CompletableDeferred<Unit>()
        clip.delegate = delegate
        player = clip
        done = finished
        if (!clip.play()) {
            stop()
            return false
        }
        try {
            finished.await()
        } finally {
            clip.stop()
            if (player === clip) {
                player = null
                done = null
            }
        }
        return true
    }

    fun stop() {
        player?.stop()
        player = null
        done?.complete(Unit)
        done = null
    }
}
