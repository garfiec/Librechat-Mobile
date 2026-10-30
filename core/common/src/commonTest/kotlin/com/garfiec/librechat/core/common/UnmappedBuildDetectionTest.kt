package com.garfiec.librechat.core.common

import com.garfiec.librechat.core.common.generated.BackendCommitMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnmappedBuildDetectionTest {

    private val pinnedDate = assertNotNull(BackendCommitMap.dateForCommit(BackendCommitMap.PINNED_COMMIT))
    private val pinnedVersion = assertNotNull(BackendCommitMap.versionForCommit(BackendCommitMap.PINNED_COMMIT))

    @Test
    fun pinnedCommitIsAPlaceableEntryInTheMap() {
        // Shape only: literal values here would fail on every sync's regeneration.
        assertNotNull(BackendVersion.parse(pinnedVersion))
        assertTrue(Regex("""^\d{4}-\d{2}-\d{2}$""").matches(pinnedDate))
    }

    @Test
    fun rcTagBranchPlacesTheServerAtThatRc() {
        // buildInfo from a live v0.8.9-rc1 image (2026-10-30).
        val detected = assertNotNull(UnmappedBuildDetection.detect("v0.8.9-rc1", "2026-10-26T21:56:08Z"))
        assertEquals("0.8.9-rc1", detected.version)
        assertEquals(BackendBuildClass.RC, detected.classification)
        assertNull(detected.commitDate)
        assertTrue(BackendVersion.supportsFeature(detected, "0.8.7-rc1"))
        assertTrue(BackendVersion.supportsFeature(detected, "0.8.8"))
    }

    @Test
    fun releaseTagBranchIsOfficial() {
        val detected = assertNotNull(UnmappedBuildDetection.detect("v0.9.0", null))
        assertEquals("0.9.0", detected.version)
        assertEquals(BackendBuildClass.OFFICIAL, detected.classification)
    }

    @Test
    fun branchBuildAfterThePinIsPlacedAtThePin() {
        val detected = assertNotNull(UnmappedBuildDetection.detect("main", "2026-10-26T21:56:08Z"))
        assertEquals(pinnedVersion, detected.version)
        assertEquals(BackendBuildClass.DEV, detected.classification)
        assertNull(detected.commitDate)
        // Unknown past the pin, not ruled out: a later date gate must still be probe-able.
        assertEquals(FeatureSupport.UNKNOWN, BackendVersion.featureSupport(detected, "99.0.0", "2099-01-01"))
    }

    @Test
    fun branchBuildOnOrBeforeThePinIsNotPlaced() {
        assertNull(UnmappedBuildDetection.detect("main", "${pinnedDate}T23:59:59Z"))
        assertNull(UnmappedBuildDetection.detect("main", "2026-01-01T00:00:00Z"))
    }

    @Test
    fun nothingToGoOnIsNotPlaced() {
        assertNull(UnmappedBuildDetection.detect(null, null))
        assertNull(UnmappedBuildDetection.detect("main", null))
        assertNull(UnmappedBuildDetection.detect("main", "yesterday"))
        assertNull(UnmappedBuildDetection.detect("release/v0.8.9", null))
    }
}
