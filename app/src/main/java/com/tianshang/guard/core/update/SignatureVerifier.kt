package com.tianshang.guard.core.update

import com.tianshang.guard.data.remote.RulesDiff
import java.security.MessageDigest

class SignatureVerifier {
    fun verify(diff: RulesDiff): Boolean {
        val signature = diff.signature ?: return false
        val content = (diff.adds + diff.removes).joinToString("\n")
        val calculated = sha256(content)
        return calculated.equals(signature, ignoreCase = true)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
