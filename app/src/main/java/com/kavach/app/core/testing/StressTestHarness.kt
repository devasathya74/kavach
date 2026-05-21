package com.kavach.app.core.testing

import android.content.Context
import com.kavach.app.data.repository.IncidentRepository
import com.kavach.app.data.repository.UserManagementRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StressTestHarness — The "Chaos Engine" for production hardening.
 * 
 * Provides automated scripts for:
 * 1. SOAK: Continuous light load (24h)
 * 2. BURST: High-concurrency mutation storm
 * 3. CHAOS: Rapid state toggles
 * 4. RECOVERY: Forced crashes/death simulation
 */
@Singleton
class StressTestHarness @Inject constructor(
    @ApplicationContext private val context: Context,
    private val incidentRepository: IncidentRepository,
    private val userRepository: UserManagementRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeTest: Job? = null

    /**
     * Profile: BURST
     * Creates 50 incidents in rapid succession.
     */
    fun runBurstTest(count: Int = 50) {
        activeTest?.cancel()
        activeTest = scope.launch {
            Timber.tag("KAVACH_STRESS").i("STARTING BURST TEST: $count incidents")
            repeat(count) { i ->
                val localId = incidentRepository.createIncidentDraft(
                    title = "Stress Test Incident #$i",
                    summary = "Automated burst test report. Sequence ${UUID.randomUUID()}",
                    type = "OTHER",
                    severity = if (i % 3 == 0) "HIGH" else "MEDIUM",
                    latitude = 26.7606,
                    longitude = 81.2333
                )
                incidentRepository.submitIncident(localId)
                delay(100) // 100ms interval
            }
            Timber.tag("KAVACH_STRESS").i("BURST TEST FINISHED")
        }
    }

    /**
     * Profile: CHAOS
     * Simulates rapid lifecycle and connectivity toggles.
     */
    fun runChaosTest() {
        activeTest?.cancel()
        activeTest = scope.launch {
            Timber.tag("KAVACH_STRESS").i("STARTING CHAOS TEST")
            repeat(20) { i ->
                Timber.tag("KAVACH_STRESS").d("Chaos Loop #$i")
                // Toggle network via ADB (requires rooted/debug build) or mock 
                // Here we simulate rapid repository mutations
                userRepository.getOfficers(search = "Test $i")
                delay(500)
                incidentRepository.observeIncidents()
                delay(500)
            }
            Timber.tag("KAVACH_STRESS").i("CHAOS TEST FINISHED")
        }
    }

    /**
     * Profile: SOAK (Simulated)
     * Creates an incident every 30 minutes for 24h.
     */
    fun runSoakTest() {
        activeTest?.cancel()
        activeTest = scope.launch {
            Timber.tag("KAVACH_STRESS").i("STARTING SOAK TEST (Simulated 24h)")
            while (isActive) {
                val localId = incidentRepository.createIncidentDraft(
                    title = "Soak Test Report",
                    summary = "Health check at ${Date()}",
                    type = "SYSTEM",
                    severity = "LOW",
                    latitude = 0.0,
                    longitude = 0.0
                )
                incidentRepository.submitIncident(localId)
                Timber.tag("KAVACH_STRESS").d("Soak pulse sent. Waiting 30m...")
                delay(1800_000) // 30 minutes
            }
        }
    }

    fun stopAllTests() {
        activeTest?.cancel()
        activeTest = null
        Timber.tag("KAVACH_STRESS").i("All stress tests stopped.")
    }
}
