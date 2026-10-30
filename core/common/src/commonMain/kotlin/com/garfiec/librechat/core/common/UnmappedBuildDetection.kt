package com.garfiec.librechat.core.common

import com.garfiec.librechat.core.common.generated.BackendCommitMap

/**
 * Places a server whose `buildInfo.commit` is not in [BackendCommitMap], which is every server
 * upgraded past the commit the app last synced to.
 *
 * Two sources, in order:
 * - **`buildInfo.branch` naming a version tag.** Upstream's image workflow sets `BUILD_BRANCH` to
 *   the git ref name, so a tag image reports e.g. `v0.8.9-rc1`. A tag build's package.json is exact,
 *   so it classifies as [BackendBuildClass.OFFICIAL] / [BackendBuildClass.RC] like a mapped tag commit.
 * - **`buildInfo.buildDate` after [BackendCommitMap.PINNED_COMMIT]'s commit date.** A build from a
 *   branch (`main`, `dev`) made after our pin is placed AT the pin: [BackendBuildClass.DEV] with the
 *   pin's version and NO commit date. That is a lower bound — it turns on what the app was synced
 *   against. Its commit is unknown, so a date gate can't place it either way: a gate above the pin's
 *   version reads UNKNOWN (probe-able), never ABSENT. A fork rebuilding older code today would
 *   be over-placed; upstream images don't, and a server without `BUILD_DATE` is not placed at all.
 */
object UnmappedBuildDetection {
    private val versionTag = Regex("""^[vV]?\d+\.\d+\.\d+(-[0-9A-Za-z.]+)?$""")
    private val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun detect(branch: String?, buildDate: String?): DetectedBackend? {
        branch?.trim()?.takeIf { versionTag.matches(it) }?.let { tag ->
            val version = tag.trimStart('v', 'V')
            val parsed = BackendVersion.parse(version) ?: return@let
            val classification = if (parsed.preRelease == null) BackendBuildClass.OFFICIAL else BackendBuildClass.RC
            return DetectedBackend(version = version, classification = classification)
        }

        val built = buildDate?.trim()?.take(10)?.takeIf { isoDate.matches(it) } ?: return null
        val pinnedVersion = BackendCommitMap.versionForCommit(BackendCommitMap.PINNED_COMMIT) ?: return null
        val pinnedDate = BackendCommitMap.dateForCommit(BackendCommitMap.PINNED_COMMIT) ?: return null
        if (built <= pinnedDate) return null
        return DetectedBackend(version = pinnedVersion, classification = BackendBuildClass.DEV)
    }
}
