package com.tianshang.guard.core.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainDao
import com.tianshang.guard.data.local.database.DomainEntity
import com.tianshang.guard.data.remote.GithubRulesApi
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent

class RuleUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs: GuardPreferences = KoinJavaComponent.get(GuardPreferences::class.java)
            val api: GithubRulesApi = KoinJavaComponent.get(GithubRulesApi::class.java)
            val domainDao: DomainDao = KoinJavaComponent.get(DomainDao::class.java)

            val localVersion = prefs.rulesVersion.first()
            val remoteVersion = api.getLatestRulesVersion()

            if (compareVersions(remoteVersion.version, localVersion) > 0) {
                val diff = api.getRulesDiff(localVersion)
                applyDiff(domainDao, diff.adds, diff.removes)
                prefs.setRulesVersion(remoteVersion.version)
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun applyDiff(dao: DomainDao, adds: List<String>, removes: List<String>) {
        val now = System.currentTimeMillis()
        adds.forEach { domain ->
            dao.insert(
                DomainEntity(
                    domain = domain.trim().lowercase(),
                    category = DomainCategory.BLACKLIST,
                    source = "remote",
                    addedAt = now
                )
            )
        }
        removes.forEach { domain ->
            dao.insert(
                DomainEntity(
                    domain = domain.trim().lowercase(),
                    category = DomainCategory.WHITELIST,
                    source = "remote",
                    addedAt = now
                )
            )
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val aParts = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bParts = b.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val aVal = aParts.getOrElse(i) { 0 }
            val bVal = bParts.getOrElse(i) { 0 }
            if (aVal != bVal) return aVal - bVal
        }
        return 0
    }
}
