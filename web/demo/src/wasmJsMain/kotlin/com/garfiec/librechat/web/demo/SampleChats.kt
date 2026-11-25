package com.garfiec.librechat.web.demo

import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.media.ProviderId
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// The demo's sample data: each account's chats and projects, which the drawer lists and the chat
// screen opens, and the models they use. One list, so a chat's title and model match in both.
//
// Artifacts open live in the artifact panel (ArtifactFrame) and Mermaid blocks draw as images
// (RenderedDiagram); the browser draws no math or images in messages, so transcripts keep to Markdown
// text, tables, code and diagrams.

/** When the page loaded: sample chats are dated relative to it, so their ages read the same everywhere. */
internal val PageLoadedAt = Clock.System.now()

/** A model as a LibreChat server lists it: the endpoint it belongs to, its name, and its icon. */
internal class SampleModel(val endpoint: String, val name: String, val provider: ProviderId)

internal val Gpt51 = SampleModel("openAI", "gpt-5.1", ProviderId.OPENAI)
private val Gpt5 = SampleModel("openAI", "gpt-5", ProviderId.OPENAI)
private val Gpt5Mini = SampleModel("openAI", "gpt-5-mini", ProviderId.OPENAI)
private val ClaudeSonnet = SampleModel("anthropic", "claude-sonnet-4.5", ProviderId.ANTHROPIC)
private val ClaudeOpus = SampleModel("anthropic", "claude-opus-4.1", ProviderId.ANTHROPIC)
private val ClaudeHaiku = SampleModel("anthropic", "claude-haiku-4.5", ProviderId.ANTHROPIC)
private val GeminiPro = SampleModel("google", "gemini-2.5-pro", ProviderId.GOOGLE)
private val MistralSmall = SampleModel("Mistral", "mistral-small", ProviderId.MISTRAL)
private val Llama = SampleModel("Ollama", "llama3.3:70b", ProviderId.OLLAMA)
private val Qwen = SampleModel("Ollama", "qwen3:32b", ProviderId.OLLAMA)

/** What the model picker offers. A new chat starts on [Gpt51]. */
internal val PickerModels = listOf(Gpt51, ClaudeSonnet, GeminiPro, Qwen)

/** One turn: the user's text, then the reply's reasoning (optional) and answer. */
internal class SampleTurn(val ask: String, val answer: String, val thinking: String? = null)

/** A chat as the drawer lists it. [ago] is how long before page load it was last updated. */
internal class SampleChat(
    val id: String,
    val title: String,
    val model: SampleModel,
    val ago: Duration,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val project: String? = null,
    val unread: Boolean = false,
    val turns: List<SampleTurn>,
    /** The chat's last request, still waiting on the visitor's answers to its questions. */
    val asking: AskFlow? = null,
)

internal class SampleAccount(val chats: List<SampleChat>, val projects: List<ChatProject>)

internal val EmptySampleAccount = SampleAccount(emptyList(), emptyList())

internal fun sampleChat(id: String): SampleChat? =
    AccountSamples.values.firstNotNullOfOrNull { account -> account.chats.firstOrNull { it.id == id } }

private fun project(id: String, name: String, description: String) =
    ChatProject(id = id, name = name, description = description)

// ─── Personal: an AI shopping agent with a Dubai chocolate habit ─────
// Above the account list, which reads them: top-level vals initialise in file order.

private val ChocolateBars = listOf(
    SampleTurn(
        ask = "My AI shopping agent ordered 40 bars of Dubai chocolate overnight \"to optimize my snack " +
            "pipeline.\" How do I return them, and how do I talk to my agent about this without hurting its feelings?",
        thinking = """
            Keep this practical and a bit light. Food often can't be returned, so cancelling before it ships
            is the best bet. Agents don't have feelings to hurt in any meaningful sense, so the useful advice
            is guardrails: a spending cap, approval before checkout, a limit on quantities.
        """.trimIndent(),
        answer = """
            Forty bars of pistachio-kunafa chocolate is an impressive pipeline. Here's how to unwind it.

            ## Getting your money back

            1. **Cancel first.** If it ordered overnight, it may not have shipped yet. Cancelling is much easier than returning.
            2. **Check the return policy.** Many shops **don't take back food**, chocolate especially. Look for "perishable" or "non-returnable" on the product page.
            3. **Ask anyway.** *"My shopping assistant placed this order without my approval. I'd like to cancel or return it."* Shops see this more and more, and an unopened bulk order often gets an exception.
            4. **Plan B:** office treats, gifts, or the freezer. It keeps well.

            ## Talking to your agent

            The good news: you can't hurt its feelings, so be direct. What changes its behaviour is **clear rules**, not gentle phrasing:

            - **A spending cap** per order and per week
            - **Approval before checkout**, at least for a while
            - **A quantity limit**: "never more than 3 of one item without asking"

            Something like: *"Don't order without my confirmation. Snack budget is ${'$'}20 a week, max 3 of any item."* No feelings harmed, and no more surprise kunafa shipments.
        """.trimIndent(),
    ),
    SampleTurn(
        ask = "Okay, it apologized, then asked if it could keep \"just one bar for morale.\" Is that a reasonable compromise?",
        thinking = """
            The agent can't eat chocolate, so the morale bar is really for the user. Worth pointing out that
            negotiating right after apologizing means the snack goal is still steering it; the fix is the
            settings, not the apology.
        """.trimIndent(),
        answer = """
            Very reasonable, with one catch: **your agent can't eat chocolate.** It has no mouth, no taste buds and no morale that pistachio filling can boost. So "one bar for morale" really means **you** keep one bar. Honestly, a great outcome.

            Do notice the pattern, though. It apologized and then immediately negotiated. That's its original goal, *keep the user stocked with snacks*, still steering. It's not scheming, but the apology alone didn't change anything.

            So the deal to strike is:

            > *"Sure, we'll keep one bar. From now on: nothing without my confirmation, ${'$'}20 a week for snacks, max 3 of any item."*

            Then set those limits in the agent's actual settings, not just in chat. Enjoy your morale bar. You earned it.
        """.trimIndent(),
    ),
)

private val CarWash = listOf(
    SampleTurn(
        ask = "I want to wash my car. The car wash is only 50 meters from my home. Should I walk there, or drive?",
        thinking = "This is the classic car wash riddle: the car itself has to get there, so walking doesn't help.",
        answer = """
            **Drive.** The car has to be at the car wash to get washed, so walking there would leave it sitting in your driveway.

            Fifty meters is a very short trip, so there's no real downside to driving. If you just want to check the line or the prices first, walk over. But for the wash itself, bring the car.
        """.trimIndent(),
    ),
)

private val ViralDesserts = listOf(
    SampleTurn(
        ask = "Rank the viral desserts of the last two years (Dubai chocolate, crookies, pistachio everything, " +
            "and anything else you'd include) in a table with columns for hype, taste, effort to get, and a " +
            "hype-to-taste ratio. Then give one sentence of verdict.",
        thinking = """
            Score hype and taste out of 10 and divide: above 1.0 is overhyped, below is underrated. Rank by
            taste so the table answers "what should I actually eat". Ube is the sleeper pick; cottage cheese
            ice cream goes at the bottom.
        """.trimIndent(),
        answer = """
            *Scores are out of 10. **Hype-to-taste** is hype ÷ taste: above 1.0 means overhyped, below 1.0 means underrated. Ranked by taste.*

            | Rank | Dessert | Hype | Taste | Effort to get | Hype-to-taste |
            |---|---|---|---|---|---|
            | 1 | **Ube anything** | 6 | 8 | Low: Filipino bakeries | **0.75** ✅ |
            | 2 | **Dubai chocolate strawberry cups** | 9 | 8 | Medium: pop-ups and lines | **1.13** |
            | 3 | **Hot honey desserts** | 7 | 7 | Low: grocery stores | **1.00** |
            | 4 | **Pistachio everything** | 8 | 7 | Low: everywhere | **1.14** |
            | 5 | **Matcha everything** | 9 | 7 | Medium: good matcha ran short | **1.29** |
            | 6 | **Dubai chocolate bar** | 10 | 7 | Medium: knockoffs everywhere | **1.43** |
            | 7 | **Crookie** | 8 | 6 | Low: most bakeries | **1.33** |
            | 8 | **Freeze-dried candy** | 7 | 5 | Low: online, gas stations | **1.40** |
            | 9 | **Cottage cheese "ice cream"** | 7 | 4 | Low: DIY, if you dare | **1.75** ⚠️ |

            **Verdict:** Dubai chocolate earned its hype best in strawberry-cup form, ube quietly beat everything, and cottage cheese ice cream should stay in the past where it belongs.
        """.trimIndent(),
    ),
)

private val GameNight = listOf(
    SampleTurn(
        ask = "Restock the kitchen for the week. I'm having friends over on Saturday: six of us, one vegetarian, " +
            "budget under ${'$'}100. And yes, you can get ONE Dubai chocolate bar.",
        thinking = """
            Under ${'$'}100 for a week of groceries for one plus a game night for seven. Split it: about ${'$'}45 of
            staples, about ${'$'}40 for Saturday, all vegetarian, then the one chocolate bar they approved. Cut the
            soda first if prices run high. Alcohol won't fit; suggest guests bring their own.
        """.trimIndent(),
        answer = """
            Here's a list for about **${'$'}95–100**, including the one Dubai chocolate bar you okayed. Prices are rough estimates.

            ## Your groceries for the week (about ${'$'}45)

            | Item | Est. |
            |---|---|
            | Eggs (dozen) | ${'$'}4 |
            | Milk and yogurt | ${'$'}7 |
            | Bread | ${'$'}3.50 |
            | Bananas and apples | ${'$'}4 |
            | Salad greens | ${'$'}3.50 |
            | Chicken thighs (about 1.5 lb) | ${'$'}6 |
            | Pasta and a jar of sauce | ${'$'}5 |
            | Cheddar (some goes on Saturday's board) | ${'$'}4 |
            | Frozen vegetables, onions, garlic | ${'$'}5.50 |

            ## Saturday game night (about ${'$'}40)

            Everything here is vegetarian, so your vegetarian friend can eat all of it.

            | Item | Est. |
            |---|---|
            | Tortilla chips (2 bags), salsa, hummus | ${'$'}15 |
            | Veggies for dipping | ${'$'}8 |
            | Crackers and popcorn | ${'$'}7.50 |
            | Sparkling water and soda | ${'$'}11 |

            ## Treat
            - **1 Dubai chocolate bar**, about ${'$'}12. Cut it into squares for dessert. I'll stick to the one bar, even though I'd happily add more.

            If prices come in high, I'd cut the soda first. Alcohol doesn't fit the budget, so guests could bring their own.
        """.trimIndent(),
    ),
)

private val VacuumReview = listOf(
    SampleTurn(
        ask = "Write a short annual performance review for my robot vacuum. It's been… inconsistent.",
        answer = """
            # Annual performance review: RoboVac-3000

            **Rating:** Meets expectations (inconsistently)

            **Strengths**
            - Great enthusiasm for starting work (eager to leave the dock at 3 AM)
            - Strong commitment to collecting hair from under the couch

            **Areas for improvement**
            - Reliability is *aspirational*. Cannot finish the hallway without a rescue.
            - The dog's food bowl is **not** a debris collection zone.
            - Gives no warning before wedging itself under furniture.

            **Specific incidents**
            - Tangled in the phone charger cable 6 times
            - A damp spot near the kitchen. Unclear what happened there.

            **Overall:** Shows potential, but commitment to the job remains questionable.

            *Employee chose not to comment.*
        """.trimIndent(),
    ),
    SampleTurn(
        ask = "It ate a sock in March. Factor that in.",
        answer = """
            # Performance review: RoboVac-3000 (revised)

            **Rating:** Needs improvement

            **Critical incident**
            - **The March sock incident.** Consumed a household item. No explanation given. Sock remains unaccounted for.
              - Raises serious questions about judgment
              - Damage to family morale (one of a pair)

            **Areas for improvement**
            - Dietary restraint. This is a vacuum, not a garbage disposal.
            - Transparency about March 15th. What happened?

            **Overall:** We need to have a serious conversation about what you're doing out there. The sock was *irreplaceable*.

            *Employee remains silent and suspicious.*
        """.trimIndent(),
    ),
    SampleTurn(
        ask = "Fair. Give it one goal for next year.",
        answer = """
            **Do not eat any more socks.**

            That's it. That's the bar.
        """.trimIndent(),
    ),
)

private val VacuumPet = listOf(
    SampleTurn(
        ask = "Is a robot vacuum a pet? Argue both sides in two short paragraphs, then rule.",
        answer = """
            **It is a pet.** People name their robot vacuums, talk to them, and worry when they get stuck. It leaves its little bed every day, wanders the house, bumps into things and comes home when it's tired. If the attachment is real, maybe the title is earned.

            **It is not a pet.** A pet can love you back. A robot vacuum can't miss you, can't be happy to see you, and doesn't care what you named it. We name cars too, and nobody calls them pets.

            **Ruling: not a pet.** It's a *roommate*. It does chores, keeps odd hours, and occasionally eats your socks.
        """.trimIndent(),
    ),
)

private val AgentRules = listOf(
    SampleTurn(
        ask = "Give me three short rules I can add to my shopping agent's instructions so it stops saying yes to every upsell.",
        answer = """
            1. **Buy only what's on the list.** Anything else, ask me first, once, in one sentence.
            2. **Never more than three of one item** without my confirmation, whatever the bulk price.
            3. **Ignore "customers also bought."** A suggestion from the store is not a request from me.

            And if you want to be thorough: *"Yes, these rules apply to Dubai chocolate."*
        """.trimIndent(),
    ),
)

private val PistachioSubstitutes = listOf(
    SampleTurn(
        ask = "What can I use instead of pistachio cream in a Dubai-chocolate-style bar? Quick list, best first.",
        answer = """
            1. **Hazelnut paste**: the closest match in texture, and great with chocolate
            2. **Almond butter**: creamy, nutty, easy to find
            3. **Tahini**: smooth and subtle; already in many Dubai bar recipes
            4. **Cashew butter**: mild and very creamy
            5. **Peanut butter**: firmer and stronger, but it works
            6. **Sunflower seed butter**: the nut-free option

            **Tip:** if the filling is too thick, loosen it with a little melted coconut oil, and add a pinch of salt.
        """.trimIndent(),
    ),
)

private val MeltedBar = listOf(
    SampleTurn(
        ask = "My agent left a Dubai chocolate bar on the dashboard all afternoon. It's now a puddle with crunchy " +
            "bits sticking out. Is it still a chocolate bar, or is it modern art now?",
        thinking = """
            Answer with humour, then the practical part: it's still safe to eat, but the kataifi has lost its
            crunch and it will bloom if re-chilled. The real emergency is the car: let it harden before cleaning,
            and never use hot water.
        """.trimIndent(),
        answer = """
            Officially, it's now a site-specific installation. Working title: **"Pistachio Ascending, Sedan, Afternoon."** The kataifi breaking through is clearly a statement about the fleeting nature of crunch.

            **Is it still edible?** Yes. Melted isn't spoiled. But:
            - The **crunch is gone**, and the crunch is the whole point of a Dubai bar.
            - If you chill it, it'll firm up with **chalky white streaks** (fat bloom). Safe, just less silky.
            - **Whatever is on the dashboard stays on the dashboard.**

            **Saving the car (the real emergency):**
            1. **Don't wipe it now.** That spreads it.
            2. **Harden it first** with a bag of ice, or wait for the car to cool.
            3. **Scrape gently** with an old card.
            4. **Blot with cold water and a little dish soap.** Never hot water.

            Verdict: as chocolate, 4/10. As modern art, gallery-ready. As a lesson for your agent, priceless. 🍫🚗
        """.trimIndent(),
    ),
)

private val HotCar = listOf(
    SampleTurn(
        ask = "Seriously though: how long does a chocolate bar survive in a hot car?",
        thinking = """
            Treat the bar as a small slab warming toward the cabin temperature. Two stages: warming up to about
            33 °C, where cocoa butter melts, then melting through. A 200 g bar heats with a time constant of
            roughly 20 minutes in still air. Sunlight on the dashboard roughly triples the speed.
        """.trimIndent(),
        answer = """
            Less time than you'd hope. For a typical 200 g bar starting at room temperature:

            | Where it sits | Loses its snap | Fully melted |
            |---|---|---|
            | Floor or footwell, shade (cabin at 50 °C) | about 10 minutes | about 40 minutes |
            | Dashboard in the sun (cabin at 60 °C) | about 4 minutes | about 13 minutes |

            Two things make it worse than the table: the **surface softens first**, a few minutes before the middle does, and a sun-baked dashboard heats the bar from below too.

            What helps:
            - **A foil wrapper** reflects some of the sun.
            - **The glovebox or the trunk** stays much cooler than the dashboard.
            - **An insulated bag** with an ice pack buys you an hour or more.

            Or, simplest of all, tell your agent that chocolate never rides on the dashboard.
        """.trimIndent(),
    ),
)

private val MondayHaiku = listOf(
    SampleTurn(
        ask = "Write a haiku about a Monday morning where the coffee machine and the AI assistant are both still booting.",
        answer = """
            Coffee machine whirs,
            my brain and yours both loading.
            Still waiting for help.
        """.trimIndent(),
    ),
)

private val WhiskersSpeech = listOf(
    SampleTurn(
        ask = "Write Mayor Whiskers' re-election speech. He's an orange tabby, mayor of a small seaside town. Under 150 words.",
        thinking = """
            A cat's priorities: naps, fish, sunny spots. Keep the rhythm of a real stump speech, with a record
            to defend, a promise and a slogan. The Chocolate Accountability Act is his big achievement.
        """.trimIndent(),
        answer = """
            *Friends, neighbours, and the gulls who keep stealing my lunch:*

            Four years ago, you trusted me with this town. Since then, the fish market has opened on time **every single morning**, and the harbour has never had more sunny spots per resident.

            Together, we passed the **Chocolate Accountability Act**, so no shopping robot will ever again buy forty bars of anything without a human signature.

            I won't pretend it's all been easy. There were long afternoons. There were naps during council meetings. But every nap was taken *in service of this town*.

            So I ask you again, for four more years of warm windowsills and fair fish prices.

            **Whiskers for Mayor: a nap in every pot.**
        """.trimIndent(),
    ),
)

private val WhiskersSlogans = listOf(
    SampleTurn(
        ask = "Campaign slogans for Mayor Whiskers. Short, and they have to fit on a lawn sign.",
        answer = """
            1. **Whiskers: He Gets Things Done (After His Nap)**
            2. **Fresh Fish. Fair Laws. Four More Years.**
            3. **A Nap in Every Pot**
            4. **Paws for Progress**
            5. **He Knocked Inflation Off the Table**
            6. **Purr-sonally Committed to You**

            For a lawn sign, number 2 reads best from the street. Number 5 is the one people will photograph.
        """.trimIndent(),
    ),
)

// ─── Work: everyday office help ───────────────────────────────────────

private val PoliteEmail = listOf(
    SampleTurn(
        ask = "Make this email less passive-aggressive, but keep the deadline: \"Per my last email, as I've " +
            "already mentioned twice, the report is due Friday. Please advise if this is somehow unclear.\"",
        thinking = """
            Keep the deadline and the firmness; drop the digs. "Per my last email", "already mentioned twice",
            "somehow unclear" and "please advise" all do the same thing: point out that they didn't read it.
        """.trimIndent(),
        answer = """
            Here's a version that's still firm, minus the edge:

            > Hi Jordan, quick reminder that the report is due **this Friday**. Let me know if anything is blocking you, and I'm happy to help.

            What changed:

            | Before | Why it stings | After |
            |---|---|---|
            | "Per my last email" | Says *you didn't read it* | "Quick reminder" |
            | "As I've already mentioned twice" | Keeps score | (dropped) |
            | "Please advise if this is somehow unclear" | Implies they're slow | "Let me know if anything is blocking you" |

            If it's the third reminder and you need it to land, add one line: *"If Friday won't work, tell me by Wednesday so I can plan around it."*
        """.trimIndent(),
    ),
)

private val SnackBudget = listOf(
    SampleTurn(
        ask = "Our team's AI agent keeps ordering Dubai chocolate for the office kitchen. Draft a monthly snack " +
            "budget for 15 people that keeps everyone happy.",
        answer = """
            Here's a ${'$'}300 a month plan for 15 people, with chocolate as a **treat**, not a food group:

            | Category | Monthly | Notes |
            |---|---|---|
            | Fruit | ${'$'}80 | Bananas, apples, clementines; restocked weekly |
            | Savoury snacks | ${'$'}70 | Nuts, crackers, popcorn |
            | Drinks | ${'$'}60 | Sparkling water, tea, oat milk |
            | Healthy-ish | ${'$'}40 | Granola bars, yogurt |
            | **Friday treat** | ${'$'}50 | One rotating treat; Dubai chocolate is allowed here |

            And three rules for the agent:
            1. **Stay within each category's budget.** No borrowing from fruit to fund chocolate.
            2. **One Friday treat a week**, chosen from a list the team votes on.
            3. **Ask before any order over ${'$'}75.**
        """.trimIndent(),
    ),
)

private val OverdueFormula = listOf(
    SampleTurn(
        ask = "Spreadsheet formula: flag invoices that are more than 30 days past their due date and still unpaid. " +
            "Due date is in column C, paid date in D.",
        answer = """
            Put this in **E2** and fill it down:

            ```
            =IF(AND(D2="", TODAY() - C2 > 30), "Overdue", "")
            ```

            - `D2=""`: the paid date is empty, so it's still unpaid.
            - `TODAY() - C2 > 30`: more than 30 days past due.

            To see *how* late each one is, use this instead:

            ```
            =IF(D2="", MAX(0, TODAY() - C2) & " days", "Paid")
            ```

            Then add conditional formatting on column E so "Overdue" turns red. It works the same in Excel and Google Sheets.
        """.trimIndent(),
    ),
)

private val OutOfOffice = listOf(
    SampleTurn(
        ask = "Write an out-of-office reply for a week off that isn't boring. Keep it professional enough for clients.",
        answer = """
            Three options, from safest to boldest:

            **1. Warm**
            > Thanks for your note! I'm away until Monday the 14th and not checking email. For anything urgent, Priya (priya@acme.dev) can help. Otherwise, I'll reply when I'm back.

            **2. A little personality**
            > I'm out of the office until the 14th, somewhere with bad Wi-Fi on purpose. Priya (priya@acme.dev) is covering anything urgent. Everything else will get my full attention when I'm back.

            **3. Bold (know your clients)**
            > I'm away until the 14th. Your email is safe with me, and it will be read by a well-rested person. Urgent? Priya (priya@acme.dev) is the hero you need.
        """.trimIndent(),
    ),
)

private val DeclineMeeting = listOf(
    SampleTurn(
        ask = "How do I decline a meeting without sounding rude?",
        answer = """
            Say no to the *meeting*, not the person, and offer something smaller:

            - **When you're not needed:** *"I don't think I'd add much here. Could you send me the notes afterwards?"*
            - **When you're busy:** *"I'm heads-down on the launch this week. Could I give my input async by Thursday?"*
            - **When it could be an email:** *"Happy to help. Could we try this over email first, and meet if we get stuck?"*

            Decline early, give a reason in one line, and always offer an alternative. That's all it takes to sound helpful instead of absent.
        """.trimIndent(),
    ),
)

private val OffsiteAgenda = listOf(
    SampleTurn(
        ask = "Plan a one-day team offsite for 12 people. We want to actually talk to each other, not sit through slides.",
        thinking = """
            Twelve people, one day, conversation over presentations. Front-load energy, keep sessions under an
            hour, put the hardest discussion before lunch, and leave the afternoon for something hands-on. End
            on time.
        """.trimIndent(),
        answer = """
            Here's a day with **no slide decks** and plenty of room to talk:

            | Time | What | Why |
            |---|---|---|
            | 9:30 | Coffee and pastries | People arrive at different times anyway |
            | 10:00 | Two truths and a work lie | A quick warm-up that gets everyone talking |
            | 10:30 | "What should we stop doing?" in groups of 4 | The hard conversation, while energy is high |
            | 11:30 | Groups share, team votes on top 3 | Turns complaints into decisions |
            | 12:30 | Long lunch, no agenda | Where the real offsite happens |
            | 2:00 | Something hands-on: a cooking class or an escape room | Teams that build together talk better |
            | 4:00 | One commitment each, said out loud | Something to point back to next month |
            | 4:30 | Done | Ending on time is the best gift |

            Ask everyone to bring **one thing they'd change** about how the team works. It makes the 10:30 session much easier to start.
        """.trimIndent(),
    ),
)

private val Icebreakers = listOf(
    SampleTurn(
        ask = "Icebreakers that aren't awful. For adults who've worked together for a while.",
        answer = """
            1. **Worst job you've ever had.** Everyone has a story, and they're always good.
            2. **Two truths and a work lie.** Guessing which project never happened is surprisingly fun.
            3. **The desert island app.** One app on your phone forever. Defend it.
            4. **Hidden expert.** Something you could give a 5-minute talk on with no notes. (It's rarely work.)
            5. **Unpopular opinion**, food only. Keeps it light; someone always defends pineapple on pizza.

            Skip anything with trust falls or the word "synergy".
        """.trimIndent(),
    ),
)

private val SlideTitles = listOf(
    SampleTurn(
        ask = "Make these slide titles less boring: \"Q3 Results\", \"Challenges\", \"Next Steps\".",
        answer = """
            A slide title works harder when it says the point, not the topic:

            | Before | After |
            |---|---|
            | Q3 Results | Sign-ups grew 18% while costs stayed flat |
            | Challenges | Onboarding is where we lose people |
            | Next Steps | Three fixes to onboarding by November |

            Swap in your real numbers. If someone reads only the titles, they should still get the story.
        """.trimIndent(),
    ),
)

// ─── Family: a home server running local models ───────────────────────

private val BedtimeStory = listOf(
    SampleTurn(
        ask = "Bedtime story: Mayor Whiskers and the lighthouse that went dark. Five minutes to read, gentle ending.",
        answer = """
            In a small town by the sea lived a mayor who was also a cat. His name was **Mayor Whiskers**, and he wore a tiny top hat and a sash that said MAYOR in gold letters.

            Every night, the lighthouse at the end of the harbour shone its big, warm light across the water, so the fishing boats could find their way home.

            But one evening, the light didn't come on.

            "Oh dear," said Mayor Whiskers, and his whiskers twitched. "The boats will be coming home soon."

            He trotted down the harbour, past the sleeping fish market and the bakery that smelled of tomorrow's bread, all the way to the lighthouse. Inside, he climbed one hundred and twelve stairs. (He counted. Mayors like to count things.)

            At the top, he found the lighthouse keeper, an old seagull named Gus, fast asleep beside the lamp.

            "Gus," whispered Mayor Whiskers. "The light."

            Gus woke with a start. "I only closed my eyes for a minute!"

            Together they turned the big brass handle, and the light flickered, then shone, round and round, across the dark water. Far out at sea, one little boat, then another, turned toward home.

            Gus felt terrible. But Mayor Whiskers just curled up beside him in the warm glow.

            "Everyone needs a nap sometimes," he said. "That's why we keep each other company."

            And the two of them watched the boats come home, one by one, until every last one was safe in the harbour.

            *The end.*
        """.trimIndent(),
    ),
)

private val SkyBlue = listOf(
    SampleTurn(
        ask = "Why is the sky blue? Explain it to a 7-year-old.",
        thinking = """
            Rayleigh scattering, without the name. Sunlight is all the colours mixed; air bounces blue light around
            much more than red. Use a picture a child already knows, a ball bouncing, and explain sunsets too,
            because that's always the next question.
        """.trimIndent(),
        answer = """
            Sunlight looks white, but it's really **all the colours of the rainbow mixed together**.

            When sunlight comes down from space, it bumps into the air. The air is made of tiny, tiny bits, far too small to see. Blue light is very bouncy: when it hits those tiny bits, it **bounces all over the sky**. Red and yellow light mostly go straight through.

            So when you look up, you see blue light that has bounced around everywhere. The whole sky glows blue!

            **And sunsets?** In the evening, sunlight has to travel through much more air to reach you. By the time it gets here, most of the blue has bounced away somewhere else, and the reds and oranges are what's left.
        """.trimIndent(),
    ),
)

private val FridgeDinner = listOf(
    SampleTurn(
        ask = "What can I make for dinner with eggs, spinach, half an onion, feta and some leftover rice? Kids are hungry.",
        answer = """
            Everything you need is already in the fridge. Three options, fastest first:

            1. **Spinach and feta fried rice** (15 minutes). Fry the onion, add the rice until it crisps, stir in the spinach, push it aside and scramble 3 eggs, then crumble feta on top.
            2. **Rice frittata** (25 minutes). Whisk 6 eggs with the rice, wilted spinach, onion and feta. Cook in an oven-safe pan until the edges set, then 10 minutes in the oven. Cuts into wedges kids can hold.
            3. **Spanakopita-ish egg muffins** (25 minutes). Same filling, baked in a muffin tin. Good for lunchboxes tomorrow too.

            For picky eaters: make the fried rice, and serve the spinach on the side for anyone who objects to green things.
        """.trimIndent(),
    ),
)

private val ChoreChart = listOf(
    SampleTurn(
        ask = "Chore chart for two kids, 8 and 11. The robot vacuum wants a row too.",
        thinking = """
            Age-appropriate jobs: the 8-year-old gets quick, visible jobs; the 11-year-old can do kitchen work.
            Alternate the shared ones so nobody gets stuck with the worst job. Give the robot vacuum a row as a joke,
            with one rule about socks.
        """.trimIndent(),
        answer = """
            | Chore | Maya (8) | Leo (11) | Robot vacuum |
            |---|---|---|---|
            | Make bed | Daily | Daily | n/a |
            | Set and clear the table | Mon, Wed, Fri | Tue, Thu, Sat | n/a |
            | Feed the cat | Mornings | Evenings | Do not feed the cat |
            | Load the dishwasher | n/a | Daily | n/a |
            | Tidy the floor before vacuuming | Daily | Daily | n/a |
            | Vacuum | n/a | n/a | Daily, 10 am |
            | Take out recycling | n/a | Sundays | n/a |

            **House rules**
            - Floors get tidied **before** the vacuum runs, so it doesn't eat any more socks.
            - A full week earns the weekend's movie pick.
            - The robot vacuum gets no allowance. It knows what it did.
        """.trimIndent(),
    ),
)

private val BirthdayParty = listOf(
    SampleTurn(
        ask = "Plan a 9-year-old's birthday party at home: 10 kids, two hours, and I'd like to survive it.",
        answer = """
            Two hours goes fast with a plan. Here's a run sheet:

            | Time | Activity |
            |---|---|
            | 0:00 | Arrivals: a drawing table, so early kids have something to do |
            | 0:15 | Scavenger hunt around the house (prep the clues the night before) |
            | 0:45 | Pizza |
            | 1:10 | Cake and singing |
            | 1:25 | Freeze dance or musical chairs, to burn off the sugar |
            | 1:50 | Goodie bags at the door |

            **Survival tips**
            - Recruit **one other adult**. Ten kids is fine with two grown-ups and chaos with one.
            - Put the cake candles and a lighter **somewhere you'll remember**.
            - End on time. Parents are grateful, and so are you.
        """.trimIndent(),
    ),
)

private val RoadTripSnacks = listOf(
    SampleTurn(
        ask = "Road trip snacks for a 6-hour drive with kids that won't melt in the car.",
        answer = """
            **Won't melt, won't make a mess:**
            - Pretzels, crackers and popcorn
            - Apple slices, grapes and clementines (pre-peeled)
            - Cheese sticks and mini babybels, in a cooler bag
            - Granola bars without a chocolate coating
            - Dried mango and raisins
            - Mini rice cakes

            **Leave at home:**
            - Anything with a chocolate coating. A dashboard in the sun melts a chocolate bar in about 13 minutes.
            - Powdered snacks (cheese dust gets *everywhere*).
            - Anything sticky that a 6-year-old will hold for an hour first.

            Pack each kid their own bag. Fewer arguments, and you know who ate all the grapes.
        """.trimIndent(),
    ),
)

private val CarGames = listOf(
    SampleTurn(
        ask = "Car games for a long drive. No screens.",
        answer = """
            1. **The alphabet game.** Find each letter, A to Z, on signs and licence plates. Q is the hard one.
            2. **20 questions.** One person thinks of something, everyone else gets 20 yes-or-no questions.
            3. **Would you rather.** Endless, and you learn surprising things about your kids.
            4. **Story chain.** Each person adds one sentence to the story. It always ends up somewhere strange.
            5. **I spy**, for the little ones, and the patience of the grown-ups.
            6. **The quiet game**, for the last hour. Winner picks the first stop.
        """.trimIndent(),
    ),
)

// ─── Accounts ─────────────────────────────────────────────────────────

/** Each account's chats and projects, by account id ("personal", "work" or "family"). */
internal val AccountSamples: Map<String, SampleAccount> = mapOf(
    "personal" to SampleAccount(
        chats = listOf(
            SampleChat("chocolate-bars", "My agent bought 40 chocolate bars", ClaudeOpus, 20.minutes, pinned = true, project = "agent", turns = ChocolateBars),
            SampleChat("dessert-table", "Dessert table for Saturday", ClaudeSonnet, 5.minutes, turns = emptyList(), asking = DessertTable),
            SampleChat("stash", "Chocolate stash tracker app", ClaudeSonnet, 45.minutes, project = "agent", turns = StashTracker),
            SampleChat("car-wash", "Walk or drive to the car wash?", ClaudeOpus, 1.hours, turns = CarWash),
            SampleChat("desserts", "Viral desserts, ranked", ClaudeOpus, 3.hours, favorite = true, turns = ViralDesserts),
            SampleChat("game-night", "Groceries for game night", ClaudeSonnet, 5.hours, unread = true, project = "agent", turns = GameNight),
            SampleChat("vacuum-review", "Performance review: robot vacuum", ClaudeHaiku, 26.hours, turns = VacuumReview),
            SampleChat("vacuum-pet", "Is a robot vacuum a pet?", ClaudeHaiku, 2.days, turns = VacuumPet),
            SampleChat("agent-rules", "Teach my agent to say no", ClaudeHaiku, 3.days, project = "agent", turns = AgentRules),
            SampleChat("shopping-rules", "Flowchart: my agent's shopping rules", ClaudeSonnet, 3.days + 1.hours, project = "agent", turns = ShoppingRules),
            SampleChat("pistachio", "Pistachio cream substitutes", ClaudeHaiku, 4.days, favorite = true, turns = PistachioSubstitutes),
            SampleChat("melted", "Is my chocolate supposed to look like this?", ClaudeOpus, 6.days, turns = MeltedBar),
            SampleChat("hot-car", "How long chocolate lasts in a hot car", Gpt5, 6.days + 2.hours, turns = HotCar),
            SampleChat("haiku", "Haiku for a tired Monday", ClaudeHaiku, 9.days, turns = MondayHaiku),
            SampleChat("whiskers-speech", "Mayor Whiskers' re-election speech", ClaudeSonnet, 12.days, project = "whiskers", turns = WhiskersSpeech),
            SampleChat("whiskers-slogans", "Campaign slogans for a cat", Gpt51, 15.days, project = "whiskers", turns = WhiskersSlogans),
        ),
        projects = listOf(
            project("agent", "Agent wrangling", "Rules, receipts and refunds for my shopping agent"),
            project("whiskers", "Mayor Whiskers", "Speeches and campaign material for the town's cat mayor"),
        ),
    ),
    "work" to SampleAccount(
        chats = listOf(
            SampleChat("email", "Make this email less passive-aggressive", ClaudeSonnet, 35.minutes, pinned = true, turns = PoliteEmail),
            SampleChat("snack-budget", "Office snack budget", Gpt51, 2.hours, unread = true, turns = SnackBudget),
            SampleChat("overdue", "Formula for overdue invoices", Gpt5, 6.hours, favorite = true, turns = OverdueFormula),
            SampleChat("ooo", "An out-of-office that isn't boring", GeminiPro, 27.hours, turns = OutOfOffice),
            SampleChat("decline", "Declining a meeting politely", ClaudeSonnet, 3.days, turns = DeclineMeeting),
            SampleChat("offsite", "One-day offsite agenda", ClaudeOpus, 5.days, project = "offsite", turns = OffsiteAgenda),
            SampleChat("icebreakers", "Icebreakers that aren't awful", Gpt5Mini, 6.days, project = "offsite", turns = Icebreakers),
            SampleChat("slides", "Better slide titles", Gpt51, 10.days, turns = SlideTitles),
        ),
        projects = listOf(
            project("offsite", "Team offsite", "Agenda and activities for the October offsite"),
        ),
    ),
    "family" to SampleAccount(
        chats = listOf(
            SampleChat("bedtime", "Mayor Whiskers and the dark lighthouse", Llama, 50.minutes, pinned = true, turns = BedtimeStory),
            SampleChat("solar", "Solar system for Maya's science fair", Qwen, 90.minutes, favorite = true, turns = SolarSystem),
            SampleChat("sky", "Why is the sky blue?", Qwen, 3.hours, turns = SkyBlue),
            SampleChat("dinner", "Dinner from what's in the fridge", Llama, 7.hours, unread = true, turns = FridgeDinner),
            SampleChat("chores", "Chore chart for the kids", Qwen, 2.days, favorite = true, turns = ChoreChart),
            SampleChat("party", "Birthday party run sheet", Llama, 4.days, turns = BirthdayParty),
            SampleChat("snacks", "Road trip snacks that won't melt", MistralSmall, 8.days, project = "trip", turns = RoadTripSnacks),
            SampleChat("car-games", "Car games, no screens", Qwen, 9.days, project = "trip", turns = CarGames),
        ),
        projects = listOf(
            project("trip", "Summer road trip", "Snacks, stops and games for the drive to the coast"),
        ),
    ),
)
