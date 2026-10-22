package com.garfiec.librechat.feature.skills

import com.garfiec.librechat.feature.skills.viewmodel.SkillEditorUiState
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class SkillValidationTest {

    @Test
    fun `name issues follow upstream validateSkillName`() {
        val cases = mapOf(
            "" to null,
            "pdf-tools" to null,
            "9lives" to null,
            // Prefixes are a prefix match only, so the namespace may appear later in the name.
            "research-anthropic-helper" to null,
            "claudette" to null,
            "helpful" to null,
            "Pdf-Tools" to SkillNameIssue.INVALID_FORMAT,
            "-leading-dash" to SkillNameIssue.INVALID_FORMAT,
            "a".repeat(SkillValidation.NAME_MAX_LENGTH + 1) to SkillNameIssue.INVALID_FORMAT,
            "anthropic-helper" to SkillNameIssue.RESERVED_PREFIX,
            "claude-code" to SkillNameIssue.RESERVED_PREFIX,
            "claude-" to SkillNameIssue.RESERVED_PREFIX,
            "help" to SkillNameIssue.RESERVED_WORD,
            "clear" to SkillNameIssue.RESERVED_WORD,
            "compact" to SkillNameIssue.RESERVED_WORD,
            "model" to SkillNameIssue.RESERVED_WORD,
            "exit" to SkillNameIssue.RESERVED_WORD,
            "quit" to SkillNameIssue.RESERVED_WORD,
            "settings" to SkillNameIssue.RESERVED_WORD,
            "anthropic" to SkillNameIssue.RESERVED_WORD,
            "claude" to SkillNameIssue.RESERVED_WORD,
        )
        cases.forEach { (name, expected) ->
            assertWithMessage(name).that(SkillValidation.nameIssue(name)).isEqualTo(expected)
        }
    }

    @Test
    fun `the 64-character name limit is inclusive`() {
        assertThat(SkillValidation.isNameValid("a".repeat(SkillValidation.NAME_MAX_LENGTH))).isTrue()
    }

    @Test
    fun `display title limit is 128 characters inclusive`() {
        assertThat(SkillValidation.isDisplayTitleValid("t".repeat(128))).isTrue()
        assertThat(SkillValidation.isDisplayTitleValid("t".repeat(129))).isFalse()
    }

    @Test
    fun `save is refused for a reserved name or an over-long title`() {
        val valid = SkillEditorUiState(name = "pdf-tools", description = "Extracts tables from PDF reports.")
        assertThat(valid.canSave).isTrue()

        assertThat(valid.copy(name = "claude-pdf").canSave).isFalse()
        assertThat(valid.copy(name = "settings").canSave).isFalse()
        val longTitle = valid.copy(displayTitle = "t".repeat(129))
        assertThat(longTitle.canSave).isFalse()
        assertThat(longTitle.displayTitleTooLong).isTrue()
        assertThat(valid.copy(displayTitle = "t".repeat(128)).canSave).isTrue()
    }

    @Test
    fun `the editor reports the name's issue for the field`() {
        assertThat(SkillEditorUiState(name = "anthropic-x").nameIssue).isEqualTo(SkillNameIssue.RESERVED_PREFIX)
        assertThat(SkillEditorUiState(name = "quit").nameIssue).isEqualTo(SkillNameIssue.RESERVED_WORD)
        assertThat(SkillEditorUiState(name = "").nameIssue).isNull()
    }
}
