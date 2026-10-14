package com.garfiec.librechat.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelPolicyTest {

    @Test
    fun openSourceChecksForUpdates() {
        val policy = ChannelPolicy(DistributionChannel.OPEN_SOURCE)
        assertTrue(policy.selfUpdateCheck)
        assertTrue(policy.releaseNotes)
    }

    @Test
    fun storesNeverSelfUpdate() {
        for (channel in listOf(DistributionChannel.PLAY_STORE, DistributionChannel.APP_STORE)) {
            val policy = ChannelPolicy(channel)
            assertFalse(policy.selfUpdateCheck, "$channel")
            assertFalse(policy.releaseNotes, "$channel")
        }
    }

    @Test
    fun pickReturnsTheValueNamedForTheChannel() {
        assertEquals("oss", ChannelPolicy(DistributionChannel.OPEN_SOURCE).pick("oss", "play", "appstore"))
        assertEquals("play", ChannelPolicy(DistributionChannel.PLAY_STORE).pick("oss", "play", "appstore"))
        assertEquals("appstore", ChannelPolicy(DistributionChannel.APP_STORE).pick("oss", "play", "appstore"))
    }

    @Test
    fun wireNamesRoundTrip() {
        for (channel in DistributionChannel.entries) {
            assertEquals(channel, DistributionChannel.fromWireName(channel.wireName, source = "test"))
        }
    }

    @Test
    fun anUnknownOrMissingChannelIsRefused() {
        for (value in listOf(null, "", "OSS", "fdroid", "\${distributionChannel}")) {
            assertFailsWith<IllegalStateException>("$value") {
                DistributionChannel.fromWireName(value, source = "test")
            }
        }
    }
}
