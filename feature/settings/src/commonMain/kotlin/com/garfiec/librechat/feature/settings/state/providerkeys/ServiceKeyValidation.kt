package com.garfiec.librechat.feature.settings.state.providerkeys

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Three-valued result for live validation of a pasted/typed service-key JSON.
 *
 * - [Empty] — input is blank; render no banner (neutral).
 * - [Valid] — JSON parses, shape looks correct.
 * - [Invalid] — JSON fails to parse or required fields are missing/wrong type.
 */
internal enum class ServiceKeyValidation { Empty, Valid, Invalid }

/**
 * Validates a Google service-key JSON paste-or-import against web's `CredentialsSchema`
 * (`GoogleConfig.tsx`): `client_email` a valid email of at least 3 characters, `project_id`
 * at least 3, and `private_key` at least 601. See [ServiceKeyValidation].
 */
internal fun validateGoogleServiceKey(json: String): ServiceKeyValidation {
    if (json.isBlank()) return ServiceKeyValidation.Empty
    val parsed = runCatching { Json.parseToJsonElement(json) }.getOrNull()
        ?: return ServiceKeyValidation.Invalid
    val obj = (parsed as? JsonObject) ?: return ServiceKeyValidation.Invalid
    // Safe-cast to JsonPrimitive — the `.jsonPrimitive` extension throws on objects/arrays,
    // which would crash mid-keystroke on adversarial input.
    val email = (obj["client_email"] as? JsonPrimitive)?.stringOrNull()
    val projectId = (obj["project_id"] as? JsonPrimitive)?.stringOrNull()
    val privateKey = (obj["private_key"] as? JsonPrimitive)?.stringOrNull()
    val ok = email != null && email.length >= MIN_EMAIL_LENGTH && EMAIL_PATTERN.matches(email) &&
        projectId != null && projectId.length >= MIN_PROJECT_ID_LENGTH &&
        privateKey != null && privateKey.length >= MIN_PRIVATE_KEY_LENGTH
    return if (ok) ServiceKeyValidation.Valid else ServiceKeyValidation.Invalid
}

private fun JsonPrimitive.stringOrNull(): String? = if (isString) content else null

private const val MIN_EMAIL_LENGTH = 3
private const val MIN_PROJECT_ID_LENGTH = 3
private const val MIN_PRIVATE_KEY_LENGTH = 601

/**
 * zod 3's `string().email()` pattern, which web's `CredentialsSchema` uses. No literal
 * braces: `{2,}` is a quantifier, which ICU and the JVM agree on.
 */
private val EMAIL_PATTERN = Regex(
    """^(?!\.)(?!.*\.\.)([A-Z0-9_'+\-.]*)[A-Z0-9_+-]@([A-Z0-9][A-Z0-9\-]*\.)+[A-Z]{2,}$""",
    RegexOption.IGNORE_CASE,
)
