package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * A reasoning choice for one turn only (v0.8.9-rc1 `reasoningOverride`): the server applies
 * `[key] = value` to that request's model parameters without saving it on the conversation, and
 * stores it on the user message it produced, so an edit or regenerate of that turn can send it again.
 *
 * [key] is one of [ReasoningOverrideKeys]. [value] is a string for the enum keys and an integer for
 * `thinkingBudget`, which web clients send; kept as a [JsonPrimitive] so a numeric value on a
 * message another client wrote still decodes.
 *
 * The server refuses (400 `invalid-reasoning-override`) a key the selected model does not resolve
 * to, or a value outside its options, so a caller checks it before sending.
 */
@Serializable
data class ReasoningOverride(
    val key: String,
    val value: JsonPrimitive,
) {
    /** The value as the string the enum keys carry, or null for a numeric budget. */
    val enumValue: String? get() = value.takeIf { it.isString }?.content

    /** The value as a whole number, for `thinkingBudget`; null for an enum value. */
    val budgetValue: Long? get() = value.takeIf { !it.isString }?.content?.toLongOrNull()

    companion object {
        fun level(key: String, value: String): ReasoningOverride = ReasoningOverride(key, JsonPrimitive(value))
    }
}

/**
 * MIRRORED from upstream `reasoningSettingKeys` (`packages/data-provider/src/parameterSettings.ts`),
 * which is also the key set `reasoningOverrideSchema` (`schemas.ts`) accepts.
 */
object ReasoningOverrideKeys {
    const val REASONING_EFFORT = "reasoning_effort"
    const val EFFORT = "effort"
    const val THINKING_LEVEL = "thinkingLevel"
    const val THINKING_BUDGET = "thinkingBudget"

    /** In upstream's resolution order: the first one a settings list carries wins for a custom endpoint. */
    val ALL: List<String> = listOf(REASONING_EFFORT, EFFORT, THINKING_LEVEL, THINKING_BUDGET)
}
