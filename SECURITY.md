# Security

Security commitments, audit records, and vulnerability reporting for **TianshangGuard (天殇·破妄)**.

## Security Model

- **On-device analysis** — all inference (URL / SMS / Web) runs locally via ONNX Runtime with NNAPI acceleration; **zero data upload**.
- **Database encryption** — SQLCipher v4.5.4 + Android Keystore (StrongBox/TEE); **fail-closed**: if the native library is unavailable the app uses a volatile in-memory store with a UI warning (never plaintext on disk); migrated plaintext files are securely overwritten before deletion.
- **DNS privacy** — DNS over HTTPS via Cloudflare + AliDNS with certificate pinning; **fail-closed** (no plaintext UDP fallback; resolution returns SERVFAIL on failure).
- **Rule integrity** — remote rule updates verified with an **Ed25519 public key** embedded in the app (canonical payload, timestamp + replay protection).
- **Minimal permissions** — only essential permissions are requested and each is user-controllable.
- **Open source & auditable** — all code is public; the community may audit it.

## Security Audit — 2026-08-28

Full-source security audit of `app/src`, `scripts/`, and `.github/workflows/`.
**Result: 20 findings (3 Critical / 7 High / 6 Medium / 4 Low) — all remediated in `main`.**

| ID | Severity | Finding | Remediation |
|----|----------|---------|-------------|
| C-01 | Critical | Keyless SHA-256 rule "signature" (zero authenticity) | **Ed25519 public-key verification** (embedded key, canonical sorted payload, timestamp + replay protection) |
| C-02 | Critical | DoH forced plaintext-UDP downgrade | **Fail-closed** multi-endpoint DoH (Cloudflare + AliDNS), SERVFAIL on total failure, DNS response validation (ID + QR + RCODE + question-section) |
| C-03 | Critical | SQLCipher silent plaintext fallback | **Fail-closed** volatile in-memory DB + UI warning (`secureStorageAvailable`) |
| H-01 | High | SMS analysis failed open when SMS model missing | Gated on `ModelType.SMS` readiness; falls back to rule engine |
| H-02 | High | VPN single-threaded DNS + `runBlocking` DoS | Bounded async pool (8) + per-source token-bucket rate limit + global in-flight cap (32) + single writer lock |
| H-03 | High | Rule updates not synced with in-memory Bloom filter | `LocalDnsEngine.reloadFilter()` wired into `RuleUpdateInteractor`; fixed `SettingsViewModel.checkRuleUpdates()` |
| H-04 | High | Keystore key not hardware-bound; passphrase in SharedPreferences | StrongBox (TEE fallback) + `derivePassphrase()` (no SharedPreferences layer) + WAL checkpoint + secure erase |
| H-05 | High | Single static cert pin | Per-host **multi-SPKI pin set** (raw.githubusercontent.com / api.github.com), verified against live chain; CI `verify-pins` job |
| H-06 | High | Release unminified/unshrunk, lint off | R8 minify + resource shrink + release lint; `proguard-rules.pro` |
| H-07 | High | CI supply chain (tag-pinned actions, keystore in runner) | Actions pinned to commit SHAs; least-privilege `permissions`; keystore decoded outside repo + `chmod 600` + always cleanup |
| M-01 | Medium | Screen-share detection bypassable | UID→package ownership verification + API 34+ MediaProjection active-session signal |
| M-02 | Medium | Web3 detection only `endsWith` | Registrable-domain parsing (`legit.com.evil.eth` → `evil.eth`) |
| M-03 | Medium | Homograph "one character blocks everything" | Brand-aware `assess()`: block only brand-similar + ≥2 confusables; non-brand IDN → needs-confirmation |
| M-04 | Medium | Feedback whitelist MD5 collision; global alert limiter | SHA-256 hash; DANGEROUS exempt from global 5s limiter; SMS cooldown key SHA-256 |
| M-05 | Medium | Training pipeline unsafe deserialization; unauthenticated server | `torch.load(weights_only=True)`; `training_server.py` binds 127.0.0.1 + optional `GUARD_TRAIN_TOKEN` |
| M-06 | Medium | Gradle wrapper no SHA-256; backup config leak | Wrapper `distributionSha256Sum`; backup rules exclude `guard_db_prefs.xml` |
| L-01 | Low | Plaintext 30s DNS keepalive | DoH-only keepalive (no plaintext UDP probe) |
| L-02 | Low | All visited domains retained | User toggle (default on) + opportunistic 30-day purge |
| L-03 | Low | Dead `PhishTankApi` | Removed |
| L-04 | Low | `normalizeUrl` global replace bug | URI-component normalization |

## Historical Audit

### v1.2.2 full code audit (2026-06-28)

A full code audit of core modules identified **59 bugs (12 Critical / 18 High / 26 Medium / 9 Low); 26 P0/P1 fixed**, 33 P2 deferred (tracked for a later release).
This is a **separate audit** from the 2026-08 security audit above. Detailed records: AGENTS.md §10.1.

## Vulnerability Reporting

Please **do not** open a public GitHub issue for security vulnerabilities. Report privately to the maintainers via:

- GitHub Security Advisory: **https://github.com/Tianshang301/TianshangGuard/security/advisories** (preferred)
- Email: the project's release signer contact (see commit signatures)

We aim to acknowledge reports within 72 hours and to release a fix in a timely manner. Please include:

- Affected version(s) and device/OS
- Steps to reproduce (minimal, if possible)
- Expected vs. actual behavior
- Proposed fix, if any

## Coordinated Disclosure

We follow responsible disclosure: please allow time for a fix to be released before public disclosure. We are happy to credit reporters (with consent) in the CHANGELOG.
