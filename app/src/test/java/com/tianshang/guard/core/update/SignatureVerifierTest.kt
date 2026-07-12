package com.tianshang.guard.core.update

import com.tianshang.guard.BaseUnitTest
import com.tianshang.guard.data.remote.RulesDiff
import org.junit.Assert
import org.junit.Test

class SignatureVerifierTest : BaseUnitTest() {

    private val verifier = SignatureVerifier()

    @Test
    fun `verify returns false when signature is null`() {
        val diff = RulesDiff(adds = listOf("example.com"), removes = emptyList(), signature = null)
        Assert.assertFalse(verifier.verify(diff))
    }

    @Test
    fun `verify returns true when signature matches content`() {
        val adds = listOf("example.com", "test.org")
        val removes = listOf("old.com")
        val content = (adds + removes).joinToString("\n")
        val expectedSig = sha256(content)
        val diff = RulesDiff(adds = adds, removes = removes, signature = expectedSig)
        Assert.assertTrue(verifier.verify(diff))
    }

    @Test
    fun `verify returns false when signature does not match`() {
        val diff = RulesDiff(
            adds = listOf("example.com"),
            removes = emptyList(),
            signature = "0000000000000000000000000000000000000000000000000000000000000000"
        )
        Assert.assertFalse(verifier.verify(diff))
    }

    @Test
    fun `verify is case insensitive`() {
        val content = listOf("example.com").joinToString("\n")
        val expectedSig = sha256(content)
        val upperSig = expectedSig.uppercase()
        val diff = RulesDiff(adds = listOf("example.com"), removes = emptyList(), signature = upperSig)
        Assert.assertTrue(verifier.verify(diff))
    }

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
