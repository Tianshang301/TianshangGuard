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
| **Rule Updates** | Remote blacklist/whitelist sync, community contributions |
| **Multi-language** | Chinese (zh), English (en), Japanese (ja) versions |

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
        N[(Room DB<br/>Domains + Alert Logs)]
        O[ONNX Models<br/>url + chinese + sms]
        P[Remote Rules<br/>GitHub]
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
    F --> G[Text Model<br/>Chinese/English/Japanese]
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

# Build Chinese version (recommended)
./gradlew assembleZhRelease

# Build English version
./gradlew assembleEnRelease

# Build Japanese version
./gradlew assembleJaRelease

# Install to device
adb install app/build/outputs/apk/zh/release/app-zh-release.apk
```

### Downloads

| Version | Language | Models Included | Status |
|---------|----------|-----------------|--------|
| [v1.1.0-chinese](https://github.com/Tianshang301/TianshangGuard/releases/tag/v1.1.0-chinese) | Chinese UI | URL + Chinese + SMS | ✅ Released |

---

## Model Training

The project includes five BytePhishingTransformer models:

| Model | File | Size | Parameters | Training Data | Performance |
|-------|------|------|------------|---------------|-------------|
| URL Detection | url_phishing.onnx | 312 KB | 120,321 | PhiUSIIL (235K URLs) | AUC=0.9942 |
| Chinese Text | chinese_phishing.onnx | 1,021 KB | 644,865 | ChiFraud (82K cleaned) | AUC=0.9492 |
| SMS Phishing | sms_phishing.onnx | 312 KB | 120,321 | FBS SMS + ChiFraud (11K) | Recall=97.88% |
| English Text | english_phishing.onnx | 312 KB | 120,321 | UCI + NCSU + IMC25 | TBD |
| Japanese Text | japanese_phishing.onnx | 312 KB | 120,321 | Generated Japanese SMS | TBD |

### Hyperparameters

| Parameter | URL/SMS/EN/JA Model | Chinese Model |
|-----------|---------------------|---------------|
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

# Train Japanese model
python train_phishing_model.py --mode japanese
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

### Evaluation

```bash
# Validate ONNX inference
python test_onnx_models.py

# Overfitting check
python check_fitting.py

# Model diagnostics
python diagnose_model.py
```

---

## Project Structure

```
TianshangGuard/
├── app/src/
│   ├── main/
│   │   ├── java/com/tianshang/guard/
│   │   │   ├── core/
│   │   │   │   ├── dns/          # DNS engine, homograph detection, Bloom Filter
│   │   │   │   ├── ml/           # MlEngine, OnnxMlEngine, rule engine
│   │   │   │   ├── monitor/      # Behavior monitoring (screen sharing)
│   │   │   │   ├── alert/        # Tiered alert engine
│   │   │   │   ├── optimizer/    # Battery optimization
│   │   │   │   ├── telemetry/    # Performance tracing
│   │   │   │   └── update/       # Rule update worker
│   │   │   ├── data/
│   │   │   │   ├── local/        # Room DB, preferences
│   │   │   │   ├── remote/       # GitHub rules API
│   │   │   │   └── repository/   # Data repositories
│   │   │   ├── domain/           # UseCase layer
│   │   │   ├── service/          # VPN, foreground service, SMS receiver, boot
│   │   │   ├── ui/               # Compose UI (home, sms, stats, settings, alerts)
│   │   │   └── di/               # Koin dependency injection
│   │   └── assets/
│   │       ├── model/            # ONNX model files (5 models)
│   │       └── rules/            # Built-in blacklists/whitelists
│   ├── zh/                       # Chinese flavor
│   │   ├── res/values/strings.xml
│   │   └── java/.../GuardApplication.kt  # Loads URL + Chinese + SMS
│   ├── en/                       # English flavor
│   │   ├── res/values/strings.xml
│   │   └── java/.../GuardApplication.kt  # Loads URL + English
│   ├── ja/                       # Japanese flavor
│   │   ├── res/values/strings.xml
│   │   └── java/.../GuardApplication.kt  # Loads URL + Japanese
│   └── test/                     # Unit tests
├── scripts/
│   ├── train_phishing_model.py   # Main training script (1483 lines)
│   ├── clean_chinese_data.py     # Data cleaning pipeline
│   ├── _calibrate_thresholds.py  # Threshold calibration
│   ├── validate_model.py         # Model validation
│   ├── check_fitting.py          # Overfitting detection
│   └── raw_data/                 # Training datasets
├── docs/
│   ├── report.tex                # Technical report
│   └── v1.1.0_report.tex        # v1.1.0 technical report
└── .github/workflows/ci.yml     # CI configuration
```

---

## Privacy & Security

### Core Commitments

- **On-device analysis**: All inference runs locally via ONNX Runtime with NNAPI hardware acceleration
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
- [ChiFraud](https://github.com/) — Chinese fraud SMS dataset
- [ONNX Runtime](https://onnxruntime.ai/) — On-device inference engine
- [PhishTank](https://www.phishtank.com/) — Phishing domain intelligence

---

## License

[MIT](LICENSE) © Tianshang301
