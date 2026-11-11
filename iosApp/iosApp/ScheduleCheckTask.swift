import BackgroundTasks
import Shared

/// Bridges the schedule check's background refresh task to the shared `ScheduledRunWatcher`.
///
/// Only the launch handler lives here, because only the app target can install it before launch
/// finishes. Submitting the request, for the server's next run time, stays on the Kotlin side.
enum ScheduleCheckTask {

    /// Must run once, from `iOSApp.init()` before `startIosKoin()`, for the same reasons as
    /// `PrefetchBackgroundTasks.register()`: Koin's eager watcher submits as soon as it can.
    static func register() {
        let identifier = IosScheduleCheck.shared.ID
        let registered = BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            run(task)
        }
        guard registered else {
            assertionFailure("BGTaskScheduler refused \(identifier) — is it in BGTaskSchedulerPermittedIdentifiers?")
            NSLog("[W/Schedules] BGTaskScheduler refused registration for \(identifier)")
            return
        }
        IosScheduleCheck.shared.markLaunchHandlerRegistered()
    }

    private static func run(_ task: BGTask) {
        // Installed before the work starts so an immediate expiration still finds something to cancel.
        let holder = CancellableWork()
        task.expirationHandler = { holder.cancel() }
        holder.work = Task { @MainActor in
            // A background-task launch connects no scene, so init() cannot be assumed to have run.
            IosKoinHelperKt.startIosKoin()
            let finished = (try? await IosScheduleCheck.shared
                .run(budgetSeconds: IosScheduleCheck.shared.BUDGET_SECONDS))?.boolValue ?? false
            // iOS terminates the app for a task that never reports completion.
            task.setTaskCompleted(success: finished)
        }
    }
}
