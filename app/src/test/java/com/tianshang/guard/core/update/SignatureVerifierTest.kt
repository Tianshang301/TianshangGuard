package com.tianshang.guard.core.update

import com.tianshang.guard.data.remote.RulesDiff
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.util.Base64

class SignatureVerifierTest {

    @Before
    fun setUp() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private fun generateKeyPair(): Pair<ByteArray, java.security.PrivateKey> {
        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        val kp = kpg.generateKeyPair()
        return kp.public.encoded to kp.private
    }

    private fun sign(diff: RulesDiff, publicKeyBytes: ByteArray, privateKey: java.security.PrivateKey): String {
        val canonical = SignatureVerifier(publicKeyBytes).canonicalize(diff)
        val sig = Signature.getInstance("Ed25519", "BC")
        sig.initSign(privateKey)
        sig.update(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    @Test
    fun `valid signature verifies true`() {
        val (publicKeyBytes, privateKey) = generateKeyPair()
        val diff = RulesDiff(
            version = "1.2.3",
            timestamp = 1700000000000L,
            nonce = "abc123",
            adds = listOf("evil-phish.com", "secure-bank-login.com"),
            removes = listOf("good.example.com")
        )
        val signed = diff.copy(signature = sign(diff, publicKeyBytes, privateKey))
        assertTrue(SignatureVerifier(publicKeyBytes).verify(signed))
    }

    @Test
    fun `tampered payload fails verification`() {
        val (publicKeyBytes, privateKey) = generateKeyPair()
        val diff = RulesDiff(
            version = "1.2.3",
            timestamp = 1700000000000L,
            nonce = "abc123",
            adds = listOf("evil-phish.com"),
            removes = listOf()
        )
        val signed = diff.copy(signature = sign(diff, publicKeyBytes, privateKey))
        val tampered = signed.copy(adds = listOf("evil-phish.com", "extra-evil.com"))
        assertFalse(SignatureVerifier(publicKeyBytes).verify(tampered))
    }

    @Test
    fun `missing signature fails verification`() {
        val (publicKeyBytes, _) = generateKeyPair()
        val diff = RulesDiff(
            version = "1.2.3",
            timestamp = 1L,
            nonce = "x",
            adds = listOf("a"),
            removes = listOf()
        )
        assertFalse(SignatureVerifier(publicKeyBytes).verify(diff))
    }

    @Test
    fun `wrong key fails verification`() {
        val (signerPublic, signerPrivate) = generateKeyPair()
        val (otherPublic, _) = generateKeyPair()
        val diff = RulesDiff(
            version = "1.2.3",
            timestamp = 1700000000000L,
            nonce = "abc123",
            adds = listOf("evil-phish.com"),
            removes = listOf()
        )
        val signed = diff.copy(signature = sign(diff, signerPublic, signerPrivate))
        assertFalse(SignatureVerifier(otherPublic).verify(signed))
    }

    @Test
    fun `canonicalize is order-independent for domain lists`() {
        val (publicKeyBytes, _) = generateKeyPair()
        val a = RulesDiff("1.0.0", 1L, "n", listOf("b", "a", "c"), listOf("x"))
        val b = RulesDiff("1.0.0", 1L, "n", listOf("c", "a", "b"), listOf("x"))
        val verifier = SignatureVerifier(publicKeyBytes)
        assertTrue(verifier.canonicalize(a) == verifier.canonicalize(b))
    }

    @Test
    fun `embedded production public key rejects foreign signatures`() {
        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        val kp = kpg.generateKeyPair()
        val canonical = "1.0.0\n1700000000000\nnonce\nadds\nremoves"
        val sig = Signature.getInstance("Ed25519", "BC").also {
            it.initSign(kp.private)
            it.update(canonical.toByteArray(Charsets.UTF_8))
        }.sign()
        val diff = RulesDiff(
            "1.0.0", 1700000000000L, "nonce",
            listOf("adds"), listOf("removes"),
            Base64.getEncoder().encodeToString(sig)
        )
        assertFalse(SignatureVerifier().verify(diff))
    }
}
