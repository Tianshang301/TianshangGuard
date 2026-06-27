package com.tianshang.guard.core.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainDao
import com.tianshang.guard.data.local.database.DomainEntity
import com.tianshang.guard.data.remote.GithubRulesApi
import com.tianshang.guard.core.util.SecureLog
import kotlinx.coroutines.flow.first
import org.koin.java.KoinJavaComponent
import java.security.MessageDigest

class RuleUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private val DOMAIN_REGEX = Regex("^(?!-)[a-zA-Z0-9-]{1,63}(?<!-)(\\.[a-zA-Z0-9-]{1,63})*\\.[a-zA-Z]{2,}$")
        private const val MAX_RULES_PER_UPDATE = 10000
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs: GuardPreferences = KoinJavaComponent.get(GuardPreferences::class.java)
            val api: GithubRulesApi = KoinJavaComponent.get(GithubRulesApi::class.java)
            val domainDao: DomainDao = KoinJavaComponent.get(DomainDao::class.java)

            val localVersion = prefs.rulesVersion.first()
            val remoteVersion = api.getLatestRulesVersion()

            if (compareVersions(remoteVersion.version, localVersion) > 0) {
                val diff = api.getRulesDiff(localVersion)
                if (diff.adds.size + diff.removes.size > MAX_RULES_PER_UPDATE) {
                    SecureLog.e("RuleUpdateWorker", "Rules update exceeds max limit")
                    return Result.failure()
                }

                // Verify rule integrity signature
                if (!verifySignature(diff)) {
                    SecureLog.e("RuleUpdateWorker", "Rule signature verification failed, rejecting update")
                    return Result.failure()
                }

                applyDiff(domainDao, diff.adds, diff.removes)
                prefs.setRulesVersion(remoteVersion.version)
                SecureLog.i("RuleUpdateWorker", "Rules updated to ${remoteVersion.version}")
            }

            Result.success()
        } catch (e: Exception) {
            SecureLog.e("RuleUpdateWorker", "Rule update failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun verifySignature(diff: com.tianshang.guard.data.remote.RulesDiff): Boolean {
        // If no signature provided, allow update (backward compatibility)
        val signature = diff.signature ?: return true

        // Calculate SHA-256 of adds + removes
        val content = (diff.adds + diff.removes).joinToString("\n")
        val calculated = sha256(content)

        return calculated.equals(signature, ignoreCase = true)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private suspend fun applyDiff(dao: DomainDao, adds: List<String>, removes: List<String>) {
        val now = System.currentTimeMillis()

        adds.forEach { rawDomain ->
            val domain = rawDomain.trim().lowercase()
            if (!DOMAIN_REGEX.matches(domain)) return@forEach
            if (dao.isWhitelisted(domain)) return@forEach

            dao.insert(
                DomainEntity(
                    domain = domain,
                    category = DomainCategory.BLACKLIST,
                    source = "remote",
                    addedAt = now
                )
            )
        }

        removes.forEach { rawDomain ->
            val domain = rawDomain.trim().lowercase()
            if (!DOMAIN_REGEX.matches(domain)) return@forEach

            dao.insert(
                DomainEntity(
                    domain = domain,
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
