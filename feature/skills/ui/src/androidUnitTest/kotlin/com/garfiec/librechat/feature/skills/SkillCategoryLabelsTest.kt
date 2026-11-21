package com.garfiec.librechat.feature.skills

import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.feature.skills.resources.Res
import com.garfiec.librechat.feature.skills.resources.skill_category_idea
import com.garfiec.librechat.feature.skills.resources.skill_category_learning
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillCategoryLabelsTest {

    // What upstream's GET /api/categories returns: labels are web localization keys.
    private val upstream = listOf(
        Category(label = "com_ui_idea", value = "idea"),
        Category(label = "com_ui_teach_or_explain", value = "teach_or_explain"),
    )

    @Test
    fun `a web localization key label falls through to the built-in translation`() {
        assertThat(resolveSkillCategoryLabel("idea", upstream))
            .isEqualTo(SkillCategoryLabel.BuiltIn(Res.string.skill_category_idea))
        assertThat(resolveSkillCategoryLabel("teach_or_explain", upstream))
            .isEqualTo(SkillCategoryLabel.BuiltIn(Res.string.skill_category_learning))
    }

    @Test
    fun `a plain-text server label wins over the built-in`() {
        val custom = listOf(Category(label = "Brainstorming", value = "idea"))
        assertThat(resolveSkillCategoryLabel("idea", custom)).isEqualTo(SkillCategoryLabel.Server("Brainstorming"))
    }

    @Test
    fun `built-ins resolve before the categories load`() {
        assertThat(resolveSkillCategoryLabel("idea", emptyList()))
            .isEqualTo(SkillCategoryLabel.BuiltIn(Res.string.skill_category_idea))
    }

    @Test
    fun `an unknown value falls back to the value`() {
        assertThat(resolveSkillCategoryLabel("legal", upstream)).isEqualTo(SkillCategoryLabel.Value("legal"))
        val blank = listOf(Category(label = " ", value = "legal"))
        assertThat(resolveSkillCategoryLabel("legal", blank)).isEqualTo(SkillCategoryLabel.Value("legal"))
    }
}
