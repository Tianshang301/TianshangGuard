# TianshangGuard (天殇·破妄)

> **If even one person can be saved from fraud, this project is worth it.**

[![CI](https://github.com/Tianshang301/TianshangGuard/actions/workflows/ci.yml/badge.svg)](https://github.com/Tianshang301/TianshangGuard/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-red.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)
![Version](https://img.shields.io/badge/version-1.5.0-blue.svg)

Open-source Android anti-fraud tool with a layered defense architecture. **All analysis runs on-device — zero data upload.**

<p align="center">
  <img src="screenshot.png" alt="TianshangGuard Screenshot" width="720">
</p>

[中文文档](readme/README.zh-CN.md)

---

## Features

| Feature | Description |
|---------|-------------|
| **DNS Domain Blocking** | Bloom Filter fast filtering + homograph detection (Punycode/Cyrillic/Greek/Fullwidth/Armenian) |
| **URL Phishing Detection** | Byte-level Transformer on-device inference (ONNX Runtime + NNAPI) |
| **SMS Scam Detection** | SMS model for Chinese + English, URL model for embedded links — on-device inference with dynamic model selection |
| **QR Code Phishing Guard** | Built-in ZXing QR scanner + CameraX preview + real-time URL risk analysis via DNS engine |
| **Web3 Domain Detection** | ENS `.eth`, Unstoppable `.crypto`, SID `.bnb` — rule-based detection, no ML dependency |
| **BPE Subword Tokenizer** | Vocabulary-based tokenizer with ByteTokenizer fallback — better Chinese character handling |
| **Behavior Monitoring** | Screen sharing + banking app combination detection via UsageStatsManager, process UID verification, and API 34+ MediaProjection signals |
| **Tiered Alerts** | Silent log → Banner → Dialog confirmation → Full-screen block with cooldown and rate limiting |
| **Feedback Engine** | User feedback (phishing / false positive) integrated with BM25 retrieval for adaptive detection |
| **BM25 Knowledge Base** | Pre-computed retrieval index for anti-fraud educational content |
| **Feature-Based Prediction** | 24-dimensional feature extraction + online prediction with adaptive threshold calibration |
| **Rule Updates** | Remote blacklist/whitelist sync with **Ed25519 signature verification** (replay-protected, canonical payload) |
| **Database Encryption** | SQLCipher + Android Keystore (StrongBox/TEE), **fail-closed** in-memory fallback, secure migration erase |
| **DNS Privacy** | DNS over HTTPS (DoH) with Cloudflare + AliDNS endpoints, certificate pinning, **fail-closed (no plaintext fallback)** |
| **Battery Optimization** | Brand-specific battery/autostart settings (Huawei, Xiaomi, OPPO, vivo, Meizu, Samsung, Honor) |
| **Multi-language** | Chinese (zh), English (en), Unified (auto-detect) build flavors |

---

## What's New in v1.5.0

### QuishGuard — QR Code Phishing Interception
- **Built-in QR scanner**: CameraX-based scanning with ZXing Core decoding
- **Risk preview before opening**: Scanned QR URLs are analyzed by the DNS engine before the browser launches
- **Tiered decisions**: Pass (safe URLs) → Warn with preview (suspicious) → Block with preview (dangerous)
- **Quick Settings Tile**: One-tap QR scanner access from the notification shade
- **Layered defense**: Active protection (built-in scanner) + passive protection (VPN DNS blocking)

### Web3Guard — Blockchain Domain Detection
- **ENS detection**: Identifies `.eth` domains and resolves via Ethereum Name Service
- **Unstoppable Domains**: Detects `.crypto`, `.nft`, `.blockchain`, `.bitcoin`, `.wallet`, etc.
- **SID (Space ID)**: Detects `.bnb`, `.arb` domains on BNB Chain and Arbitrum
- **Pure rule-based**: Zero ML dependency, lightweight detection

### SMS Model v5 — 100% Real Data Training
- **v5 dataset**: 8,848 real Chinese SMS samples (50/50 balanced) — cleaned FBS (6,948) + mudou_spam + mudou_ham (1,900)
- **30-epoch training**: BytePhishingTransformer (120K params), FocalLoss(alpha=0.75, gamma=2.0), batch=64
- **Calibrated thresholds**: SAFE < 0.30, SUSPICIOUS 0.30–0.59, DANGEROUS ≥ 0.59 (v5 validation: AUC=0.9672, F1=0.9206)
- **No synthetic data**: Training uses only real FBS + mudou SMS data; no template-generated phishing

### Security Infrastructure
- **Database encryption**: SQLCipher v4.5.4 with Android Keystore AES-GCM passphrase (StrongBox/TEE-backed)
- **Fail-closed encryption**: If the SQLCipher native library is unavailable, data falls back to a volatile **in-memory** store with a UI warning (never plaintext on disk)
- **Automatic migration**: Plaintext databases are transparently migrated to encrypted format on first launch; old plaintext files are securely overwritten before deletion
- **Ed25519 rule signing**: Remote rule updates are verified with an embedded public key (canonical payload, timestamp + replay protection)
- **Security module tests**: 6 androidTests covering encryption, decryption, data persistence, migration, tamper detection

### Security Audit Remediation (2026-08)

A full-source security audit (2026-08-28) identified 20 findings (3 Critical / 7 High / 6 Medium / 4 Low); all are remediated in `main`:

- **C-01** — Keyless SHA-256 rule "signature" → **Ed25519 public-key verification** with canonical payload + replay protection
- **C-02** — DoH plaintext-UDP downgrade → **fail-closed** (multi-endpoint DoH, SERVFAIL on failure, DNS response question-section validation)
- **C-03** — SQLCipher silent plaintext fallback → **fail-closed** volatile in-memory DB + UI warning
- **H-01..H-07** — Per-model ML fallback, VPN bounded concurrency + rate limiting, rule/filter sync, StrongBox/TEE key, multi-SPKI pin set, release R8 minify + lint, CI SHA-pinned actions + least-privilege
- **M-01..M-06** — Homograph brand-aware detection, Web3 registrable-domain parsing, screen-share UID/MediaProjection signals, SHA-256 feedback hash, training-script hardening (`weights_only`, loopback + token auth), wrapper SHA-256 + backup exclusions
- **L-01..L-04** — DoH-only keepalive, visit-history retention toggle + 30-day purge, URL normalization fix, dead code removal

### Bug Fixes & Stability
- **59 security audit bugs identified**: 26 P0/P1 fixed (12 Critical + 14 High), 33 P2 deferred to v1.6.0
- **CIPHER_HOOK alignment**: All SQLCipher database connections now use consistent encryption parameters (cipher_page_size, kdf_iter, HMAC algorithm)
- **CIPHER_HOOK mismatch fixed**: Test helpers and production code now share the same `SQLiteDatabaseHook`, eliminating "file is not a database" errors
- **Migration engine rewritten**: Replaced `sqlcipher_export()` (incompatible with Android SQLite) with read-via-Android-SQLite + write-via-Room-DAOs pipeline
- **Tamper detection test**: New robust test with `withTimeout(5000)` to prevent SQLCipher native hangs on corrupted files
- **Test isolation**: All security tests now use UUID-unique database names to prevent inter-test contamination
- **26/26 androidTests pass** on real Huawei device (ADY-AL00)

### Technical Report
- [v1.5.0 SMS Anti-fraud Model Training Report](docs/v1.5.0_report.tex) — LaTeX technical report covering data construction, 30-epoch training, ONNX quantization, threshold calibration (AUC=0.9672, F1=0.9206 at threshold 0.59), and manual test coverage gap analysis

---

## Architecture

```mermaid
graph TB
    subgraph Presentation["UI Layer"]
        A[MainActivity] --> B[SmsScreen]
        A --> C[StatsScreen]
        A --> D[SettingsScreen]
        A --> Qr[QrPreviewActivity<br/>CameraX + ZXing Scanner]
        F[AlertActivity<br/>5 Alert Types]
        G[OnboardingScreen]
    end

    subgraph Domain["Domain Layer (7 Use Cases)"]
        H[AnalyzeSmsUseCase]
        I[AnalyzeWebPageUseCase]
        J[CheckDomainRiskUseCase]
        K[TriggerAlertUseCase]
        L[DetectScreenSharingUseCase]
        M[UpdateRulesUseCase]
        N[InterceptQrUseCase]
    end

    subgraph Core["Core Engine Layer"]
        O[DnsEngine<br/>DNS Proxy + Domain Detection<br/>+ Web3DomainDetector]
        P[MlEngine<br/>ONNX Inference + BPE Tokenizer]
        Q[MonitorEngine<br/>Behavior Monitoring]
        R[AlertEngine<br/>Tiered Alerts + Cooldown]
        S[FeedbackEngine<br/>BM25 + Feature Integration]
        T[Bm25Engine<br/>Knowledge Base Retrieval]
        U[FeatureBasedPredictor<br/>24-dim Feature Analysis]
        V[QuishGuardEngine<br/>QR Content Analysis]
    end

    subgraph Data["Data Layer"]
        W[(Room DB<br/>SQLCipher Encrypted)]
        X[ONNX Models<br/>URL + SMS + English]
        Y[BPE Vocabulary<br/>tokenizer/bpe_tokenizer_vocab.json]
        Z[Remote Rules<br/>GitHub + Ed25519]
    end

    A --> H
    A --> K
    A --> M
    B --> H
    C --> W
    D --> M
    Qr --> N
    H --> P
    I --> P
    J --> O
    K --> R
    L --> Q
    N --> V
    V --> O
    O --> W
    P --> X
    P --> Y
    Q --> R
    R --> F
    S --> T
    S --> U
```

### ML Inference Pipeline

```mermaid
flowchart LR
    A[Input Text] --> B{BPE Tokenizer<br/>vocab=4096}
    B -->|Success| C[ONNX Model<br/>INT8 Quantized]
    B -->|OOV Fallback| D[ByteTokenizer<br/>UTF-8 Encoding]
    D --> C
    C --> E{Risk Score}
    E -->|< 0.30| F[✅ Safe]
    E -->|0.30 ~ 0.59| G[⚠️ Suspicious]
    E -->|≥ 0.59| H[🚨 Dangerous]
    C -.->|Timeout/Fail| I[Rule Engine Fallback<br/>JSON Keywords]
    I --> E
```

### QR Code Scanning & Protection Flow

```mermaid
flowchart TD
    A[QR Code Scanned] --> B{Scanner Source}
    B -->|Built-in Scanner| C[QrPreviewActivity<br/>CameraX + ZXing]
    B -->|Third-party App| D[Browser Opens URL<br/>→ VPN DNS Query]
    C --> E[QrCodeDecoder<br/>URL / Payment / WiFi / Text]
    E --> F{URL?}
    F -->|Yes| G[QuishGuardEngine<br/>→ CheckDomainRiskUseCase]
    G --> H{DnsResult}
    H -->|Allow| I[Pass ✓<br/>Open in Browser]
    H -->|Unknown, risk≥0.5| J[Block ⛔<br/>Full-screen Preview]
    H -->|Unknown, 0.1≤risk<0.5| K[Warning ⚠️<br/>Preview with Risk Score]
    H -->|Block| J
    F -->|Payment QR| L[Warn with Preview]
    F -->|WiFi / Other| M[Pass ✓]

    D --> N[GuardVpnService<br/>DNS Interception]
    N --> O[LocalDnsEngine<br/>→ Homograph → Blacklist → ML → Web3]
    O --> P{DnsResult}
    P -->|Allow| Q[DNS Response ✓]
    P -->|Block| R[NXDOMAIN Response ⛔]
```

### SMS Detection Flow

```mermaid
flowchart TD
    A[Incoming SMS] --> B{SMS Monitor<br/>Enabled?}
    B -->|No| C[Ignore]
    B -->|Yes| D[SmsReceiver<br/>BroadcastReceiver + goAsync]
    D --> E[AnalyzeSmsUseCase]
    E --> F[Language Detection]
    F --> G[Text Model<br/>Chinese / English]
    F --> H[SMS Specialist Model]
    F --> I[URL Extraction + URL Model]
    G --> J[maxOf Fusion]
    H --> J
    I --> J
    J --> K{Risk Level}
    K -->|SAFE| L[Silent Log]
    K -->|SUSPICIOUS| M[Banner Alert]
    K -->|DANGEROUS| N[Dialog Alert<br/>+ Anti-fraud Tip + Feedback Buttons]

    O[Manual Input] --> P[SmsScreen]
    P --> E
```

---

## Quick Start

### Requirements

- **JDK**: 17
- **Android SDK**: 35 (compileSdk)
- **Gradle**: 8.x (wrapper included)
- **Device**: Android 8.0+ (API 26)

### Build

```bash
# Clone repository
git clone https://github.com/Tianshang301/TianshangGuard.git
cd TianshangGuard

# Build Chinese version
./gradlew assembleZhRelease

# Build English version
./gradlew assembleEnRelease

# Build Unified version (auto-detect language)
./gradlew assembleUnifiedRelease

# Install to device
adb install app/build/outputs/apk/zh/release/app-zh-release.apk
```

### Downloads

| Version | Language | Models Included | Status |
|---------|----------|-----------------|--------|
| [v1.5.0](https://github.com/Tianshang301/TianshangGuard/releases/tag/v1.5.0) | Auto-detect (language switch in Settings) | URL + SMS + Chinese + English | ✅ Released |
| Build from source (zh) | Chinese UI | URL + SMS | Build with `./gradlew assembleZhRelease` |
| Build from source (en) | English UI | URL + English | Build with `./gradlew assembleEnRelease` |

---

## Model Training

The project includes BytePhishingTransformer models:

| Model | File | Size | Parameters | Training Data | Performance |
|-------|------|------|------------|---------------|-------------|
| URL Detection | url_phishing.onnx | 319 KB | 120,321 | PhiUSIIL (235K URLs, augmented path-invariant) | AUC=0.9942 |
| SMS Phishing | sms_phishing.onnx | 319 KB | 120,321 | v5 real SMS: cleaned FBS (6,948) + mudou (1,900), 50/50 balanced | AUC=0.9672, F1=0.9206 at threshold 0.59 |
| Chinese Text | chinese_phishing.onnx | 319 KB | 120,321 | ChiFraud (82K cleaned + balanced) | AUC=0.9492 |
| English Text | english_phishing.onnx | 319 KB | 120,321 | UCI + NCSU + IMC25 | TBD |
| PhishTector Legacy | phishing_detector_quant.onnx | 1022 KB | 644,865 | ChiFraud (FP32, 645K params, historical) | Historical |

### Hyperparameters

| Parameter | URL/SMS/EN Model |
|-----------|------------------|
| d_model | 64 |
| n_heads | 2 |
| n_layers | 2 |
| d_ff | 128 |
| max_seq_len | 512 |
| vocab_size | 256 |
| tokenizer | BPE (vocab=4096) + Byte fallback |

### Training Commands

```bash
cd scripts

# Train URL model
python train_phishing_model.py --mode url

# Train SMS model
python train_phishing_model.py --mode sms

# Train English model
python train_phishing_model.py --mode english

# Train BPE tokenizer
python train_bpe_tokenizer.py

# Knowledge distillation for SMS model
python distill_sms_model.py

# Back-translation augmentation
python backtranslate_augment.py --input raw_data/chifraud/ --output raw_data/augmented/

# ONNX export + calibration
python export_and_calibrate.py
```

Models are automatically exported as ONNX INT8 quantized and copied to `app/src/main/assets/model/`.

### Threshold Calibration

Calibrated on v5 validation set (885 real SMS samples, AUC=0.9672):

```bash
python calibrate_sms_threshold.py
```

Deployed thresholds:
- **SAFE**: score < 0.30
- **SUSPICIOUS**: 0.30 – 0.59 (silent flag zone, catches ~92.9% of phishing at 8.7% FPR)
- **DANGEROUS**: ≥ 0.59 (Recall=92.9%, FPR=8.7%, F1=0.9206)

### Evaluation

```bash
# Validate ONNX inference
python test_onnx_models.py

# Model diagnosis
python diagnose_model.py

# Fitting check
python check_fitting.py
```

---

## Project Structure

```
TianshangGuard/
├── app/src/
│   ├── main/
│   │   ├── java/com/tianshang/guard/
│   │   │   ├── core/
│   │   │   │   ├── dns/           # DnsEngine, LocalDnsEngine, HomographDetector, BloomFilter, DnsPacketHandler, DohClient, BkTree, Web3DomainDetector
│   │   │   │   ├── ml/            # MlEngine, OnnxMlEngine, BpeTokenizer, ByteTokenizer, RuleBasedEngine, MlEngineWithFallback, InputSanitizer
│   │   │   │   ├── monitor/       # ScreenShareMonitor, RemoteConfigProvider
│   │   │   │   ├── alert/         # TieredAlertEngine, CooldownManager, AlertDataHolder
│   │   │   │   ├── feedback/      # FeedbackEngine (BM25 + feature integration)
│   │   │   │   ├── retrieval/     # Bm25Engine, KnowledgeBase
│   │   │   │   ├── rl/            # FeatureExtractor (24-dim), FeatureVector, FeatureStore, FeatureBasedPredictor
│   │   │   │   ├── calibration/   # ThresholdCalibrator
│   │   │   │   ├── update/        # RuleUpdateWorker, RuleUpdateInteractor, SignatureVerifier (Ed25519)
│   │   │   │   ├── optimizer/     # BatteryOptimizer (7 brands)
│   │   │   │   ├── quish/         # QuishGuardEngine, QrCodeDecoder (ZXing-based QR analysis)
│   │   │   │   ├── telemetry/     # PerformanceTracer
│   │   │   │   └── util/          # SecureLog, LocaleHelper
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── database/  # GuardDatabase (Room), Dao, Entity
│   │   │   │   │   ├── security/  # EncryptedDatabaseProvider (SQLCipher)
│   │   │   │   │   └── GuardPreferences.kt (DataStore)
│   │   │   │   ├── remote/        # GithubRulesApi
│   │   │   │   └── repository/    # RuleRepository, AlertRepository
│   │   │   ├── domain/            # 7 UseCases: AnalyzeSms, AnalyzeWebPage, CheckDomainRisk, TriggerAlert, DetectScreenSharing, UpdateRules, InterceptQr
│   │   │   ├── service/           # GuardVpnService (DoH), ForegroundService, BootReceiver, SmsReceiver, QrScanTileService
│   │   │   ├── ui/                # Compose UI (main, sms, stats, settings, alert, qr, onboarding, theme)
│   │   │   └── di/                # AppModule (Koin)
│   │   ├── assets/
│   │   │   ├── model/             # 5 ONNX model files (+1 auto backup)
│   │   │   ├── tokenizer/         # bpe_tokenizer_vocab.json
│   │   │   ├── knowledge_base/    # BM25 pre-computed index (index.bin)
│   │   │   ├── rules/             # whitelist.json, blacklist.json, keywords_sms.json, keywords_web.json
│   │   │   └── test_data/         # sms/domain/feedback/alert/feature test cases
│   │   └── res/                   # Base resources
│   ├── zh/                        # Chinese flavor (GuardApplication + strings.xml)
│   ├── en/                        # English flavor
│   ├── unified/                   # Unified flavor (auto-detect language)
│   ├── test/                      # Unit tests (23 files, 175 tests)
│   └── androidTest/               # Instrumentation tests (4 files, 26 tests)
├── scripts/
│   ├── train_phishing_model.py    # Main training script
│   ├── train_bpe_tokenizer.py     # BPE tokenizer training
│   ├── distill_sms_model.py       # SMS model knowledge distillation
│   ├── backtranslate_augment.py   # Back-translation augmentation
│   ├── build_bm25_index.py        # BM25 index builder
│   ├── _calibrate_thresholds.py   # Threshold calibration
│   ├── export_and_calibrate.py    # ONNX export + calibration
│   └── raw_data/                  # Training datasets (PhiUSIIL, ChiFraud, FBS, English)
└── .github/workflows/
    ├── ci.yml                     # CI: unit tests
    └── build.yml                  # Build: APK artifacts (3 flavors)
```

---

## Privacy & Security

### Core Commitments

- **On-device analysis**: All inference runs locally via ONNX Runtime with NNAPI hardware acceleration
- **Database encryption**: SQLCipher + Android Keystore (StrongBox/TEE), fail-closed in-memory fallback if the native library is unavailable, secure erase of migrated plaintext files
- **DNS privacy**: DNS over HTTPS (DoH) via Cloudflare + AliDNS, certificate pinning, fail-closed (no plaintext UDP fallback — resolution fails closed with SERVFAIL)
- **Rule integrity**: **Ed25519 public-key signature verification** for rule updates (canonical payload + timestamp/replay protection; unsigned or tampered updates rejected)
- **Feedback privacy**: User feedback (phishing / false positive) stored locally only, never uploaded
- **Feature extraction local**: All 24-dimensional feature analysis runs on-device
- **Open-source auditable**: Code is fully public, community review welcome
- **Minimal permissions**: Only essential permissions requested, user controls each

### Required Permissions

| Permission | Purpose |
|------------|---------|
| `BIND_VPN_SERVICE` ⚡ | VPN DNS interception — set as `<service android:permission>` attribute |
| `INTERNET` | DNS over HTTPS, GitHub rules update |
| `SYSTEM_ALERT_WINDOW` | Overlay warnings for phishing alerts |
| `PACKAGE_USAGE_STATS` | Screen sharing + banking app detection |
| `RECEIVE_SMS` + `READ_SMS` | Incoming SMS phishing analysis |
| `CAMERA` + `FOREGROUND_SERVICE_CAMERA` | QR code scanning (v1.5.0) |
| `RECEIVE_BOOT_COMPLETED` | Auto-start protection on boot |
| `FOREGROUND_SERVICE` | Keep-alive service for continuous protection |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ foreground service type declaration |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent battery optimization from killing service |
| `ACCESS_NETWORK_STATE` | Network connectivity checks for DoH fallback |
| `VIBRATE` | Vibrations for dangerous-level alerts |

### Capability Boundaries

**Can protect against**:
- Known phishing domain access
- Spoofed domains (visual confusion, homograph, transliteration)
- Phishing phrases and scam keywords in SMS (Chinese, English)
- Screen sharing + banking app high-risk operations
- Phishing content in web pages
- SMS phishing with embedded malicious URLs
- QR code phishing URLs (built-in scanner + VPN DNS dual layer)

**Cannot protect against**:
- Users voluntarily bypassing protection (core social engineering problem)
- Phone scams (no network traffic signature)
- Zero-day phishing domains (not yet indexed)
- Encrypted communication content (WeChat, in-app WebView)

---

## Tests

### Unit Tests (23 files, 175 tests)

| Module | Tests | Coverage |
|--------|-------|----------|
| `RuleBasedEngineTest` | 8 | Keyword matching logic |
| `HomographDetectorTest` | 16 | Homograph detection + pinyin confusion + brand-aware `assess()` |
| `AdaptiveBloomFilterTest` | 8 | Bloom filter correctness |
| `CooldownManagerTest` | 6 | Alert cooldown logic |
| `BkTreeTest` | 10 | BK-tree operations |
| `DnsPacketHandlerTest` | 15 | DNS packet parsing + response validation |
| `DohClientTest` | 2 | DoH client |
| `BpeTokenizerTest` | 9 | BPE tokenizer |
| `ByteTokenizerTest` | 9 | Byte tokenizer |
| `OnnxMlEngineTest` | 10 | ONNX engine |
| `OnnxMlEngineSpikeTest` | 4 | ONNX integration |
| `FeatureExtractorTest` | 23 | Feature extraction |
| `Bm25EngineTest` | 8 | BM25 retrieval |
| `PerformanceTracerTest` | 5 | Performance metrics |
| `SignatureVerifierTest` | 6 | Ed25519 signature verification |
| `FeedbackEngineTokenizerTest` | 7 | Feedback tokenization |
| `GuardPreferencesTest` | 1 | DataStore preferences |
| `RuleRepositoryTest` | 8 | Rule repository |
| `RuleUpdateInteractorTest` | 8 | Rule update interactor (signature + replay protection) |
| `AnalyzeSmsUseCaseTest` | 6 | SMS analysis use case |
| `AnalyzeWebPageUseCaseTest` | 2 | Web page analysis |
| `CheckDomainRiskUseCaseTest` | 3 | Domain risk check |
| `UpdateRulesUseCaseTest` | 1 | Rules update |

### Android Instrumentation Tests (26 tests)

| Test Class | Tests | Status |
|-----------|-------|--------|
| `AlertDaoTest` | 5 | ✅ Pass |
| `DomainDaoTest` | 6 | ✅ Pass |
| `GuardPreferencesTest` | 9 | ✅ Pass |
| `SecurityModuleTest` | 6 | ✅ Pass (SQLCipher encryption, migration, tamper detection, Keystore) |

### Test Data (assets/test_data/)

| File | Cases | Coverage |
|------|-------|----------|
| `sms_test_cases.json` | 41 | Phishing + legitimate + English SMS |
| `domain_test_cases.json` | 27 | Whitelist, blacklist, homograph, punycode, suspicious, unknown |
| `feedback_test_cases.json` | 10 | Phishing + false positive scenarios |
| `alert_test_cases.json` | 10 | 6 alert types |
| `feature_test_cases.json` | 14 | 14 feature dimensions |

---

## Contributing

```bash
# 1. Fork repository
# 2. Create feature branch
git checkout -b feature/your-feature

# 3. Commit changes
git commit -m "Add your feature"

# 4. Push branch
git push origin feature/your-feature

# 5. Create Pull Request
```

### Rule Contributions

Submit suspicious domains to `rules/community/` directory in JSON format:

```json
{
  "domain": "example.com",
  "reason": "phishing",
  "source": "user_report"
}
```

---

## Acknowledgments

- [PhiUSIIL](https://www.kaggle.com/datasets/shashwatwork/phiusiil-phishing-url-dataset) — URL phishing dataset
- [ChiFraud](https://github.com/xuemingxxx/ChiFraud) — Chinese fraud SMS dataset
- [FBS SMS](https://github.com/Cypher-Z/FBS_SMS_Dataset) — Fake Base Station SMS dataset (CCS'20)
- [mudou_spam](https://huggingface.co/datasets/shaonianruntu/Spam-Message-Classification) — Chinese SMS classification dataset (spam + ham)
- [ONNX Runtime](https://onnxruntime.ai/) — On-device inference engine
- [PhishTank](https://www.phishtank.com/) — Phishing domain intelligence
- [SQLCipher](https://www.zetetic.net/sqlcipher/) — Encrypted database engine
- [CameraX](https://developer.android.com/training/camerax) — Camera API for QR scanning
- [ZXing](https://github.com/zxing/zxing) — QR code decoding library

---

## License

[MIT](LICENSE) © Tianshang301
