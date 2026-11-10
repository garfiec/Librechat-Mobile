package com.garfiec.librechat.core.ui.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.darwin.NSObject

/** Plays encoded audio clips (mp3, aac, wav) one at a time, such as a server's text-to-speech reply. */
@OptIn(ExperimentalForeignApi::class)
class AudioClipPlayer {
    private var player: AVAudioPlayer? = null
    private var done: CompletableDeferred<Boolean>? = null

    // AVAudioPlayer holds its delegate weakly, so this instance keeps it alive.
    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            done?.complete(successfully)
        }

        override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
            done?.complete(false)
        }
    }

    /** Suspends until [audio] has played or [stop] ends it; false when iOS can't play these bytes. */
    suspend fun play(audio: ByteArray): Boolean {
        stop()
        // Dictation leaves the session in play-and-record (earpiece); spoken replies go to the speaker.
        // Reconfigured only when something switched it away, not before every clip of a long reply.
        val session = AVAudioSession.sharedInstance()
        if (session.category != AVAudioSessionCategoryPlayback) {
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        }
        // Typed non-null, but the initializer throws for audio iOS can't decode.
        val clip = runCatching { AVAudioPlayer(data = audio.toNSData(), error = null) }.getOrNull() ?: return false
        val finished = CompletableDeferred<Boolean>()
        clip.delegate = delegate
        player = clip
        done = finished
        if (!clip.play()) {
            stop()
            return false
        }
        return try {
            finished.await()
        } finally {
            clip.stop()
            if (player === clip) {
                player = null
                done = null
            }
        }
    }

    fun stop() {
        player?.stop()
        player = null
        done?.complete(true)
        done = null
    }
}
