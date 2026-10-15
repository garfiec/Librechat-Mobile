package com.garfiec.librechat.core.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Runs on every platform's regex engine, which is the point: they differ. */
class RegexGuardTest {

    @Test
    fun ordinaryPatternsCompile() {
        listOf("flow\\.debounce", "\\bkotlin\\b", "error [0-9]{3}", "colou?r", "(foo|bar) baz", "(ab)+", "^\\s*import").forEach {
            assertIs<RegexCheck.Ok>(RegexGuard.compile(it, matchCase = false), it)
        }
    }

    @Test
    fun nestedQuantifiersAreTooComplex() {
        listOf("(a+)+", "(\\w*)*", "(x+){2,}", "((ab)*c)+", "(a|aa)+", "(.*a){3}", "(?:a+)+", "(?<n>\\d+)*").forEach {
            assertTrue(RegexGuard.isTooComplex(it), it)
        }
    }

    @Test
    fun groupKindMarkersAreNotQuantifiers() {
        listOf("(?:ab)+", "(?i)kotlin", "(?=foo)bar", "(?<word>ab)+").forEach {
            assertFalse(RegexGuard.isTooComplex(it), it)
        }
    }

    @Test
    fun twoWildcardRunsOnOneBranchAreTooComplex() {
        assertTrue(RegexGuard.isTooComplex(".*foo.*bar"))
        assertFalse(RegexGuard.isTooComplex(".*foo|.*bar"))
    }

    @Test
    fun escapedAndClassCharactersAreNotSyntax() {
        assertFalse(RegexGuard.isTooComplex("\\(a+\\)+"))
        assertFalse(RegexGuard.isTooComplex("[(+)*]+"))
    }

    @Test
    fun invalidPatternsAreReportedNotThrown() {
        assertEquals(RegexCheck.Rejected(RegexProblem.Invalid), RegexGuard.compile("(unclosed", matchCase = false))
        assertEquals(RegexCheck.Rejected(RegexProblem.TooComplex), RegexGuard.compile("(a+)+", matchCase = false))
    }

    @Test
    fun matchCaseControlsCaseFolding() {
        val folded = assertIs<RegexCheck.Ok>(RegexGuard.compile("kotlin", matchCase = false)).regex
        val exact = assertIs<RegexCheck.Ok>(RegexGuard.compile("kotlin", matchCase = true)).regex
        assertTrue(folded.containsMatchIn("Kotlin"))
        assertFalse(exact.containsMatchIn("Kotlin"))
    }
}
