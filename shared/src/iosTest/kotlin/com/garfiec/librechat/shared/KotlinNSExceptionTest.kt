package com.garfiec.librechat.shared

import platform.Foundation.NSNumber
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.getStackTraceAddresses
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The crash hook's NSException must carry the Kotlin throw site, or Apple's crash report shows only
 * the hook. This breaks if a newer iOS stops storing the stack in NSException's `reserved` field.
 */
class KotlinNSExceptionTest {

    @OptIn(ExperimentalNativeApi::class)
    @Test
    fun the_exception_reports_the_kotlin_throw_site() {
        val throwable = IllegalStateException("boom")
        val exception = kotlinNSException(throwable, "kotlin.IllegalStateException", "boom")

        val reported = exception.callStackReturnAddresses.map { (it as NSNumber).unsignedLongLongValue.toLong() }
        assertEquals(throwable.getStackTraceAddresses(), reported)
        assertEquals("KotlinException: kotlin.IllegalStateException", exception.name)
        assertEquals("boom", exception.reason)
    }
}
