package com.garfiec.librechat.feature.chat.components

import kotlin.test.Test
import kotlin.test.assertEquals

class LandingHeroTest {

    private val icon = LandingHeroIcon(endpointName = "openAI", iconUrl = "https://srv/spec.png")

    @Test
    fun a_temporary_chat_shows_its_own_glyph_rather_than_the_hero_icon() {
        assertEquals(LandingHero.Temporary, landingHero(compact = false, temporary = true, heroIcon = icon))
    }

    @Test
    fun a_temporary_chat_without_a_hero_icon_shows_its_own_glyph_rather_than_the_app_tile() {
        assertEquals(LandingHero.Temporary, landingHero(compact = false, temporary = true, heroIcon = null))
    }

    @Test
    fun a_normal_chat_shows_its_hero_icon() {
        assertEquals(LandingHero.Endpoint(icon), landingHero(compact = false, temporary = false, heroIcon = icon))
    }

    @Test
    fun a_normal_chat_without_a_hero_icon_shows_the_app_tile() {
        assertEquals(LandingHero.AppTile, landingHero(compact = false, temporary = false, heroIcon = null))
    }

    @Test
    fun compact_drops_every_icon() {
        assertEquals(LandingHero.None, landingHero(compact = true, temporary = true, heroIcon = icon))
        assertEquals(LandingHero.None, landingHero(compact = true, temporary = false, heroIcon = icon))
        assertEquals(LandingHero.None, landingHero(compact = true, temporary = false, heroIcon = null))
    }
}
