package com.garfiec.librechat.feature.skills.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The size limit in the import report sits inside a translated sentence, so it follows the app locale. */
class SkillImportFailureDialogTest {

    @Test
    fun `a whole limit reads without decimals`() {
        assertThat(formatMegabytes(5.0, "en-US")).isEqualTo("5")
        assertThat(formatMegabytes(5.0, "de-DE")).isEqualTo("5")
    }

    @Test
    fun `a fractional limit takes the locale's decimal separator`() {
        assertThat(formatMegabytes(1.5, "en-US")).isEqualTo("1.5")
        assertThat(formatMegabytes(1.5, "de-DE")).isEqualTo("1,5")
        assertThat(formatMegabytes(0.25, "fr-FR")).isEqualTo("0,25")
    }
}
