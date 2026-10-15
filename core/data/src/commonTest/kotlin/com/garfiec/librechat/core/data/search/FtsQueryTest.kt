package com.garfiec.librechat.core.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FtsQueryTest {

    private fun expression(input: String) = assertIs<FtsQueryPlan.Match>(FtsQuery.plan(input)).expression

    @Test
    fun quotesEveryTokenAndAndsThem() {
        assertEquals("\"kotlin\" \"flow\"", expression("kotlin flow "))
    }

    @Test
    fun prefixesOnlyTheWordBeingTyped() {
        assertEquals("\"kotlin\" \"flo*\"", expression("kotlin flo"))
        assertEquals("\"kotlin\" \"flo\"", expression("kotlin flo "))
    }

    @Test
    fun noPrefixUnderThreeCharacters() {
        assertEquals("\"kotlin\" \"fl\"", expression("kotlin fl"))
        assertEquals("\"ab\"", expression("ab"))
    }

    @Test
    fun thePlanSaysWhetherTheLastWordBecameAPrefix() {
        assertEquals(true, assertIs<FtsQueryPlan.Match>(FtsQuery.plan("kotlin flo")).lastIsPrefix)
        assertEquals(false, assertIs<FtsQueryPlan.Match>(FtsQuery.plan("kotlin fl")).lastIsPrefix)
        assertEquals(false, assertIs<FtsQueryPlan.Match>(FtsQuery.plan("kotlin flow ")).lastIsPrefix)
    }

    @Test
    fun operatorsAndSyntaxBecomePlainWords() {
        assertEquals("\"alpha\" \"OR\" \"beta\"", expression("alpha OR beta "))
        assertEquals("\"NOT\" \"alpha\"", expression("NOT alpha "))
        assertEquals("\"alpha\" \"NEAR/2\" \"beta\"", expression("alpha NEAR/2 beta "))
        assertEquals("\"col:val\"", expression("col:val "))
    }

    @Test
    fun quotesAndStarsAreStripped() {
        assertEquals("\"alpha\" \"beta*\"", expression("\"alpha\" be*ta"))
    }

    @Test
    fun punctuationOnlyTokensAreDropped() {
        assertEquals("\"alpha\"", expression("alpha -- ! "))
        assertEquals(FtsQueryPlan.Empty, FtsQuery.plan("-- !!"))
    }

    @Test
    fun tooShortIsEmpty() {
        assertEquals(FtsQueryPlan.Empty, FtsQuery.plan(""))
        assertEquals(FtsQueryPlan.Empty, FtsQuery.plan(" a "))
    }

    @Test
    fun unsegmentedScriptsAreScanned() {
        assertEquals(FtsQueryPlan.Scan("数据库"), FtsQuery.plan(" 数据库 "))
        assertEquals(FtsQueryPlan.Scan("猫"), FtsQuery.plan("猫"))
        assertEquals(FtsQueryPlan.Scan("カタカナ"), FtsQuery.plan("カタカナ"))
        assertEquals(FtsQueryPlan.Scan("한국어"), FtsQuery.plan("한국어"))
        assertEquals(FtsQueryPlan.Scan("ภาษาไทย"), FtsQuery.plan("ภาษาไทย"))
        assertEquals(FtsQueryPlan.Scan("kotlin 协程"), FtsQuery.plan("kotlin 协程"))
    }

    @Test
    fun accentedLatinStaysOnTheIndex() {
        assertEquals("\"café\"", expression("café "))
    }

    @Test
    fun termsAreTheTokensAsTyped() {
        assertEquals(listOf("Kotlin", "flo"), assertIs<FtsQueryPlan.Match>(FtsQuery.plan("Kotlin flo")).terms)
    }
}
