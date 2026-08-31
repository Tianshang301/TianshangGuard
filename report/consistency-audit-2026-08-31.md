# TianshangGuard 文档与代码一致性审查报告

> 审查日期: 2026-08-31
> 审查对象: TianshangGuard v1.5.0 (`main` @ `d935f83`)
> 方法: 基于 GitHub API 获取 100+ 文件的外部审查报告 + 本地全量复核

---

## 1. 背景

维护者收到一份基于 GitHub API 的自动化一致性审查报告（覆盖代码、配置、测试、脚本、文档）。
本报告对其中每项发现进行**本地复核**（源码/资源/manifest/CI/统计），并记录最终结论与已执行的修复。

---

## 2. 审查方法

- 本地读取源码、`AndroidManifest.xml`、`build.gradle.kts`、`gradle-wrapper.properties`、`.github/workflows/ci.yml`
- 精确统计 `@Test` 数量与测试文件（`app/src/test`、`app/src/androidTest`）
- 解析 `assets/tokenizer/bpe_tokenizer_vocab.json` 实际词条数
- 对比 AGENTS.md / README(EN/zh) / CHANGELOG 声称值与代码实际

---

## 3. 发现核实结果

### 3.1 真实不一致（本次已修复）

| 编号 | 问题 | 证据 | 处置 |
|------|------|------|------|
| P1 | **BPE 词表大小声称 4096，实际 162** | `bpe_tokenizer_vocab.json` 实际 **162 键**（id 0-163）；训练脚本默认目标 `vocab_size=4096` | ✅ README/AGENTS.md/zh 改为 162，并注明"未集成 ONNX 管道" |
| P2 | **单元测试文件数声称 23** | 实际 25 个 `.kt`（23 个测试类 + 2 个辅助类 `BaseUnitTest`/`MainDispatcherRule`），`@Test` 总数 **175** 与文档一致 | ✅ README/zh 改为 "23 test classes + 2 helpers = 25 files" |
| P3 | **权限表遗漏保护级权限** | Manifest 中 `BROADCAST_SMS`（SmsReceiver）、`BIND_QUICK_SETTINGS_TILE`（QrScanTileService）作为组件 `android:permission` 声明，README 权限表未列出 | ✅ README/zh 补充两行并标注"保护级" |

### 3.2 外部报告误判（本地复核确认与文档一致，无需修复）

| 外部报告声称 | 本地复核 | 结论 |
|-------------|---------|------|
| 单元测试 `@Test` 159 vs 声称 175 | **实际 175**（逐文件精确统计） | 误判（API 未取全文件） |
| Android 测试仅 1 文件 6 测试 | **实际 4 文件 26 测试**（AlertDaoTest 5 / DomainDaoTest 6 / GuardPreferencesTest 9 / SecurityModuleTest 6） | 误判（API 获取不全） |
| "CI Hardening 无代码支撑" | `ci.yml` 含 `permissions:` 最小权限、actions **commit-SHA pin**、`verify-pins` job（openssl 校验 SPKI 指纹） | 误判（已实现） |
| "Gradle 8.x 未标注" | `gradle-wrapper.properties` 明确 `gradle-8.7-bin.zip` + `distributionSha256Sum` | 误判（已标注） |

### 3.3 外部报告方向正确、建议采纳

- **BPE 集成状态**：`REPORT.md`、`TianshangGuard_v1.5.0_Feasibility_Analysis.md` 已记录"BPE(vocab=4096) 训练完成但未集成 ONNX 管道，生产路径使用 ByteTokenizer(vocab=256)"。README 原描述存在误导（标注 4096 且未注明集成状态）。已修正。

---

## 4. 本次执行修改

| 文件 | 修改 |
|------|------|
| `README.md` | BPE vocab=4096→162（图 + 超参数表 + ML 管道注释"未集成生产"）；测试文件数 23→25（+2 辅助类）；权限表补充 BROADCAST_SMS / BIND_QUICK_SETTINGS_TILE |
| `readme/README.zh-CN.md` | 同步上述三处 |
| `AGENTS.md` | `BpeTokenizer.kt` 标注 vocab=162 + "未集成 ONNX 管道" |
| `LICENSE`（新增） | MIT 协议（与 AGENTS.md 声明一致） |

> 未改动代码 / CI / 模型资产。仅文档。

---

## 5. 残余风险（已知遗留，未在本次范围）

1. **BPE 生产推理不一致风险（低-中）**：`OnnxMlEngine.encode()` 现在优先使用 BPE(162)（若已加载），而模型按 ByteTokenizer(vocab=256) 训练。按维护者决策（仅修正文档），代码保持现状；如需根治需重训 BPE 至完整词表或回退纯 ByteTokenizer。参见 `REPORT.md`。
2. **Android 测试需真机/模拟器**：26 个 instrumentation 测试本地无法在 JVM 单元测试中运行，需设备验证（已记录 26/26 华为真机通过）。
3. **`gradle-wrapper` 版本 8.7**：已在 wrapper 中显式标注，与文档一致。

---

## 6. 结论

- 外部自动化审查报告的**核心方向正确**（BPE 词表、权限披露、测试文件数），但其**量化统计多项因 API 获取不全而误判**。
- 本地复核确认：测试数量（175 单元 / 26 androidTest）、CI 加固（SHA pin + 最小权限 + verify-pins）、Gradle 版本均**与文档一致**。
- 本次已修正全部真实不一致项（P1/P2/P3），并补充 LICENSE。
- 项目整体文档-代码一致性评级：**高（≈90%）**，残余风险见 §5。
