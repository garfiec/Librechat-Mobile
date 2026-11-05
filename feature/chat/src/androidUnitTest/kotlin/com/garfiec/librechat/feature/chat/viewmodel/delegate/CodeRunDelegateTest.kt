package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.CodeRunRepository
import com.garfiec.librechat.core.model.code.CodeRunRequest
import com.garfiec.librechat.core.model.code.CodeRunResult
import com.garfiec.librechat.feature.chat.components.CodeRunStatus
import com.garfiec.librechat.feature.chat.components.CodeRunTarget
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CodeRunDelegateTest {

    private class FakeRuns : CodeRunRepository {
        val requests = mutableListOf<CodeRunRequest>()
        var stored: Result<Map<String, List<CodeRunResult>>> = Result.Success(emptyMap())
        var runsCalls = 0
        var answer: CompletableDeferred<Result<CodeRunResult>>? = null
        var next: Result<CodeRunResult> = Result.Success(CodeRunResult("ok", emptyList()))

        override suspend fun run(request: CodeRunRequest): Result<CodeRunResult> {
            requests += request
            return answer?.await() ?: next
        }

        override suspend fun runs(conversationId: String): Result<Map<String, List<CodeRunResult>>> {
            runsCalls++
            return stored
        }
    }

    private val target = CodeRunTarget(messageId = "m1", partIndex = 2, blockIndex = 1, lang = "py", code = "print(1)")

    private fun TestScope.delegate(
        repo: FakeRuns,
        conversation: () -> String? = { "c1" },
        failures: MutableList<Unit> = mutableListOf(),
    ) = CodeRunDelegate(
        scope = this,
        repository = repo,
        conversationId = conversation,
        isTemporary = { true },
        onRunFailed = { failures += Unit },
    )

    @Test
    fun `a run sends the block and shows Running, then Complete, then Run again`() = runTest {
        val repo = FakeRuns().apply { answer = CompletableDeferred() }
        val delegate = delegate(repo)

        delegate.run(target)
        runCurrent()
        assertThat(delegate.state.value.status[target.key]).isEqualTo(CodeRunStatus.RUNNING)
        assertThat(repo.requests.single()).isEqualTo(
            CodeRunRequest(
                messageId = "m1",
                conversationId = "c1",
                blockIndex = 1,
                lang = "py",
                code = "print(1)",
                partIndex = 2,
                isTemporary = true,
            ),
        )

        // A second tap while it runs sends nothing.
        delegate.run(target)
        repo.answer!!.complete(Result.Success(CodeRunResult("1\n", emptyList())))
        runCurrent()
        assertThat(repo.requests).hasSize(1)
        assertThat(delegate.state.value.status[target.key]).isEqualTo(CodeRunStatus.COMPLETE)
        assertThat(delegate.state.value.results[target.key]?.map { it.output }).containsExactly("1\n")

        advanceTimeBy(1_501)
        assertThat(delegate.state.value.status[target.key]).isNull()
    }

    @Test
    fun `a failed run says so once and shows Failed for two seconds`() = runTest {
        val repo = FakeRuns().apply { next = Result.Error(message = "Forbidden") }
        val failures = mutableListOf<Unit>()
        val delegate = delegate(repo, failures = failures)

        delegate.run(target)
        runCurrent()

        assertThat(failures).hasSize(1)
        assertThat(delegate.state.value.status[target.key]).isEqualTo(CodeRunStatus.FAILED)
        assertThat(delegate.state.value.results[target.key]).isNull()
        advanceTimeBy(1_600)
        assertThat(delegate.state.value.status[target.key]).isEqualTo(CodeRunStatus.FAILED)
        advanceTimeBy(500)
        assertThat(delegate.state.value.status[target.key]).isNull()
    }

    @Test
    fun `stored runs are read once per conversation and a new run appends`() = runTest {
        val earlier = CodeRunResult("earlier", emptyList())
        val repo = FakeRuns().apply { stored = Result.Success(mapOf(target.key to listOf(earlier))) }
        val delegate = delegate(repo)

        delegate.ensureLoaded()
        delegate.ensureLoaded()
        runCurrent()
        assertThat(repo.runsCalls).isEqualTo(1)

        delegate.run(target)
        runCurrent()
        assertThat(delegate.state.value.results[target.key]?.map { it.output }).containsExactly("earlier", "ok").inOrder()
    }

    @Test
    fun `a failed read is asked again the next time a block shows`() = runTest {
        val repo = FakeRuns().apply { stored = Result.Error(message = "offline") }
        val delegate = delegate(repo)

        delegate.ensureLoaded()
        runCurrent()
        delegate.ensureLoaded()
        runCurrent()

        assertThat(repo.runsCalls).isEqualTo(2)
    }

    @Test
    fun `another conversation starts empty`() = runTest {
        var conversation = "c1"
        val repo = FakeRuns()
        val delegate = delegate(repo, conversation = { conversation })
        delegate.run(target)
        runCurrent()
        assertThat(delegate.state.value.results).isNotEmpty()

        conversation = "c2"
        delegate.ensureLoaded()
        runCurrent()

        assertThat(delegate.state.value.results).isEmpty()
        assertThat(repo.runsCalls).isEqualTo(1)
    }

    @Test
    fun `nothing runs before the chat has a conversation`() = runTest {
        val repo = FakeRuns()
        val delegate = delegate(repo, conversation = { null })

        delegate.run(target)
        delegate.ensureLoaded()
        runCurrent()

        assertThat(repo.requests).isEmpty()
        assertThat(repo.runsCalls).isEqualTo(0)
    }
}
