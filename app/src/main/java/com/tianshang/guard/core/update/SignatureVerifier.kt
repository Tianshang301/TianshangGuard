package com.tianshang.guard.core.update

import com.tianshang.guard.data.remote.RulesDiff
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.Security
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies rule-set updates using an **Ed25519** public-key signature.
 *
 * This replaces the previous keyless SHA-256 "signature" scheme, which only proved
 * that the payload matched a hash transmitted alongside it and therefore provided
 * zero authenticity guarantees (anyone able to modify the HTTP response could also
 * recompute and supply the hash). See audit finding C-01.
 *
 * The public key is embedded in the APK at build time; the corresponding private
 * key is held only by the release signer (see scripts/sign_rules.py) and is never
 * shipped with the app.
 *
 * Canonical payload format (must match scripts/sign_rules.py EXACTLY):
 *   version + "\n" + timestamp + "\n" + nonce + "\n" +
 *   adds.joinToString("\n") + "\n" + removes.joinToString("\n")
 */
class SignatureVerifier(
    private val publicKeyBytes: ByteArray = DEFAULT_PUBLIC_KEY
) {
    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun verify(diff: RulesDiff): Boolean {
        val signature = diff.signature ?: return false
        val sigBytes = try {
            Base64.getDecoder().decode(signature)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val canonical = canonicalize(diff)
        return try {
            val keyFactory = KeyFactory.getInstance("Ed25519", PROVIDER)
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val verifier = Signature.getInstance("Ed25519", PROVIDER)
            verifier.initVerify(publicKey)
            verifier.update(canonical.toByteArray(Charsets.UTF_8))
            verifier.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    internal fun canonicalize(diff: RulesDiff): String {
        return buildString {
            append(diff.version).append('\n')
            append(diff.timestamp).append('\n')
            append(diff.nonce).append('\n')
            // Sort domain lists so the canonical form is independent of server
            // ordering (a non-canonical order must not produce a different hash).
            append(diff.adds.sorted().joinToString("\n")).append('\n')
            append(diff.removes.sorted().joinToString("\n"))
        }
    }

    companion object {
        private const val PROVIDER = "BC"

        // Embedded Ed25519 public key (X.509 SubjectPublicKeyInfo, Base64 DER).
        private val DEFAULT_PUBLIC_KEY: ByteArray = Base64.getDecoder().decode(
            "MCowBQYDK2VwAyEAUmbUs0ixPoyLmoaftjH/BBvdDGCqlGxszf4uYwKfTE4="
        )
    }
}
