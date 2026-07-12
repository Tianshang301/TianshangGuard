package com.tianshang.guard.core.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tianshang.guard.core.util.SecureLog
import org.koin.java.KoinJavaComponent

class RuleUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val interactor: RuleUpdateInteractor = KoinJavaComponent.get(RuleUpdateInteractor::class.java)
            val success = interactor.execute()
            if (success) Result.success() else Result.failure()
        } catch (e: Exception) {
            SecureLog.e("RuleUpdateWorker", "Rule update failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
