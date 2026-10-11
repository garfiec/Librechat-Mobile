package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class EphemeralAgent(
    val mcp: List<String>? = null,
    @SerialName("web_search") val webSearch: Boolean? = null,
    @SerialName("file_search") val fileSearch: Boolean? = null,
    @SerialName("execute_code") val executeCode: Boolean? = null,
    val artifacts: String? = null,
    /**
     * Equip this run with the memory tools (`set_memory`/`delete_memory`). Only honored when the
     * server has the `memory` agent capability enabled and the caller holds MEMORIES
     * USE+CREATE+UPDATE — the backend drops the tools otherwise.
     */
    val memory: Boolean? = null,
    /**
     * TCHAT: equip the run with the image toolkit. `true` means the default
     * `modelSpecs.imageList` entry; a string names an entry. Only Tchat servers read it.
     */
    @SerialName("image_gen") val imageGen: JsonPrimitive? = null,
)
