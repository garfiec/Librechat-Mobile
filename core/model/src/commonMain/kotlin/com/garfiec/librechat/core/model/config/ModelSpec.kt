package com.garfiec.librechat.core.model.config

import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.model.Preset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

@Serializable
data class ModelSpec(
    val name: String,
    val label: String? = null,
    val preset: Preset? = null,
    val iconURL: String? = null,
    val description: String? = null,
    /**
     * Whether the spec is offered in the model picker. The server already drops
     * `showInMenu: false` specs from `/api/config`, so mobile never receives one —
     * the field is modeled for round-trip completeness only. A hidden spec stays
     * resolvable by name (`spec: "<name>"`) on a conversation.
     */
    val showInMenu: Boolean? = null,
    /** The spec new chats open on when the server prioritizes specs. */
    @SerialName("default") val isDefault: Boolean = false,
    /** A default that yields to the user's own prior selection. */
    val softDefault: Boolean = false,
    /**
     * Picker position, ascending; specs without one follow, in list order. A `Double` because the
     * server's schema is `z.number()`: a fractional value would fail an `Int` decode, and with it
     * the whole `/api/config`.
     */
    val order: Double? = null,
    /**
     * Raw `skills` key: `true`, `false` or a list of skill names. Absent means the spec leaves
     * skills to the user's own toggle. Kept as a [JsonElement] because the key is a union; read it
     * through [skillsSetting].
     *
     * Not sent by v0.8.8: `GET /api/config` runs the specs through `sanitizeModelSpecs`, which
     * deletes `skills` from every spec (registered in `scripts/mirrors.json` as
     * `model-spec-skills-sanitized`), so this is always null against the targeted backend and
     * every spec-governed skills branch built on it is inert. The server still applies the spec's
     * setting to the run; the client just can't see it. Kept so the handling switches on if
     * upstream starts sending the key.
     */
    val skills: JsonElement? = null,
    /** Shown on the new-chat screen when the spec's agent, if it has one, has none of its own. */
    @SerialName("conversation_starters") val conversationStarters: List<String> = emptyList(),
    /**
     * Raw `artifacts` key: `true` or an artifacts mode (`"default"`, `"shadcnui"`, `"custom"`). Kept
     * as a [JsonElement] because the key is a union; read it through [artifactsMode].
     */
    val artifacts: JsonElement? = null,
) {
    /**
     * The `ephemeralAgent.artifacts` this spec turns on, or null for none. As web's
     * `applyModelSpecEphemeralAgent` (`client/src/utils/endpoints.ts`): `true` is the default mode
     * and a string is sent as is. The server reads artifacts from the ephemeral agent only, never
     * from the spec, so a client that doesn't send it gets no artifacts.
     */
    val artifactsMode: String?
        get() {
            val value = artifacts as? JsonPrimitive ?: return null
            if (value.booleanOrNull == true) return ArtifactsMode.DEFAULT.wire
            return value.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotEmpty() }
        }

    /**
     * What this spec says about skills, or null when it configures none.
     *
     * MIRRORED from `api/server/services/Endpoints/agents/initialize.js` (registered in
     * `scripts/mirrors.json` as `model-spec-skills`): `true` turns the full catalog on, `false`
     * turns skills off, a list limits them to those names. A spec that has the key governs the
     * ephemeral agent's skills outright, so the per-conversation toggle is not consulted.
     * An empty list means no skills, like `false`. A value of any other shape is ignored, as the
     * server's `Array.isArray` / `=== true` / `=== false` checks would ignore it.
     */
    val skillsSetting: ModelSpecSkills?
        get() = when (val raw = skills) {
            null -> null
            is JsonArray -> {
                val names = raw.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }
                if (names.isEmpty()) ModelSpecSkills.Off else ModelSpecSkills.Only(names)
            }
            is JsonPrimitive -> when (raw.takeUnless { it.isString }?.booleanOrNull) {
                true -> ModelSpecSkills.All
                false -> ModelSpecSkills.Off
                null -> null
            }
            else -> null
        }
}

/** A model spec's `skills` setting. See [ModelSpec.skillsSetting]. */
sealed interface ModelSpecSkills {
    /** `skills: true`: the whole catalog the user can reach. */
    data object All : ModelSpecSkills

    /** `skills: false` or `[]`: no skills, manual picks included. */
    data object Off : ModelSpecSkills

    /** `skills: [names]`: only these skills. */
    data class Only(val names: List<String>) : ModelSpecSkills
}
