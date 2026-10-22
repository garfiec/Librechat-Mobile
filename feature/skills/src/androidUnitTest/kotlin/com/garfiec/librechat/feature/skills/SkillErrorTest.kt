package com.garfiec.librechat.feature.skills

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillErrorTest {

    @Test
    fun `server text is kept, and missing or blank text leaves the wording to the kind`() {
        assertThat(skillError(SkillErrorKind.LOAD_SKILL, "Skill not found"))
            .isEqualTo(SkillError(SkillErrorKind.LOAD_SKILL, "Skill not found"))
        assertThat(skillError(SkillErrorKind.LOAD_SKILL, null).message).isNull()
        assertThat(skillError(SkillErrorKind.LOAD_SKILL, " \n").message).isNull()
    }
}
