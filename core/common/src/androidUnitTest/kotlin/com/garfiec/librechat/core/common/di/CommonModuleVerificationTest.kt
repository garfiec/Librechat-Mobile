package com.garfiec.librechat.core.common.di

import android.app.Application
import android.content.Context
import com.garfiec.librechat.core.common.ChannelPolicy
import com.garfiec.librechat.core.common.DistributionChannel
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

class CommonModuleVerificationTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun verifyCommonModule() {
        commonModule.verify(
            extraTypes = listOf(
                Context::class,
                Application::class,
            ),
            // Exempt for ChannelPolicy alone: DistributionChannel is deliberately not in the graph,
            // so a definition that injects it must still fail here.
            injections = injectedParameters(definition<ChannelPolicy>(DistributionChannel::class)),
        )
    }
}
