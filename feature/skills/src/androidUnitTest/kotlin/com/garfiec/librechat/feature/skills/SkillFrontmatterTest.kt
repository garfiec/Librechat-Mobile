package com.garfiec.librechat.feature.skills

import com.garfiec.librechat.feature.skills.util.FrontmatterField
import com.garfiec.librechat.feature.skills.util.HEADER_FRONTMATTER_KEYS
import com.garfiec.librechat.feature.skills.util.parseFrontmatter
import kotlin.test.Test
import kotlin.test.assertEquals

class SkillFrontmatterTest {

    @Test
    fun `splits the block from the body and skips the header's keys`() {
        val raw = """
            ---
            name: zarr-python
            description: "Stores arrays"
            license: MIT license
            ---

            # Zarr Python

            Body.
        """.trimIndent()

        val parsed = parseFrontmatter(raw, HEADER_FRONTMATTER_KEYS)

        assertEquals(listOf(FrontmatterField("license", "MIT license")), parsed.fields)
        assertEquals("# Zarr Python\n\nBody.", parsed.body)
    }

    @Test
    fun `a list joins its items and a skipped list does not leak into later keys`() {
        val raw = "---\nname:\n  - a\n  - b\ntags:\n  - x\n  - y\nempty:\n---\nBody"

        val parsed = parseFrontmatter(raw, setOf("name"))

        assertEquals(listOf(FrontmatterField("tags", "x,y")), parsed.fields)
        assertEquals("Body", parsed.body)
    }

    @Test
    fun `a body with no frontmatter or an unclosed block comes back unchanged`() {
        assertEquals(parseFrontmatter("# Title\n\nText").body, "# Title\n\nText")
        val unclosed = "---\nname: x\n# Title"
        assertEquals(unclosed, parseFrontmatter(unclosed).body)
        assertEquals(emptyList(), parseFrontmatter(unclosed).fields)
    }
}
