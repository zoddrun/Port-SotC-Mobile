package com.portsotc.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAchievementLedgerTest {

    private val satisfiedState = LocalAchievementState(
        supportedDiscImported = true,
        discImageVerified = true,
        boot2HashRecorded = true,
        discTreeReady = true,
        runtimeInstalled = true,
        vaultBackupCreated = true,
        nativeReportCached = true
    )

    @Test
    fun unlocksOnlyKnownMilestonesOnce() {
        val ledger = LocalAchievementLedger.empty()

        assertTrue(ledger.unlock(LocalAchievements.DISC_IMPORTED.id, 100L))
        assertFalse(ledger.unlock(LocalAchievements.DISC_IMPORTED.id, 200L))
        assertFalse(ledger.unlock("unknown_milestone", 300L))
        assertEquals(100L, ledger.unlockedAt(LocalAchievements.DISC_IMPORTED.id))
        assertEquals(setOf(LocalAchievements.DISC_IMPORTED.id), ledger.unlockedIds())
    }

    @Test
    fun syncUnlocksEverySatisfiedConditionOnlyOnTheFirstPass() {
        val ledger = LocalAchievementLedger.empty()

        val first = ledger.sync(satisfiedState, 1_000L)
        val second = ledger.sync(satisfiedState, 2_000L)

        assertEquals(LocalAchievements.ALL.size, first.size)
        assertTrue(second.isEmpty())
        assertEquals(LocalAchievements.ALL.map { it.id }.toSet(), ledger.unlockedIds())
        assertTrue(ledger.unlockedIds().all { ledger.unlockedAt(it) == 1_000L })
    }

    @Test
    fun syncHonorsIndividualConditions() {
        val ledger = LocalAchievementLedger.empty()
        val state = LocalAchievementState(
            supportedDiscImported = true,
            discImageVerified = false,
            boot2HashRecorded = false,
            discTreeReady = false,
            runtimeInstalled = false,
            vaultBackupCreated = false,
            nativeReportCached = false
        )

        val unlocked = ledger.sync(state, 500L)

        assertEquals(listOf(LocalAchievements.DISC_IMPORTED.id), unlocked.map { it.id })
        assertFalse(ledger.isUnlocked(LocalAchievements.RUNTIME_PRESENT.id))
    }

    @Test
    fun restoresOnlyValidStoredMilestones() {
        val ledger = LocalAchievementLedger.from(
            mapOf(
                LocalAchievements.BOOT2_IDENTIFIED.id to 10L,
                "invalid" to 20L,
                LocalAchievements.VAULT_BACKUP.id to 0L
            )
        )

        assertEquals(setOf(LocalAchievements.BOOT2_IDENTIFIED.id), ledger.unlockedIds())
        assertEquals(10L, ledger.unlockedAt(LocalAchievements.BOOT2_IDENTIFIED.id))
        assertNull(ledger.unlockedAt(LocalAchievements.VAULT_BACKUP.id))
    }
}
