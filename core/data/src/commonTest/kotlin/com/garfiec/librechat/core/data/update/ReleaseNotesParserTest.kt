package com.garfiec.librechat.core.data.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseNotesParserTest {

    // Shape of the published v2026.10.0 body, trimmed: badges, Highlights, What's Changed, footer.
    private val withHighlights = """
        ![Target Backend](https://img.shields.io/badge/Target_Backend-LibreChat_v0.8.8-orange) ![APK Downloads](https://img.shields.io/github/downloads/garfiec/Librechat-Mobile/v2026.10.0/switchboard-v2026.10.0.apk?label=APK)

        # Highlights

        More fixes for iOS, starting with a keyboard that can finally be dismissed.

        * Features
          * Tool approvals appear one call at a time in a panel docked above the composer
        * Bug Fixes
          * Tapping outside a text field dismisses the keyboard

        ## What's Changed
        * fix(ui): dismiss the keyboard on a tap outside the text field by @garfiec in https://github.com/garfiec/Librechat-Mobile/pull/397


        **Full Changelog**: https://github.com/garfiec/Librechat-Mobile/compare/v2026.09.0...v2026.10.0


        ---
        [Build provenance attestation](https://github.com/garfiec/Librechat-Mobile/attestations/51702784) · [CI workflow run](https://github.com/garfiec/Librechat-Mobile/actions/runs/36816753710)
    """.trimIndent().replace("\n", "\r\n")

    // Shape of v2026.08.4: no hand-written Highlights.
    private val generatedOnly = """
        ![Target Backend](https://img.shields.io/badge/Target_Backend-LibreChat_v0.8.8-rc1-orange)

        ## What's Changed
        * fix(chat): stop the streaming follow scroll by @garfiec in https://github.com/garfiec/Librechat-Mobile/pull/367

        **Full Changelog**: https://github.com/garfiec/Librechat-Mobile/compare/v2026.08.3...v2026.08.4

        ---
        [Build provenance attestation](https://github.com/garfiec/Librechat-Mobile/attestations/42732800)
    """.trimIndent()

    @Test
    fun splitsHighlightsFromGeneratedChangelog() {
        val notes = ReleaseNotesParser.parse(withHighlights)
        val highlights = assertNotNull(notes.highlights)
        assertTrue(highlights.startsWith("More fixes for iOS"))
        assertTrue("Tool approvals appear" in highlights)
        assertFalse("What's Changed" in highlights)

        val changelog = assertNotNull(notes.fullChangelog)
        assertEquals(
            "* fix(ui): dismiss the keyboard on a tap outside the text field by @garfiec in " +
                "[#397](https://github.com/garfiec/Librechat-Mobile/pull/397)",
            changelog.lines().first(),
        )
        assertTrue("**Full Changelog**" in changelog)
    }

    @Test
    fun dropsBadgesAndProvenanceFooter() {
        val notes = ReleaseNotesParser.parse(withHighlights)
        val all = notes.highlights + notes.fullChangelog
        assertFalse("img.shields.io" in all)
        assertFalse("attestation" in all)
        assertFalse("---" in all)
        assertFalse('\r' in all)
    }

    @Test
    fun generatedOnlyBodyHasNoHighlights() {
        val notes = ReleaseNotesParser.parse(generatedOnly)
        assertNull(notes.highlights)
        assertTrue(assertNotNull(notes.fullChangelog).startsWith("* fix(chat): stop the streaming"))
    }

    @Test
    fun bodyWithoutKnownHeadingsIsAllHighlights() {
        val notes = ReleaseNotesParser.parse("Small fix release.\n\n* One thing")
        assertEquals("Small fix release.\n\n* One thing", notes.highlights)
        assertNull(notes.fullChangelog)
    }

    @Test
    fun ruleInsideNotesIsNotTreatedAsFooter() {
        val body = "# Highlights\n\nPart one\n\n---\n\n## Part two\n\n* a\n* b\n* c\n* d"
        val notes = ReleaseNotesParser.parse(body)
        assertTrue("Part two" in assertNotNull(notes.highlights))
    }

    @Test
    fun linkedPullUrlIsLeftAlone() {
        val linked = "* see [the PR](https://github.com/o/r/pull/5)"
        assertEquals(linked, ReleaseNotesParser.parse(linked).highlights)
    }

    @Test
    fun emptyBody() {
        assertEquals(ReleaseNotesParser.Notes(null, null), ReleaseNotesParser.parse(null))
        assertEquals(ReleaseNotesParser.Notes(null, null), ReleaseNotesParser.parse("  \n"))
    }
}
