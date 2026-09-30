package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.AskUserQuestionOption
import com.garfiec.librechat.feature.chat.util.AskAnswerDraft
import com.garfiec.librechat.feature.chat.util.composeAskAnswer
import com.garfiec.librechat.feature.chat.util.nextBlankQuestionIndex
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AskUserQuestionPanelLogicTest {

    private val questionIds = listOf("a", "b", "c")

    @Test
    fun `next blank searches forward from the current question and wraps`() {
        val answers = mapOf("a" to "", "b" to "yes", "c" to "")

        assertThat(nextBlankQuestionIndex(questionIds, answers, fromIndex = 0)).isEqualTo(2)
        assertThat(nextBlankQuestionIndex(questionIds, answers, fromIndex = 2)).isEqualTo(0)
    }

    @Test
    fun `next blank can land back on the current question`() {
        // Skipping forward past a question left blank must come back to it, not submit.
        val answers = mapOf("a" to "", "b" to "yes", "c" to "yes")

        assertThat(nextBlankQuestionIndex(questionIds, answers, fromIndex = 0)).isEqualTo(0)
    }

    @Test
    fun `next blank is null once every question is answered`() {
        val answers = mapOf("a" to "x", "b" to "y", "c" to "z")

        assertThat(nextBlankQuestionIndex(questionIds, answers, fromIndex = 1)).isNull()
    }

    @Test
    fun `a skipped draft composes to the declined sentinel whatever else it holds`() {
        val options = listOf(AskUserQuestionOption(label = "EU", value = "eu"))

        assertThat(composeAskAnswer(options, AskAnswerDraft(skipped = true)))
            .isEqualTo(ASK_USER_DECLINED_ANSWER)
        assertThat(composeAskAnswer(options, AskAnswerDraft(selectedOptions = listOf("eu"), skipped = true)))
            .isEqualTo(ASK_USER_DECLINED_ANSWER)
        assertThat(composeAskAnswer(options, AskAnswerDraft(selectedOptions = listOf("eu"))))
            .isEqualTo("eu")
    }
}
