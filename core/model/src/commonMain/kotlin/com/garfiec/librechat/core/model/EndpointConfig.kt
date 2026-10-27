package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EndpointConfig(
    val type: String? = null,
    val order: Int? = null,
    val iconURL: String? = null,
    val modelDisplayLabel: String? = null,
    val name: String? = null,
    val userProvide: Boolean? = null,
    val userProvideURL: Boolean? = null,
    val capabilities: List<String> = emptyList(),
    val disableBuilder: Boolean? = null,
    val azure: Boolean? = null,
    /**
     * The providers an agent may run on, sent on the `agents` entry only. Empty means
     * unrestricted, which is also what a server that omits the field means.
     */
    val allowedProviders: List<String> = emptyList(),
    /**
     * Bedrock's per-credential flags: which of the AWS credentials the user supplies. The server
     * sets each from its `BEDROCK_AWS_*` env var being `user_provided`; older servers omit them.
     */
    val userProvideAccessKeyId: Boolean? = null,
    val userProvideSecretAccessKey: Boolean? = null,
    val userProvideSessionToken: Boolean? = null,
    val userProvideBearerToken: Boolean? = null,
)
