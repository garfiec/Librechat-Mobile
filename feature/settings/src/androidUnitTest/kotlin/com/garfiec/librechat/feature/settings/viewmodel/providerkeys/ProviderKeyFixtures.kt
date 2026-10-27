package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A service key that passes web's CredentialsSchema (private_key of at least 601 characters). */
internal val VALID_SERVICE_KEY: String = Json.encodeToString(
    JsonObject.serializer(),
    buildJsonObject {
        put("client_email", "svc@my-project.iam.gserviceaccount.com")
        put("project_id", "my-project")
        put("private_key", "-----BEGIN PRIVATE KEY-----\n" + "A".repeat(640) + "\n-----END PRIVATE KEY-----\n")
    },
)
