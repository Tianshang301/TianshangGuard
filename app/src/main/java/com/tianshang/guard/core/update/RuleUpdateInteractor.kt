package com.tianshang.guard.core.update

import com.tianshang.guard.data.local.GuardPreferences
import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainDao
import com.tianshang.guard.data.local.database.DomainEntity
import com.tianshang.guard.data.remote.GithubRulesApi
import com.tianshang.guard.data.remote.RulesDiff
import com.tianshang.guard.core.util.SecureLog
import kotlinx.coroutines.flow.first

class RuleUpdateInteractor(
    private val prefs: GuardPreferences,
    private val api: GithubRulesApi,
    private val domainDao: DomainDao,
    private val signatureVerifier: SignatureVerifier
) {
    companion object {
        private val DOMAIN_REGEX = Regex("^(?!-)[a-zA-Z0-9-]{1,63}(?<!-)(\\.[a-zA-Z0-9-]{1,63})*\\.[a-zA-Z]{2,}$")
        private const val MAX_RULES_PER_UPDATE = 10000
    }

    suspend fun execute(): Boolean {
        return try {
            val localVersion = prefs.rulesVersion.first()
            val remoteVersion = api.getLatestRulesVersion()

            if (compareVersions(remoteVersion.version, localVersion) <= 0) {
                return true
            }

            val diff = api.getRulesDiff(localVersion)
            if (diff.adds.size + diff.removes.size > MAX_RULES_PER_UPDATE) {
                SecureLog.e("RuleUpdateInteractor", "Rules update exceeds max limit")
                return false
            }

            if (!signatureVerifier.verify(diff)) {
                SecureLog.e("RuleUpdateInteractor", "Rule signature verification failed, rejecting update")
                return false
            }

            applyDiff(domainDao, diff)
            prefs.setRulesVersion(remoteVersion.version)
            SecureLog.i("RuleUpdateInteractor", "Rules updated to ${remoteVersion.version}")
            true
        } catch (e: Exception) {
            SecureLog.e("RuleUpdateInteractor", "Rule update failed", e)
            false
        }
    }

    private suspend fun applyDiff(dao: DomainDao, diff: RulesDiff) {
        val now = System.currentTimeMillis()
        diff.adds.forEach { rawDomain ->
            val domain = rawDomain.trim().lowercase()
            if (!DOMAIN_REGEX.matches(domain)) return@forEach
            if (dao.isWhitelisted(domain)) return@forEach
            dao.insert(DomainEntity(
                domain = domain,
                category = DomainCategory.BLACKLIST,
                source = "remote",
                addedAt = now
            ))
        }
        diff.removes.forEach { rawDomain ->
            val domain = rawDomain.trim().lowercase()
            if (!DOMAIN_REGEX.matches(domain)) return@forEach
            dao.insert(DomainEntity(
                domain = domain,
                category = DomainCategory.WHITELIST,
                source = "remote",
                addedAt = now
            ))
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
