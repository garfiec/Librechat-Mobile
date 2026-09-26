package com.garfiec.librechat.core.model.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * The one interface flag on this class where ABSENT means OFF.
 *
 * Scheduled chats are experimental and opt-in, so the server enables them only on an explicit
 * admin say-so. Every other object-form flag here defaults ON when absent, which is exactly why
 * this cannot go through a shared helper: the mistake would show a surface whose create and run
 * operations the backend rejects, and it would look like a working feature until the user used it.
 */
class SchedulesEnabledTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun config(body: String): InterfaceConfig = json.decodeFromString(body)

    @Test
    fun an_absent_flag_is_off() {
        assertFalse(isSchedulesEnabled(config("{}").schedules))
    }

    @Test
    fun an_explicit_null_is_off() {
        assertFalse(isSchedulesEnabled(config("""{"schedules": null}""").schedules))
    }

    @Test
    fun the_boolean_form_answers_directly() {
        assertFalse(isSchedulesEnabled(config("""{"schedules": false}""").schedules))
        assertTrue(isSchedulesEnabled(config("""{"schedules": true}""").schedules))
    }

    @Test
    fun an_empty_object_is_on() {
        // The object form is on unless it says otherwise — an admin who wrote a config block at
        // all has opted in, even before setting anything in it.
        assertTrue(isSchedulesEnabled(config("""{"schedules": {}}""").schedules))
    }

    @Test
    fun an_object_is_on_unless_it_sets_use_false() {
        assertFalse(isSchedulesEnabled(config("""{"schedules": {"use": false}}""").schedules))
        assertTrue(isSchedulesEnabled(config("""{"schedules": {"use": true}}""").schedules))
        assertTrue(
            isSchedulesEnabled(
                config("""{"schedules": {"maxPerUser": 5, "requireProject": true}}""").schedules,
            ),
        )
    }

    @Test
    fun the_runtime_config_rides_on_the_same_key() {
        // `interface.schedules` is both the feature switch and the policy block — the boolean
        // form is a runtime disable, not a permission toggle, and the object form carries limits.
        val config = config(
            """{"schedules": {"use": true, "maxPerUser": 3, "minIntervalMinutes": 15}}""",
        )

        assertTrue(isSchedulesEnabled(config.schedules))
    }

    @Test
    fun an_unreadable_shape_resolves_the_way_absent_does() {
        // A deliberate departure on input the server cannot produce: upstream reads anything that
        // is not null, `false` or `{use:false}` as ON, so a string or an array enables it there.
        // The config's zod union is `boolean | object`, so neither survives /api/config — and OFF
        // is the right direction for a feature that is off by default.
        assertFalse(isSchedulesEnabled(config("""{"schedules": ["nonsense"]}""").schedules))
        assertFalse(isSchedulesEnabled(config("""{"schedules": "true"}""").schedules))
    }
}
