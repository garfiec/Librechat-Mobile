package com.garfiec.librechat.core.model.code

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CodeRunTest {

    // The app's network Json (core/network NetworkModule) as far as these bodies care.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun languagesFollowWebAliases() {
        assertEquals("py", CodeRunLanguages.normalize("Python3"))
        assertEquals("js", CodeRunLanguages.normalize(" node "))
        assertEquals("cpp", CodeRunLanguages.normalize("c++"))
        assertEquals("rs", CodeRunLanguages.normalize("rust"))
        assertNull(CodeRunLanguages.normalize("bash"))
        assertNull(CodeRunLanguages.normalize("kotlin"))
        assertNull(CodeRunLanguages.normalize(null))
    }

    @Test
    fun keysMatchWebMapToolCalls() {
        assertEquals("m1_0_2_execute_code", codeRunKey("m1", null, 2))
        assertEquals("m1_3_0_execute_code", codeRunKey("m1", 3, null))
    }

    @Test
    fun storedRunsGroupByBlockInServerOrderAndSkipOtherTools() {
        val body = """[
            {"toolId":"execute_code","messageId":"m1","partIndex":1,"blockIndex":0,"result":"first"},
            {"toolId":"web_search","messageId":"m1","partIndex":1,"blockIndex":0,"result":"x"},
            {"toolId":"execute_code","messageId":"m1","blockIndex":0,"result":"plain"},
            {"toolId":"execute_code","messageId":"m1","partIndex":1,"blockIndex":0,"result":"second",
             "attachments":[null,{"file_id":"f1","filename":"plot.png","filepath":"/images/plot.png"}]}
        ]"""
        val runs = json.decodeFromString<List<ToolCallRecord>>(body).codeRunsByKey()

        assertEquals(listOf("m1_1_0_execute_code", "m1_0_0_execute_code"), runs.keys.toList())
        assertEquals(listOf("first", "second"), runs.getValue("m1_1_0_execute_code").map { it.output })
        assertEquals(listOf("f1"), runs.getValue("m1_1_0_execute_code")[1].attachments.map { it.fileId })
    }

    @Test
    fun aNonStringResultReadsAsItsJson() {
        val response = json.decodeFromString<CodeRunResponse>("""{"result":[{"type":"text","text":"hi"}]}""")
        assertEquals("""[{"type":"text","text":"hi"}]""", response.toResult().output)
    }

    @Test
    fun aRequestWithoutPartsSendsNoPartIndex() {
        val body = json.encodeToJsonElement(
            CodeRunRequest.serializer(),
            CodeRunRequest(messageId = "m1", conversationId = "c1", blockIndex = 0, lang = "py", code = "print(1)"),
        ).jsonObject
        assertFalse("partIndex" in body)
        assertEquals(setOf("messageId", "conversationId", "blockIndex", "lang", "code"), body.keys)
    }
}
