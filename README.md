# TianshangGuard (天殇·破妄)

> **If even one person can be saved from fraud, this project is worth it.**

[![CI](https://github.com/Tianshang301/TianshangGuard/actions/workflows/ci.yml/badge.svg)](https://github.com/Tianshang301/TianshangGuard/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-red.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)

Open-source Android anti-fraud tool with a layered defense architecture. **All analysis runs on-device — zero data upload.**

[中文文档](readme/README.zh-CN.md)

---

## Features

| Feature | Description |
|---------|-------------|
| **DNS Domain Blocking** | Bloom Filter fast filtering + homograph detection (Punycode/Cyrillic/fullwidth) |
| **URL Phishing Detection** | Byte-level Transformer on-device inference (ONNX Runtime + NNAPI) |
| **SMS Scam Detection** | Multi-model fusion: language detection + SMS specialist + URL extraction |
| **Behavior Monitoring** | Screen sharing + banking app combination detection |
| **Tiered Alerts** | Silent log → Banner → Dialog confirmation → Full-screen block |
| **Rule Updates** | Remote blacklist/whitelist sync with SHA-256 integrity verification |
| **Database Encryption** | SQLCipher + Android Keystore for local data protection |
| **DNS Privacy** | DNS over HTTPS (DoH) with UDP fallback |
| **Multi-language** | Chinese (zh), English (en), Unified (auto-detect) versions |

---

## What's New in v1.3.0-alpha

### Security Hardening
- **SQLCipher Database Encryption**: User data encrypted with Android Keystore
- **DNS over HTTPS (DoH)**: DNS queries encrypted via Cloudflare, UDP fallback
- **Rule Integrity Verification**: SHA-256 signature validation for rule updates

### Model Enhancement
- **Back-Translation Augmentation**: Chinese → English → Chinese for diverse training data
- **Feature Vector Expansion**: 8 → 24 dimensions for better phishing detection
- **Knowledge Distillation**: SMS model trained with Chinese model as teacher

### Experience Optimization
- **Alert Details**: "Why was this flagged?" shows detection reasons and ML score
- **Enhanced Statistics**: Trend charts and risk distribution visualization

---

## Architecture

```mermaid
graph TB
    subgraph Presentation["UI Layer"]
        A[MainActivity] --> B[SmsScreen]
        A --> C[StatsScreen]
        A --> D[SettingsScreen]
        A --> E[ReportScreen]
        F[AlertActivity]
    end

    subgraph Domain["Domain Layer"]
        G[AnalyzeSmsUseCase]
        H[CheckDomainRiskUseCase]
        I[TriggerAlertUseCase]
    end

    subgraph Core["Core Engine Layer"]
        J[DnsEngine<br/>DNS Proxy + Domain Detection]
        K[MlEngine<br/>ONNX Inference + Rule Fallback]
        L[MonitorEngine<br/>Behavior Monitoring]
        M[AlertEngine<br/>Tiered Alerts]
    end

    subgraph Data["Data Layer"]
        N[(Room DB<br/>SQLCipher Encrypted)]
        O[ONNX Models<br/>url + chinese + sms]
        P[Remote Rules<br/>GitHub + SHA-256]
    end

    A --> G
    B --> G
    G --> K
    H --> J
    I --> M
    J --> N
    K --> O
    L --> M
    M --> F
```

### ML Inference Pipeline

```mermaid
flowchart LR
    A[Input Text] --> B[ByteTokenizer<br/>UTF-8 Encoding]
    B --> C[ONNX Model<br/>INT8 Quantized]
    C --> D{Risk Score}
    D -->|< 0.10| E[✅ Safe]
    D -->|0.10 ~ 0.50| F[⚠️ Suspicious]
    D -->|≥ 0.50| G[🚨 Dangerous]
    C -.->|Timeout/Fail| H[Rule Engine Fallback]
    H --> D
```

### SMS Detection Flow

```mermaid
flowchart TD
    A[Incoming SMS] --> B{SMS Monitor<br/>Enabled?}
    B -->|No| C[Ignore]
    B -->|Yes| D[SmsReceiver<br/>BroadcastReceiver]
    D --> E[AnalyzeSmsUseCase]
    E --> F[Language Detection]
    F --> G[Text Model<br/>Chinese/English]
    F --> H[SMS Specialist Model]
    F --> I[URL Extraction + Model]
    G --> J[maxOf Fusion]
    H --> J
    I --> J
    J --> K{Risk Level}
    K -->|SAFE| L[Silent Log]
    K -->|SUSPICIOUS| M[Banner Alert]
    K -->|DANGEROUS| N[Dialog Alert<br/>+ Anti-fraud Tip]

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
| [v1.3.0-alpha-english](https://github.com/Tianshang301/TianshangGuard/releases/tag/v1.3.0-alpha-english) | English UI | URL + English | ✅ Released |
| [v1.3.0-alpha-chinese](https://github.com/Tianshang301/TianshangGuard/releases/tag/v1.3.0-alpha-chinese) | Chinese UI | URL + Chinese + SMS | ✅ Released |
| [v1.3.0-alpha-unified](https://github.com/Tianshang301/TianshangGuard/releases/tag/v1.3.0-alpha-unified) | Auto-detect | URL + Chinese + English + SMS | ✅ Released |

---

## Model Training

The project includes BytePhishingTransformer models:

| Model | File | Size | Parameters | Training Data | Performance |
|-------|------|------|------------|---------------|-------------|
| URL Detection | url_phishing.onnx | 312 KB | 120,321 | PhiUSIIL (235K URLs) | AUC=0.9942 |
| Chinese Text | chinese_phishing.onnx | 1,021 KB | 644,865 | ChiFraud (82K cleaned) | AUC=0.9492 |
| SMS Phishing | sms_phishing.onnx | 312 KB | 120,321 | FBS SMS + ChiFraud (11K) | Recall=97.88% |
| English Text | english_phishing.onnx | 312 KB | 120,321 | UCI + NCSU + IMC25 | TBD |

### Hyperparameters

| Parameter | URL/SMS/EN Model | Chinese Model |
|-----------|------------------|---------------|
| d_model | 64 | 128 |
| n_heads | 2 | 4 |
| n_layers | 2 | 4 |
| d_ff | 128 | 256 |
| max_seq_len | 512 | 512 |
| vocab_size | 256 | 256 |

### Training Commands

```bash
cd scripts

# Train URL model
python train_phishing_model.py --mode url

# Train Chinese model
python train_phishing_model.py --mode chinese --fresh

# Train SMS model
python train_phishing_model.py --mode sms

# Train English model
python train_phishing_model.py --mode english

# Back-translation augmentation
python backtranslate_augment.py --input raw_data/chifraud/ --output raw_data/augmented/
```

Models are automatically exported as ONNX INT8 quantized and copied to `app/src/main/assets/model/`.

### Threshold Calibration

After training, calibrate optimal thresholds:

```bash
python _calibrate_thresholds.py
```

Current thresholds (deployed):
- **SAFE**: score < 0.10
- **SUSPICIOUS**: 0.10 – 0.50
- **DANGEROUS**: ≥ 0.50

---

## Project Structure

```
TianshangGuard/
├── app/src/
│   ├── main/
│   │   ├── java/com/tianshang/guard/
│   │   │   ├── core/
│   │   │   │   ├── dns/          # DNS engine, homograph detection, Bloom Filter, DoH
│   │   │   │   ├── ml/           # MlEngine, OnnxMlEngine, rule engine
│   │   │   │   ├── monitor/      # Behavior monitoring (screen sharing)
│   │   │   │   ├── alert/        # Tiered alert engine
│   │   │   │   ├── feedback/     # Human feedback system
│   │   │   │   ├── retrieval/    # BM25 retrieval engine
│   │   │   │   ├── rl/           # Feature extraction & prediction
│   │   │   │   ├── calibration/  # Threshold calibration
│   │   │   │   ├── optimizer/    # Battery optimization
│   │   │   │   ├── telemetry/    # Performance tracing
│   │   │   │   └── update/       # Rule update worker
│   │   │   ├── data/
│   │   │   │   ├── local/        # Room DB (SQLCipher), preferences
│   │   │   │   ├── remote/       # GitHub rules API
│   │   │   │   └── repository/   # Data repositories
│   │   │   ├── domain/           # UseCase layer
│   │   │   ├── service/          # VPN, foreground service, SMS receiver, boot
│   │   │   ├── ui/               # Compose UI (home, sms, stats, settings, alerts)
│   │   │   └── di/               # Koin dependency injection
│   │   └── assets/
│   │       ├── model/            # ONNX model files
│   │       └── knowledge_base/   # BM25 pre-computed index
│   ├── zh/                       # Chinese flavor
│   ├── en/                       # English flavor
│   └── unified/                  # Unified flavor (auto-detect)
├── scripts/
│   ├── train_phishing_model.py   # Main training script
│   ├── backtranslate_augment.py  # Back-translation augmentation
│   ├── build_bm25_index.py       # BM25 index builder
│   └── raw_data/                 # Training datasets
└── .github/workflows/
    ├── ci.yml                    # CI: unit tests
    └── build.yml                 # Build: APK artifacts
```

---

## Privacy & Security

### Core Commitments

- **On-device analysis**: All inference runs locally via ONNX Runtime with NNAPI hardware acceleration
- **Database encryption**: SQLCipher + Android Keystore for local data protection
- **DNS privacy**: DNS over HTTPS (DoH) via Cloudflare, UDP fallback
- **Rule integrity**: SHA-256 signature verification for rule updates
- **Open-source auditable**: Code is fully public, community review welcome
- **Local storage only**: All data stored locally in Room database, user can export or delete anytime
- **Minimal permissions**: Only essential permissions requested, user controls each

### Capability Boundaries

**Can protect against**:
- Known phishing domain access
- Spoofed domains (visual confusion, homograph, transliteration)
- Phishing phrases and scam keywords in SMS
- Screen sharing + banking app high-risk operations
- Phishing content in web pages

**Cannot protect against**:
- Users voluntarily bypassing protection (core social engineering problem)
- Phone scams (no network traffic signature)
- Zero-day phishing domains (not yet indexed)
- Encrypted communication content (WeChat, in-app WebView)

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
- [ONNX Runtime](https://onnxruntime.ai/) — On-device inference engine
- [PhishTank](https://www.phishtank.com/) — Phishing domain intelligence

---

## License

[MIT](LICENSE) © Tianshang301
