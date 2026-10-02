package com.garfiec.librechat.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {

    @Test
    fun parsesReleaseTag() {
        assertEquals(AppVersion(2026, 10, 1), AppVersion.parse("v2026.10.1"))
        assertEquals(AppVersion(2026, 10, 1), AppVersion.parse("2026.10.1"))
    }

    @Test
    fun parsesReleaseCandidate() {
        assertEquals(AppVersion(2026, 10, 1, rc = 2), AppVersion.parse("v2026.10.1-rc2"))
        assertEquals(AppVersion(2026, 10, 1, rc = 2), AppVersion.parse("2026.10.1-rc.2"))
    }

    @Test
    fun ignoresDebugSuffix() {
        assertEquals(AppVersion(2026, 10, 0), AppVersion.parse("2026.10.0-debug"))
        assertEquals(AppVersion(2026, 10, 0, rc = 1), AppVersion.parse("2026.10.0-rc1-debug"))
    }

    @Test
    fun toStringMatchesTagSpelling() {
        assertEquals("2026.09.0", AppVersion.parse("v2026.09.0").toString())
        assertEquals("2026.10.1-rc2", AppVersion.parse("v2026.10.1-rc2").toString())
    }

    @Test
    fun rejectsGarbage() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("2026.10"))
        assertNull(AppVersion.parse("2026.10.0-beta1"))
        assertNull(AppVersion.parse("2026.10.0+dev"))
    }

    @Test
    fun ordersByCalverTriple() {
        assertTrue(AppVersion.parse("2026.10.0")!! > AppVersion.parse("2026.09.9")!!)
        assertTrue(AppVersion.parse("2027.01.0")!! > AppVersion.parse("2026.12.4")!!)
        assertTrue(AppVersion.parse("2026.10.2")!! > AppVersion.parse("2026.10.1")!!)
    }

    @Test
    fun releaseCandidateSortsBelowItsRelease() {
        assertTrue(AppVersion.parse("2026.10.1-rc1")!! < AppVersion.parse("2026.10.1")!!)
        assertTrue(AppVersion.parse("2026.10.1-rc1")!! < AppVersion.parse("2026.10.1-rc2")!!)
        assertTrue(AppVersion.parse("2026.10.1-rc1")!! > AppVersion.parse("2026.10.0")!!)
    }
}
