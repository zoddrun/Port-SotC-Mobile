package com.portsotc.mobile.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Original, locally evaluated milestones.
 *
 * These are deliberately not Shadow of the Colossus trophies or
 * RetroAchievements. They track verifiable setup work performed by this app and
 * never claim that gameplay, rendering, audio or a save has been verified on a
 * physical device.
 */
enum class LocalAchievementCategory(val label: String) {
    DISC("DISC"),
    RUNTIME("RUNTIME"),
    STORAGE("STORAGE"),
    DIAGNOSTICS("DIAGNOSTICS")
}

data class LocalAchievement(
    val id: String,
    val title: String,
    val description: String,
    val category: LocalAchievementCategory
)

data class LocalAchievementState(
    val supportedDiscImported: Boolean,
    val discImageVerified: Boolean,
    val boot2HashRecorded: Boolean,
    val discTreeReady: Boolean,
    val runtimeInstalled: Boolean,
    val vaultBackupCreated: Boolean,
    val nativeReportCached: Boolean
)

data class UnlockedLocalAchievement(val id: String, val unlockedAt: Long)

object LocalAchievements {

    val DISC_IMPORTED = LocalAchievement(
        "disc_imported",
        "Supported Disc Imported",
        "A disc identifying as SCUS-97472 was imported into app-private storage.",
        LocalAchievementCategory.DISC
    )
    val DISC_VERIFIED = LocalAchievement(
        "disc_verified",
        "Image Digest Verified",
        "The imported image and its extracted boot files passed the local integrity checks.",
        LocalAchievementCategory.DISC
    )
    val BOOT2_IDENTIFIED = LocalAchievement(
        "boot2_identified",
        "BOOT2 Identified",
        "A disc inspection produced a boot executable identifier from SYSTEM.CNF and the ELF bytes.",
        LocalAchievementCategory.DISC
    )
    val TREE_PREPARED = LocalAchievement(
        "disc_tree_prepared",
        "Runtime Files Prepared",
        "The extracted disc tree required by the recompiled runtime was prepared.",
        LocalAchievementCategory.STORAGE
    )
    val RUNTIME_PRESENT = LocalAchievement(
        "runtime_present",
        "Native Runtime Packaged",
        "The statically recompiled ARM64 runner is present in this installation.",
        LocalAchievementCategory.RUNTIME
    )
    val VAULT_BACKUP = LocalAchievement(
        "vault_backup",
        "Memory Card Backed Up",
        "At least one app-private memory-card backup was created in the local vault.",
        LocalAchievementCategory.STORAGE
    )
    val NATIVE_REPORT = LocalAchievement(
        "native_report_cached",
        "Native Report Cached",
        "A diagnostics report from the native library was saved to app-private storage.",
        LocalAchievementCategory.DIAGNOSTICS
    )

    val ALL: List<LocalAchievement> = listOf(
        DISC_IMPORTED,
        DISC_VERIFIED,
        BOOT2_IDENTIFIED,
        TREE_PREPARED,
        RUNTIME_PRESENT,
        VAULT_BACKUP,
        NATIVE_REPORT
    )

    fun conditionMet(achievement: LocalAchievement, state: LocalAchievementState): Boolean =
        when (achievement.id) {
            DISC_IMPORTED.id -> state.supportedDiscImported
            DISC_VERIFIED.id -> state.discImageVerified
            BOOT2_IDENTIFIED.id -> state.boot2HashRecorded
            TREE_PREPARED.id -> state.discTreeReady
            RUNTIME_PRESENT.id -> state.runtimeInstalled
            VAULT_BACKUP.id -> state.vaultBackupCreated
            NATIVE_REPORT.id -> state.nativeReportCached
            else -> false
        }

    fun byId(id: String): LocalAchievement? = ALL.firstOrNull { it.id == id }

    fun isKnownId(id: String): Boolean = byId(id) != null
}

/**
 * Pure unlock ledger. Timestamps use the supplied clock so persistence and
 * evaluator logic can be tested without Android.
 */
class LocalAchievementLedger private constructor(
    private val unlocked: LinkedHashMap<String, Long>
) {

    fun unlockedIds(): Set<String> = unlocked.keys.toSet()

    fun unlockedAt(id: String): Long? = unlocked[id]

    fun isUnlocked(id: String): Boolean = unlocked.containsKey(id)

    fun unlock(id: String, now: Long): Boolean {
        if (!LocalAchievements.isKnownId(id) || isUnlocked(id)) return false
        unlocked[id] = now
        return true
    }

    /** Unlocks every satisfied milestone and returns only the new ones. */
    fun sync(state: LocalAchievementState, now: Long): List<LocalAchievement> {
        val newlyUnlocked = ArrayList<LocalAchievement>()
        LocalAchievements.ALL.forEach { achievement ->
            if (!isUnlocked(achievement.id) && LocalAchievements.conditionMet(achievement, state)) {
                if (unlock(achievement.id, now)) newlyUnlocked.add(achievement)
            }
        }
        return newlyUnlocked
    }

    companion object {
        fun empty(): LocalAchievementLedger = LocalAchievementLedger(LinkedHashMap())

        fun from(unlocked: Map<String, Long>): LocalAchievementLedger {
            val valid = LinkedHashMap<String, Long>()
            unlocked.forEach { (id, timestamp) ->
                if (LocalAchievements.isKnownId(id) && timestamp > 0L) valid[id] = timestamp
            }
            return LocalAchievementLedger(valid)
        }
    }
}

/** SharedPreferences adapter for [LocalAchievementLedger]. */
class LocalAchievementStore(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("sotc_local_achievements", Context.MODE_PRIVATE)

    fun load(): LocalAchievementLedger {
        val ids = preferences.getString(KEY_IDS, "") ?: ""
        val unlocked = LinkedHashMap<String, Long>()
        ids.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { id ->
            val timestamp = preferences.getLong("${KEY_UNLOCKED_AT}$id", 0L)
            if (timestamp > 0L) unlocked[id] = timestamp
        }
        return LocalAchievementLedger.from(unlocked)
    }

    fun save(ledger: LocalAchievementLedger) {
        val editor = preferences.edit()
        val ids = ledger.unlockedIds().sorted().joinToString(",")
        editor.putString(KEY_IDS, ids)
        ledger.unlockedIds().forEach { id ->
            ledger.unlockedAt(id)?.let { timestamp ->
                editor.putLong("${KEY_UNLOCKED_AT}$id", timestamp)
            }
        }
        editor.apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_UNLOCKED_AT = "unlocked_at."
    }
}
