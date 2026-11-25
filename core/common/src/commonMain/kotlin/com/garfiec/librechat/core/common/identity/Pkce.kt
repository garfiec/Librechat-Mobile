package com.garfiec.librechat.core.common.identity

import okio.ByteString.Companion.encodeUtf8
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** RFC 7636 proof key for one authorization attempt. [toString] keeps the verifier out of logs. */
class Pkce private constructor(val verifier: String) {
    /** The S256 challenge: base64url(SHA-256(verifier)), unpadded. */
    val challenge: String = verifier.encodeUtf8().sha256().base64Url().trimEnd('=')

    override fun toString(): String = "Pkce(<redacted>)"

    companion object {
        /** 96 characters from three random UUIDs: `Uuid.random()` draws from a cryptographically secure source. */
        @OptIn(ExperimentalUuidApi::class)
        fun create(): Pkce = Pkce(List(3) { Uuid.random().toHexString() }.joinToString(""))

        /** For tests only: the RFC 7636 appendix B vector. */
        internal fun of(verifier: String): Pkce = Pkce(verifier)

        /** An unguessable `state` value. */
        @OptIn(ExperimentalUuidApi::class)
        fun newState(): String = Uuid.random().toHexString()
    }
}
