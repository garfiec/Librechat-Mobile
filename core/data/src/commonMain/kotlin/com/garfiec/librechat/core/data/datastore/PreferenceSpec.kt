package com.garfiec.librechat.core.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.common.ChannelPolicy

/**
 * One device-wide preference: where it is stored and what it reads as before the user has chosen.
 * Declare preferences in [AppPreferences] and read them through [PreferenceStore].
 *
 * [default] is evaluated on every read and never written, so a stored choice always wins and an
 * unchosen preference follows the current build's default. Build it with [always] or [perChannel].
 *
 * [S] is the stored type and [V] the value callers see. [decode] returning null treats a stored
 * value the app no longer understands as unchosen.
 */
class PreferenceSpec<S : Any, V>(
    val key: Preferences.Key<S>,
    val default: (ChannelPolicy) -> V,
    val decode: (S) -> V?,
    val encode: (V & Any) -> S,
)

/** The same default on every channel. */
fun <V> always(value: V): (ChannelPolicy) -> V = { value }

/** A default per distribution channel. Every channel is named, so adding one fails to compile here. */
fun <V> perChannel(openSource: V, playStore: V, appStore: V): (ChannelPolicy) -> V =
    { it.pick(openSource = openSource, playStore = playStore, appStore = appStore) }

/** A boolean preference. [V] is `Boolean?` when "not chosen yet" means something of its own. */
fun <V : Boolean?> boolPreference(name: String, default: (ChannelPolicy) -> V): PreferenceSpec<Boolean, V> =
    PreferenceSpec(
        key = booleanPreferencesKey(name),
        default = default,
        decode = { stored ->
            @Suppress("UNCHECKED_CAST") // V is Boolean or Boolean?, and a stored Boolean is either.
            stored as V
        },
        encode = { it },
    )

/** An int preference. [decode] may clamp a stored value, or return null to treat it as unchosen. */
fun intPreference(
    name: String,
    default: (ChannelPolicy) -> Int,
    decode: (Int) -> Int? = { it },
): PreferenceSpec<Int, Int> = PreferenceSpec(key = intPreferencesKey(name), default = default, decode = decode, encode = { it })

/** A string preference. [decode] returning null treats a stored value as unchosen. */
fun <V : Any> stringPreference(
    name: String,
    default: (ChannelPolicy) -> V,
    decode: (String) -> V?,
    encode: (V) -> String,
): PreferenceSpec<String, V> = PreferenceSpec(key = stringPreferencesKey(name), default = default, decode = decode, encode = encode)
