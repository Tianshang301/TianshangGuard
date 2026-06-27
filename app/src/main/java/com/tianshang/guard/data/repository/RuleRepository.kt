package com.tianshang.guard.data.repository

import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainDao
import com.tianshang.guard.data.local.database.DomainEntity

class RuleRepository(private val domainDao: DomainDao) {

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    suspend fun isWhitelisted(domain: String): Boolean {
        return domainDao.isWhitelisted(domain)
    }

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    suspend fun isBlacklisted(domain: String): Boolean {
        return domainDao.isBlacklisted(domain)
    }

    // BUGFIX: Changed to suspend fun to avoid main thread queries
    suspend fun getKnownDomains(): List<String> {
        return domainDao.getKnownDomains()
    }

    suspend fun addToWhitelist(domain: String) {
        domainDao.insert(
            DomainEntity(
                domain = domain,
                category = DomainCategory.WHITELIST,
                source = "user"
            )
        )
    }

    suspend fun addToBlacklist(domain: String) {
        domainDao.insert(
            DomainEntity(
                domain = domain,
                category = DomainCategory.BLACKLIST,
                source = "user"
            )
        )
    }

    suspend fun addSuspicious(domain: String, confidence: Float) {
        domainDao.insert(
            DomainEntity(
                domain = domain,
                category = DomainCategory.SUSPICIOUS,
                source = "detector",
                confidence = confidence
            )
        )
    }

    suspend fun clearAll() {
        domainDao.clearAll()
    }
}
