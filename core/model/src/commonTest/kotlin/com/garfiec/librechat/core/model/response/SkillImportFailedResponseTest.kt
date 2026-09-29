package com.garfiec.librechat.core.model.response

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SkillImportFailedResponseTest {

    @Test
    fun decodes_the_rc4_incomplete_import_body() {
        val body = """
            {"error":"skill_import_incomplete","message":"Skill import failed",
             "failedFiles":[
               {"path":"scripts/big.bin","reason":"file_too_large","limitMb":5},
               {"path":"../escape.md","reason":"invalid_path"}
             ]}
        """.trimIndent()

        val failure = SkillImportFailedResponse.from(body)

        assertEquals(SkillImportFailedResponse.INCOMPLETE, failure?.error)
        assertEquals(
            listOf(
                SkillImportFailedFile(path = "scripts/big.bin", reason = "file_too_large", limitMb = 5.0),
                SkillImportFailedFile(path = "../escape.md", reason = "invalid_path"),
            ),
            failure?.failedFiles,
        )
        assertNull(failure?.skillId)
    }

    @Test
    fun a_failed_rollback_names_the_skill_left_behind() {
        val body = """{"error":"skill_import_rollback_failed","message":"x","failedFiles":[],"skillId":"sk_1"}"""
        assertEquals("sk_1", SkillImportFailedResponse.from(body)?.skillId)
        assertEquals(
            SkillImportFailedResponse.CLEANUP_INCOMPLETE,
            SkillImportFailedResponse.from("""{"error":"skill_import_cleanup_incomplete","failedFiles":[]}""")?.error,
        )
    }

    @Test
    fun one_malformed_entry_does_not_discard_the_report() {
        val body = """
            {"error":"skill_import_rollback_failed","failedFiles":[
               {"path":"scripts/run.sh","reason":null},
               {"reason":"persistence_failed"},
               {"path":"ok.md","reason":"invalid_path"}
             ],"skillId":"sk_1"}
        """.trimIndent()

        val failure = SkillImportFailedResponse.from(body)

        assertEquals("sk_1", failure?.skillId)
        assertEquals(
            listOf(
                SkillImportFailedFile(path = "scripts/run.sh", reason = ""),
                SkillImportFailedFile(path = "", reason = "persistence_failed"),
                SkillImportFailedFile(path = "ok.md", reason = "invalid_path"),
            ),
            failure?.failedFiles,
        )
    }

    @Test
    fun any_other_error_body_is_not_an_import_report() {
        // A validation 400 on the same route, a generic sentence under `error`, and non-JSON.
        assertNull(SkillImportFailedResponse.from("""{"error":"Validation failed","issues":[]}"""))
        assertNull(SkillImportFailedResponse.from("""{"message":"Internal Server Error"}"""))
        assertNull(SkillImportFailedResponse.from("<html>502</html>"))
        assertNull(SkillImportFailedResponse.from(null))
    }
}
