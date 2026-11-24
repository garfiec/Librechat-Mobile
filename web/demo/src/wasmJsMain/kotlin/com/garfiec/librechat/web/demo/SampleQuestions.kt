package com.garfiec.librechat.web.demo

import com.garfiec.librechat.core.model.AskUserQuestionItem
import com.garfiec.librechat.core.model.AskUserQuestionOption
import com.garfiec.librechat.feature.chat.components.ASK_USER_DECLINED_ANSWER

/**
 * A reply that stops to ask the visitor a few questions (the app's question panel, which an agent's
 * `ask_user_question` tool raises), then answers with what they picked. [intro] streams first; the
 * run pauses on [questions]; [reply] is the rest of the answer, given each question's answer by id.
 */
internal class AskFlow(
    val ask: String,
    val intro: String,
    val questions: List<AskUserQuestionItem>,
    val reply: (answers: Map<String, String>) -> String,
)

private fun options(vararg labels: String) = labels.map { AskUserQuestionOption(label = it, value = it) }

internal val DessertTable = AskFlow(
    ask = "Help me plan a dessert table for Saturday.",
    intro = "Happy to. Three quick questions first, so the plan fits the party.",
    questions = listOf(
        AskUserQuestionItem(
            id = "guests",
            header = "Guests",
            question = "How many people are coming?",
            options = options("4 to 6", "8 to 12", "20 or more"),
        ),
        AskUserQuestionItem(
            id = "avoid",
            header = "Allergies",
            question = "Anything the desserts should avoid?",
            options = options("Nuts", "Gluten", "Dairy", "Nothing"),
            multiSelect = true,
        ),
        AskUserQuestionItem(
            id = "chocolate",
            header = "Chocolate",
            question = "How much Dubai chocolate should be involved?",
            options = options("A tasteful amount", "All of it", "None. Be strong."),
        ),
    ),
    reply = ::dessertPlan,
)

private fun dessertPlan(answers: Map<String, String>): String {
    fun answer(id: String) = answers[id]?.takeIf { it.isNotBlank() && it != ASK_USER_DECLINED_ANSWER }
    val guests = answer("guests") ?: "8 to 12"
    val avoid = answer("avoid").orEmpty()
    val noNuts = "Nuts" in avoid
    val noGluten = "Gluten" in avoid
    val noDairy = "Dairy" in avoid
    val chocolate = answer("chocolate") ?: "A tasteful amount"
    val desserts = when (guests) {
        "4 to 6" -> 3
        "20 or more" -> 6
        else -> 4
    }

    val rows = buildList {
        add(
            when {
                chocolate == "None. Be strong." -> "| Lemon posset | Make Friday, chill overnight | Citrus resets the palate |"
                noNuts -> "| Chocolate kataifi nests (no pistachio) | Saturday morning | The Dubai bar's crunch, nut-free |"
                chocolate == "All of it" -> "| Dubai chocolate tart | Friday night | The centrepiece. Also the side piece |"
                else -> "| Dubai chocolate bark, broken into shards | Friday night | One tasteful nod to the agent's habit |"
            },
        )
        add(if (noDairy) "| Coconut panna cotta | Friday, chill overnight | Dairy-free and still silky |" else "| Vanilla panna cotta | Friday, chill overnight | Make-ahead, no oven on the day |")
        add(if (noGluten) "| Flourless brownies | Saturday morning | Gluten-free by design |" else "| Brown butter blondies | Saturday morning | Easy to cut into many small squares |")
        add("| Fruit platter with honey yoghurt dip | Last thing | Something fresh between the rich ones |")
        if (chocolate == "All of it") add("| Chocolate-dipped strawberries | Saturday afternoon | Because you said all of it |")
        add(if (noNuts) "| Coconut macaroons | Friday | Nut-free, keeps for days |" else "| Pistachio financiers | Friday | Small, sturdy and travel well |")
    }.take(desserts)

    val note = when {
        chocolate == "All of it" -> "You asked for all of it, so the agent has already been told. Expect a delivery."
        chocolate == "None. Be strong." -> "No Dubai chocolate this time. The agent took it well, then asked if Sunday counts."
        noNuts -> "Pistachio is out, which rules out the classic Dubai bar; the kataifi nests keep the crunch."
        else -> "One chocolate dessert keeps the agent happy without taking over the table."
    }

    return """
        Here's the plan for **$guests guests**, ${if (avoid.isEmpty() || avoid == "Nothing") "with no restrictions" else "avoiding ${avoid.lowercase()}"}:

        | Dessert | When to make it | Why |
        |---|---|---|
        ${rows.joinToString("\n        ")}

        Plan on two or three small pieces per person, and put the fruit out last so the table never looks picked over.

        $note
    """.trimIndent()
}
