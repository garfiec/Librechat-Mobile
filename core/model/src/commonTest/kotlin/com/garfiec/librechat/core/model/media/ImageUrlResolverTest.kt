package com.garfiec.librechat.core.model.media

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.FileReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageUrlResolverTest {

    private val cleanBase = "https://chat.example.com"

    /** A web login URL pasted as the server URL; joined raw, every image on it would 403. */
    private val webLoginBase = "https://chat.example.com/login?redirect_to=%2Fc%2Fnew"

    private fun fileRef(filepath: String? = null, fileId: String? = null) =
        FileReference(fileId = fileId, filepath = filepath)

    @Test
    fun imagesPathJoinsTheOriginNotTheStoredString() {
        assertEquals(
            "https://chat.example.com/images/u1/a.png",
            resolveFileReferenceUrl(fileRef(filepath = "/images/u1/a.png"), webLoginBase),
        )
        assertEquals(
            "https://chat.example.com/images/u1/a.png",
            resolveAttachmentUrl(Attachment(filepath = "/images/u1/a.png"), webLoginBase),
        )
    }

    @Test
    fun fileIdFallbackJoinsTheOrigin() {
        assertEquals(
            "https://chat.example.com/api/files/f1",
            resolveFileReferenceUrl(fileRef(fileId = "f1"), webLoginBase),
        )
    }

    @Test
    fun avatarJoinsTheOrigin() {
        assertEquals(
            "https://chat.example.com/images/u1/avatar.png",
            resolveAvatarUrl("/images/u1/avatar.png", webLoginBase),
        )
    }

    /**
     * Byte-for-byte what these built before: the strings are cache keys shared by every image
     * surface and the prefetcher, so a clean base must not change on upgrade.
     */
    @Test
    fun aCleanBaseBuildsTheSameUrlsAsBefore() {
        assertEquals(
            "https://chat.example.com/images/u1/a.png",
            resolveFileReferenceUrl(fileRef(filepath = "/images/u1/a.png"), cleanBase),
        )
        assertEquals(
            "https://chat.example.com/api/files/f1",
            resolveFileReferenceUrl(fileRef(fileId = "f1"), cleanBase),
        )
        assertEquals(
            "https://chat.example.com/api/files/rel.png",
            resolveFileReferenceUrl(fileRef(filepath = "rel.png"), cleanBase),
        )
        // A trailing slash on the stored base must not produce a double slash.
        assertEquals(
            "https://chat.example.com/images/u1/avatar.png",
            resolveAvatarUrl("/images/u1/avatar.png", "$cleanBase/"),
        )
    }

    @Test
    fun absoluteAndInlineReferencesAreReturnedUntouched() {
        assertEquals(
            "https://cdn.example.net/x.png",
            resolveFileReferenceUrl(fileRef(filepath = "https://cdn.example.net/x.png"), webLoginBase),
        )
        assertEquals(
            "data:image/png;base64,AAAA",
            resolveFileReferenceUrl(fileRef(filepath = "data:image/png;base64,AAAA"), webLoginBase),
        )
    }

    @Test
    fun nothingToResolveIsNull() {
        assertNull(resolveFileReferenceUrl(fileRef(), cleanBase))
        assertNull(resolveAvatarUrl("", cleanBase))
        assertNull(resolveAttachmentUrl(null, cleanBase))
    }
}
