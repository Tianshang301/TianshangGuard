# Changelog

All notable changes to TianshangGuard are documented in this file.

## [1.5.0] - 2026-07-26

### Added
- **SMS Model v5**: 100% real data training (FBS + mudou_spam for phishing, mudou_ham for legitimate), 8,848 samples, 30 epochs
- **RiskLevel threshold calibration**: SAFE < 0.30, SUSPICIOUS 0.30–0.59, DANGEROUS ≥ 0.59 (AUC=0.9672 on v5 validation)
- **QuishGuard QR scanner**: Built-in CameraX + ZXing QR code scanner with real-time URL risk analysis
- **Web3Guard**: ENS `.eth`, Unstoppable `.crypto`, SID `.bnb` blockchain domain detection
- **Quick Settings Tile**: One-tap QR scanner access from notification shade
- **SQLCipher database encryption**: Encrypted local storage with Android Keystore protection
- **DNS over HTTPS (DoH)**: Cloudflare DoH with UDP fallback
- **SHA-256 rule integrity verification**: Rule update signature verification
- **BPE tokenizer deployment**: Vocabulary-based subword tokenizer with ByteTokenizer fallback
- **24-dimensional feature extraction + feature-based predictor**
- **Feedback engine + BM25 knowledge base**: User feedback integrated with adaptive detection
- **Camera + FOREGROUND_SERVICE_CAMERA permissions** (Android 14+)
- **Database DAO suspend migration + v1→v4 migration strategy**

### Fixed
- 26 security audit bugs fixed (12 Critical + 14 High) out of 59 identified
- CIPHER_HOOK alignment across all SQLCipher database connections
- SQLCipher "file is not a database" error from test/production hook mismatch
- Database migration engine: replaced `sqlcipher_export()` with read-via-Android-SQLite + write-via-Room-DAOs
- Tamper detection test: `withTimeout(5000)` to prevent SQLCipher native hangs
- Test isolation: UUID-unique database names across tests
- CI/CD: Gradle OOM fixes (heap 4g, per-flavor runners), keystore decode pipeline
- CI/CD: keystore.properties generation on CI (gitignored file missing on runner)
- CI/CD: trailing newline in gradle.properties so heap override works correctly
- CI/CD: printf+tempfile for keystore decode to avoid echo truncation

### Changed
- SMS model retrained from 10 to 30 epochs, switched from synthetic+v4 to v5 real data
- RiskLevel enum thresholds: SAFE 0.50→0.30, SUSPICIOUS 0.90→0.59, DANGEROUS unchanged at 1.0
- `export_and_calibrate.py` supports `CALIBRATE_MODE` env var for SMS calibration
- Japanese mode removed from training script

### Security
- SQLCipher v4.5.4 with AES-GCM key encryption via Android Keystore
- Automatic plaintext-to-encrypted database migration
- All ML inference on-device, zero data upload
- DNS over HTTPS with certificate pinning
- SHA-256 signature verification for rule updates

---

## [1.4.2] - 2026-07-03

### Added
- Manual test report generation
- SMS test set (40 cases, Chinese + English)
- Training report v1.4.2

### Fixed
- SQLCipher native crash on startup (`loadLibs()` fix)
- Various model calibration issues

---

## [1.4.1] - 2026-06-30

### Fixed
- Battery optimization for Huawei, Xiaomi, OPPO, vivo, Meizu, Samsung, Honor
- Brand-specific battery/autostart settings
- Bug fixes from v1.4.0 alpha testing

---

## [1.4.0] - 2026-06-20

### Added
- Feature-based prediction pipeline (24-dim feature extraction + prediction)
- BM25 knowledge base retrieval for anti-fraud education
- Feedback engine with n-gram token matching
- Threshold calibrator with momentum-based adaptation

---

## [1.3.2] - 2026-06-15

### Fixed
- ONNX model loading timeout handling
- MlEngineWithFallback resource cleanup
- PerformanceTracer thread safety (ConcurrentHashMap)

---

## [1.3.1] - 2026-06-10

### Fixed
- BPE tokenizer ByteTokenizer fallback logic
- ONNX inference session resource leaks
- DNS cache thread safety

---

## [1.3.0-alpha] - 2026-06-01

### Added
- SQLCipher database encryption (encrypted database provider)
- DNS over HTTPS client with UDP fallback
- SHA-256 rule update integrity verification
- BPE subword tokenizer

---

## [1.2.2] - 2026-05-28

### Fixed
- 59 security audit bugs identified and triaged
- BertTokenizer byte truncation fix
- ONNX resource leak fix
- RiskLevel.toScore() midpoint correction
- VPN handlerThread lifecycle fix
- DNS cache synchronization
- IPv6 response offset fix
- Homograph detection deduplication
- UI thread safety improvements
- SmsViewModel IO scheduler fix
- SettingsScreen toggle deduplication

---

## [1.2.1] - 2026-05-15

### Added
- Homograph detection improvements (Cyrillic, Greek, Fullwidth, Armenian)
- Adaptive Bloom Filter for DNS blacklist
- Levenshtein distance based domain similarity detection (BK-tree)

### Fixed
- DNS engine memory optimization
- Alert cooldown rate limiting

---

## [1.2.0] - 2026-05-01

### Added
- Behavior monitoring: screen sharing + banking app detection
- Tiered alert system (silent / banner / dialog / fullscreen)
- CooldownManager for alert rate limiting
- RemoteConfigProvider for app configuration

---

## [1.1.0-chinese] - 2026-04-15

### Added
- Chinese UI flavor with Chinese SMS model
- Initial ONNX Runtime integration
- Basic DNS phishing domain blocking
- Bloom Filter based blacklist
- First open-source release
