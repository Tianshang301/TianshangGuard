# TianshangGuard（天殇·破妄）

> **如果能少一人受骗，这个项目就有意义。**

[![CI](https://github.com/Tianshang301/TianshangGuard/actions/workflows/ci.yml/badge.svg)](https://github.com/Tianshang301/TianshangGuard/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-red.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)

开源 Android 反诈工具，采用分层防御架构，**所有分析在设备本地完成，零数据上传**。

[English](../README.md)

---

## 功能特性

| 功能 | 说明 |
|------|------|
| **DNS 域名拦截** | Bloom Filter 快速过滤 + 同形字符检测（Punycode/西里尔/全角） |
| **网页钓鱼检测** | Byte-level Transformer 端侧推理（ONNX Runtime + NNAPI） |
| **短信诈骗检测** | 多模型融合：语言检测 + SMS 专家模型 + URL 提取 |
| **行为监控** | 检测屏幕共享 + 银行应用组合，阻断社会工程学攻击 |
| **分级预警** | 静默记录 → 横幅提示 → 弹窗确认 → 全屏阻断 |
| **规则更新** | 远程拉取黑白名单，支持社区贡献 |
| **多语言支持** | 中文（zh）、英文（en）、日语（ja）版本 |

---

## 技术架构

```mermaid
graph TB
    subgraph Presentation["UI 层"]
        A[MainActivity] --> B[SmsScreen]
        A --> C[StatsScreen]
        A --> D[SettingsScreen]
        A --> E[ReportScreen]
        F[AlertActivity]
    end

    subgraph Domain["Domain 层"]
        G[AnalyzeSmsUseCase]
        H[CheckDomainRiskUseCase]
        I[TriggerAlertUseCase]
    end

    subgraph Core["Core Engine 层"]
        J[DnsEngine<br/>DNS 代理 + 域名检测]
        K[MlEngine<br/>ONNX 推理 + 规则兜底]
        L[MonitorEngine<br/>行为监控]
        M[AlertEngine<br/>分级预警]
    end

    subgraph Data["Data 层"]
        N[(Room DB<br/>域名 + 预警日志)]
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

### ML 推理流程

```mermaid
flowchart LR
    A[输入文本] --> B[ByteTokenizer<br/>UTF-8 编码]
    B --> C[ONNX 模型<br/>INT8 量化]
    C --> D{风险分数}
    D -->|< 0.10| E[✅ 安全]
    D -->|0.10 ~ 0.50| F[⚠️ 可疑]
    D -->|≥ 0.50| G[🚨 危险]
    C -.->|超时/失败| H[规则引擎兜底]
    H --> D
```

### 短信检测流程

```mermaid
flowchart TD
    A[收到短信] --> B{短信监控<br/>是否开启?}
    B -->|否| C[忽略]
    B -->|是| D[SmsReceiver<br/>BroadcastReceiver]
    D --> E[AnalyzeSmsUseCase]
    E --> F[语言检测]
    F --> G[文本模型<br/>中文/英文/日语]
    F --> H[SMS 专家模型]
    F --> I[URL 提取 + 模型]
    G --> J[maxOf 融合]
    H --> J
    I --> J
    J --> K{风险等级}
    K -->|SAFE| L[静默记录]
    K -->|SUSPICIOUS| M[横幅预警]
    K -->|DANGEROUS| N[弹窗预警<br/>+ 反诈提示]

    O[手动输入] --> P[SmsScreen]
    P --> E
```

---

## 快速开始

### 环境要求

- **JDK**: 17
- **Android SDK**: 35（compileSdk）
- **Gradle**: 8.x（项目自带 wrapper）
- **设备**: Android 8.0+（API 26）

### 构建

```bash
# 克隆仓库
git clone https://github.com/Tianshang301/TianshangGuard.git
cd TianshangGuard

# 构建中文版（推荐）
./gradlew assembleZhRelease

# 构建英文版
./gradlew assembleEnRelease

# 构建日语版
./gradlew assembleJaRelease

# 安装到设备
adb install app/build/outputs/apk/zh/release/app-zh-release.apk
```

### 下载

| 版本 | 语言 | 包含模型 | 状态 |
|------|------|----------|------|
| [v1.1.0-chinese](https://github.com/Tianshang301/TianshangGuard/releases/tag/v1.1.0-chinese) | 中文 UI | URL + 中文 + SMS | ✅ 已发布 |

---

## 模型训练

项目包含五个 BytePhishingTransformer 模型：

| 模型 | 文件 | 大小 | 参数量 | 训练数据 | 性能 |
|------|------|------|--------|----------|------|
| URL 检测 | url_phishing.onnx | 312 KB | 120,321 | PhiUSIIL（23.5 万条） | AUC=0.9942 |
| 中文文本 | chinese_phishing.onnx | 1,021 KB | 644,865 | ChiFraud（8.2 万条清洗） | AUC=0.9492 |
| SMS 诈骗 | sms_phishing.onnx | 312 KB | 120,321 | FBS SMS + ChiFraud（1.1 万条） | Recall=97.88% |
| 英文文本 | english_phishing.onnx | 312 KB | 120,321 | UCI + NCSU + IMC25 | 待测试 |
| 日语文本 | japanese_phishing.onnx | 312 KB | 120,321 | 生成日语 SMS | 待测试 |

### 超参数

| 超参数 | URL/SMS/英/日模型 | 中文模型 |
|--------|-------------------|----------|
| d_model | 64 | 128 |
| n_heads | 2 | 4 |
| n_layers | 2 | 4 |
| d_ff | 128 | 256 |
| max_seq_len | 512 | 512 |
| vocab_size | 256 | 256 |

### 训练命令

```bash
cd scripts

# 训练 URL 模型
python train_phishing_model.py --mode url

# 训练中文模型
python train_phishing_model.py --mode chinese --fresh

# 训练 SMS 模型
python train_phishing_model.py --mode sms

# 训练英文模型
python train_phishing_model.py --mode english

# 训练日语模型
python train_phishing_model.py --mode japanese
```

训练完成后，模型自动导出为 ONNX INT8 量化版本并复制到 `app/src/main/assets/model/`。

### 阈值校准

训练后校准最优阈值：

```bash
python _calibrate_thresholds.py
```

当前阈值（已部署）：
- **SAFE**: 分数 < 0.10
- **SUSPICIOUS**: 0.10 – 0.50
- **DANGEROUS**: ≥ 0.50

### 评估

```bash
# 验证 ONNX 推理
python test_onnx_models.py

# 拟合检查
python check_fitting.py

# 模型诊断
python diagnose_model.py
```

---

## 项目结构

```
TianshangGuard/
├── app/src/
│   ├── main/
│   │   ├── java/com/tianshang/guard/
│   │   │   ├── core/
│   │   │   │   ├── dns/          # DNS 引擎、同形字符检测、Bloom Filter
│   │   │   │   ├── ml/           # MlEngine、OnnxMlEngine、规则引擎
│   │   │   │   ├── monitor/      # 行为监控（屏幕共享检测）
│   │   │   │   ├── alert/        # 分级预警引擎
│   │   │   │   ├── optimizer/    # 电池优化
│   │   │   │   ├── telemetry/    # 性能追踪
│   │   │   │   └── update/       # 规则更新 Worker
│   │   │   ├── data/
│   │   │   │   ├── local/        # Room 数据库、偏好设置
│   │   │   │   ├── remote/       # GitHub 规则 API
│   │   │   │   └── repository/   # 数据仓库
│   │   │   ├── domain/           # UseCase 层
│   │   │   ├── service/          # VPN、前台服务、短信接收器、开机启动
│   │   │   ├── ui/               # Compose UI（主页、短信、统计、设置、预警）
│   │   │   └── di/               # Koin 依赖注入
│   │   └── assets/
│   │       ├── model/            # ONNX 模型文件（5 个模型）
│   │       └── rules/            # 内置黑白名单
│   ├── zh/                       # 中文版本
│   │   ├── res/values/strings.xml
│   │   └── java/.../GuardApplication.kt  # 加载 URL + 中文 + SMS
│   ├── en/                       # 英文版本
│   │   ├── res/values/strings.xml
│   │   └── java/.../GuardApplication.kt  # 加载 URL + 英文
│   ├── ja/                       # 日语版本
│   │   ├── res/values/strings.xml
│   │   └── java/.../GuardApplication.kt  # 加载 URL + 日语
│   └── test/                     # 单元测试
├── scripts/
│   ├── train_phishing_model.py   # 主训练脚本（1483 行）
│   ├── clean_chinese_data.py     # 数据清洗流水线
│   ├── _calibrate_thresholds.py  # 阈值校准
│   ├── validate_model.py         # 模型验证
│   ├── check_fitting.py          # 拟合检查
│   └── raw_data/                 # 训练数据集
├── docs/
│   ├── report.tex                # 技术报告
│   └── v1.1.0_report.tex        # v1.1.0 技术报告
└── .github/workflows/ci.yml     # CI 配置
```

---

## 隐私与安全

### 核心承诺

- **纯本地分析**：所有推理通过 ONNX Runtime + NNAPI 硬件加速在设备端完成
- **开源可审计**：代码完全公开，接受社区审查
- **仅本地存储**：所有数据存储在本地 Room 数据库，用户可随时导出或删除
- **最小权限**：仅请求必要权限，用户可逐项控制

### 能力边界

**能防护**：
- 已知钓鱼域名访问
- 仿冒域名（视觉混淆、同形字符、音译混淆）
- 短信中的钓鱼话术和诈骗关键词
- 屏幕共享 + 银行应用组合的高风险操作
- 网页内容中的钓鱼话术

**不能防护**：
- 用户主动绕过保护（社会工程学核心难题）
- 电话诈骗（无网络流量特征）
- 零日钓鱼域名（未被收录）
- 加密通信内容（微信、银行 App 内 WebView）

---

## 贡献指南

```bash
# 1. Fork 仓库
# 2. 创建特性分支
git checkout -b feature/your-feature

# 3. 提交更改
git commit -m "Add your feature"

# 4. 推送分支
git push origin feature/your-feature

# 5. 创建 Pull Request
```

### 规则贡献

提交可疑域名到 `rules/community/` 目录，JSON 格式：

```json
{
  "domain": "example.com",
  "reason": "phishing",
  "source": "user_report"
}
```

---

## 致谢

- [PhiUSIIL](https://www.kaggle.com/datasets/shashwatwork/phiusiil-phishing-url-dataset) — URL 钓鱼数据集
- [ChiFraud](https://github.com/xuemingxxx/ChiFraud) — 中文欺诈短信数据集
- [ONNX Runtime](https://onnxruntime.ai/) — 端侧推理引擎
- [PhishTank](https://www.phishtank.com/) — 钓鱼域名情报

---

## License

[MIT](LICENSE) © Tianshang301
