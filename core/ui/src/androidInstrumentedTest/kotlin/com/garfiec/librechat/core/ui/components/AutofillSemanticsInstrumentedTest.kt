package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.garfiec.librechat.core.model.ui.GlassCapability
import com.garfiec.librechat.core.ui.theme.LocalGlassLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Autofill reads its hints from the text field's own semantics node, so a hint set through
 * `modifier` only works if [AdaptiveOutlinedTextField] hands that modifier to the real field in both
 * styles. The auth screens rely on that for their login hints, and the secret fields for
 * [excludeFromAutofill].
 */
@RunWith(AndroidJUnit4::class)
class AutofillSemanticsInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setField(glass: GlassCapability?, modifier: Modifier) = composeRule.setContent {
        CompositionLocalProvider(LocalGlassLevel provides glass) {
            AdaptiveOutlinedTextField(value = "", onValueChange = {}, modifier = modifier.testTag(FIELD))
        }
    }

    private fun assertContentType(glass: GlassCapability?) {
        setField(glass, Modifier.semantics { contentType = LOGIN_ID })
        composeRule.onNodeWithTag(FIELD)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, LOGIN_ID))
    }

    private fun assertExcluded(glass: GlassCapability?) {
        setField(glass, Modifier.excludeFromAutofill())
        composeRule.onNodeWithTag(FIELD)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDataType, ContentDataType.None))
    }

    @Test
    fun contentTypeReachesTheFieldInMaterial() = assertContentType(glass = null)

    @Test
    fun contentTypeReachesTheFieldInLiquidGlass() = assertContentType(glass = GlassCapability.FULL)

    @Test
    fun excludeFromAutofillReachesTheFieldInMaterial() = assertExcluded(glass = null)

    @Test
    fun excludeFromAutofillReachesTheFieldInLiquidGlass() = assertExcluded(glass = GlassCapability.FULL)

    private companion object {
        const val FIELD = "field"

        // Combined content types compare by identity, so the test must assert the same instance.
        val LOGIN_ID = ContentType.Username + ContentType.EmailAddress
    }
}
