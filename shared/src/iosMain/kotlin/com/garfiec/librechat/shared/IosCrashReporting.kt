package com.garfiec.librechat.shared

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.logging.PersistentLogWriter
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSException
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.numberWithUnsignedLongLong
import platform.Foundation.setValue
import platform.objc.class_getInstanceVariable
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.getStackTraceAddresses

private val log = Logger.withTag("CrashReporting")

/**
 * Installs a Kotlin/Native unhandled exception hook that:
 * 1. Writes a synchronous crash record via [persistentWriter] (best-effort; the process may die
 *    before any async log drain runs, so this path is intentionally synchronous).
 * 2. Logs the full Kotlin stack trace via Kermit + NSLog (visible in Xcode/Console.app)
 * 3. Raises the Kotlin exception as an NSException carrying its class, message and Kotlin stack
 *    ([kotlinNSException]), so Apple's crash report shows where it was thrown.
 *
 * Call once during app startup. [persistentWriter] may be null if Koin failed to start or the
 * writer could not be resolved — the hook is still installed for stack-trace/NSException behavior.
 */
@OptIn(ExperimentalNativeApi::class)
fun installCrashReporting(persistentWriter: PersistentLogWriter? = null) {
    setUnhandledExceptionHook { throwable ->
        // Persist a crash record first, synchronously, before the process tears down. Guarded so a
        // logging failure can never mask the original crash or recurse.
        runCatching {
            persistentWriter?.writeCrashRecord(
                tag = "Crash",
                message = "unhandled Kotlin exception",
                throwable = throwable,
            )
        }

        // Log the full Kotlin stack trace so it's captured in OS logs
        val stackTrace = throwable.stackTraceToString()
        val exceptionName = throwable::class.qualifiedName ?: throwable::class.simpleName ?: "Unknown"
        val message = throwable.message ?: "(no message)"

        // Guarded like the crash record: a throw here would skip the NSException below.
        runCatching {
            log.e { "FATAL: Unhandled Kotlin exception: $exceptionName: $message" }
            log.e { stackTrace }
        }

        // Also write directly to NSLog in case Kermit writers aren't flushed
        runCatching {
            nsLogVerbatim("FATAL: Unhandled Kotlin exception: $exceptionName: $message")
            nsLogVerbatim("Stack trace:\n$stackTrace")
        }

        // Raise as NSException so iOS crash reporters capture a meaningful report
        // instead of a bare SIGABRT with no context
        kotlinNSException(throwable, exceptionName, message).raise()
    }
    log.d { "iOS crash reporting hook installed" }
}

/**
 * An NSException whose "Last Exception Backtrace" in Apple's crash report is where [throwable] was
 * thrown, not this hook.
 *
 * CoreFoundation records the stack at `raise` only when the exception's private `reserved`
 * dictionary has no `callStackReturnAddresses` yet; overriding the public getter is ignored. The
 * ivar is checked first because a KVC miss would throw an Objective-C exception Kotlin can't catch.
 */
@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
internal fun kotlinNSException(throwable: Throwable, exceptionName: String, message: String): NSException {
    val exception = NSException(name = "KotlinException: $exceptionName", reason = message, userInfo = null)
    if (class_getInstanceVariable(NSException.`class`(), "reserved") != null) {
        val addresses = throwable.getStackTraceAddresses().map { NSNumber.numberWithUnsignedLongLong(it.toULong()) }
        exception.setValue(
            NSMutableDictionary().apply { setValue(addresses, forKey = "callStackReturnAddresses") },
            forKey = "reserved",
        )
    }
    return exception
}
