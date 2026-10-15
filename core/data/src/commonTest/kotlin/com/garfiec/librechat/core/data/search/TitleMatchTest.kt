package com.garfiec.librechat.core.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TitleMatchTest {

    private fun marked(title: String, query: String): List<String>? =
        TitleMatch.of(title, query)?.map { title.substring(it) }

    @Test
    fun everyTermMustMatchInAnyOrder() {
        assertEquals(listOf("Flow", "Kotlin"), marked("Flow in Kotlin", "kotlin flow"))
        assertNull(TitleMatch.of("Flow in Swift", "kotlin flow"))
    }

    @Test
    fun termsMatchAtWordStartsAndTheLastOneAsAPrefix() {
        assertEquals(listOf("Kotl"), marked("Kotlin tips", "kotl"))
        assertFalse(TitleMatch.matches("Ktor streaming", "tor"))
    }

    @Test
    fun onlyTheLastTermIsAPrefix() {
        assertFalse(TitleMatch.matches("Kotlin Flow", "kot flow"))
        assertTrue(TitleMatch.matches("Kotlin Flow", "kotlin flo"))
        // A finished last word (trailing space) is a whole word too.
        assertFalse(TitleMatch.matches("Kotlin Flow", "kotlin flo "))
    }

    @Test
    fun unsegmentedScriptsMatchAsASubstring() {
        assertTrue(TitleMatch.matches("東京の天気", "天気"))
    }

    @Test
    fun blankTitlesAndTooShortQueriesNeverMatch() {
        assertNull(TitleMatch.of(null, "kotlin"))
        assertNull(TitleMatch.of("Kotlin", "k"))
    }

    @Test
    fun aTwoLetterWordBeingTypedIsAPrefixInTitles() {
        assertEquals(listOf("Ki"), marked("Kitchen Island", "ki"))
        assertFalse(TitleMatch.matches("Kitchen Island", "ki "))
    }

    @Test
    fun matchCaseAndWholeWordApplyToTitles() {
        val caseSensitive = SearchOptions(matchCase = true)
        assertFalse(TitleMatch.matches("Cracking RSA keys", "rsa", caseSensitive))
        assertTrue(TitleMatch.matches("Cracking RSA keys", "RSA", caseSensitive))
        assertFalse(TitleMatch.matches("Kitchen lighting", "kit", SearchOptions(wholeWord = true)))
        assertTrue(TitleMatch.matches("Kit list", "kit", SearchOptions(wholeWord = true)))
    }

    @Test
    fun aRegexRunsOnTitles() {
        val regex = SearchOptions(regex = true)
        assertEquals(listOf("RSA ke"), TitleMatch.of("Cracking RSA keys", "r.a ke", regex)?.map { "Cracking RSA keys".substring(it) })
        assertNull(TitleMatch.of("Cracking RSA keys", "(a+)+", regex))
        assertNull(TitleMatch.of("Cracking RSA keys", "[", regex))
    }

    @Test
    fun aServerHitMustHoldEveryWord() {
        assertFalse(TermMatch.matches("weaknesses of the scheme", "weaknesses figure"))
        assertTrue(TermMatch.matches("this figure shows the weaknesses", "weaknesses figure"))
    }
}
