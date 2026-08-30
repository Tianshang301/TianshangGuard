package com.tianshang.guard.core.update

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tianshang.guard.core.util.SecureLog
import java.util.concurrent.TimeUnit

/**
 * Schedules the (signature-verified) remote rule update so it is actually
 * reachable at runtime (audit C-01: the worker existed but was never enqueued,
 * leaving the signed update chain as dead code).
 *
 * - A periodic job refreshes rules daily (with a 30s initial backoff).
 * - A one-time job runs on app start so the latest rules are pulled promptly.
 * Both use unique names so they are never double-scheduled across restarts.
 */
object RuleUpdateScheduler {

    private const val PERIODIC_WORK_NAME = "rule_update_periodic"
    private const val ONE_TIME_WORK_NAME = "rule_update_boot"
    private const val PERIOD_HOURS = 24L

    fun schedule(context: Context) {
        try {
            val workManager = WorkManager.getInstance(context)

            val periodic = PeriodicWorkRequestBuilder<RuleUpdateWorker>(
                PERIOD_HOURS, TimeUnit.HOURS
            ).build()
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )

            val oneTime = OneTimeWorkRequestBuilder<RuleUpdateWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                oneTime
            )
            SecureLog.i("RuleUpdateScheduler", "Rule update scheduled (periodic + boot)")
        } catch (e: Exception) {
            SecureLog.e("RuleUpdateScheduler", "Failed to schedule rule update", e)
        }
    }
}
