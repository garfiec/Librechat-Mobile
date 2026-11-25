package com.garfiec.librechat.feature.chat.viewmodel.delegate

private const val KEY_SEPARATOR = "::"

/** Stable composite key for a (endpoint, model) favorite — used by pickers for membership checks and sorting. */
fun favoriteModelKey(endpoint: String, model: String): String = "$endpoint$KEY_SEPARATOR$model"

/** Inverse of [favoriteModelKey]: splits a key back into (endpoint, model), or null if malformed. */
fun parseFavoriteModelKey(key: String): Pair<String, String>? {
    val sep = key.indexOf(KEY_SEPARATOR)
    if (sep < 0) return null
    return key.substring(0, sep) to key.substring(sep + KEY_SEPARATOR.length)
}
