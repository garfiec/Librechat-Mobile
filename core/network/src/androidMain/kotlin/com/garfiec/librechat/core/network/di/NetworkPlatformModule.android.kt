package com.garfiec.librechat.core.network.di

import com.garfiec.librechat.core.common.di.KoinQualifiers
import com.garfiec.librechat.core.network.client.CertificateTrustListener
import com.garfiec.librechat.core.network.client.CertificateTrustOkHttp
import com.garfiec.librechat.core.network.sse.SseHttpTransport
import io.ktor.client.engine.HttpClientEngineFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual val networkPlatformModule: Module = module {
    single<HttpClientEngineFactory<*>> { CertificateTrustOkHttp(CertificateTrustListener(get())) }
    single { SseHttpTransport(get(KoinQualifiers.Streaming)) }
}
