# v1.2.0 检查记录（2026-10-14）

## REA 静态观察

- 目标：`.tooling/release-check/published-v1.2.0/verified-JWSK-v1.2.0-release.apk`，保留原件。
- SHA-256：`c165f2f6234cd60364bc575a1688416ce1afb04fc7f42eb912f42cd73660c12a`。
- Evidence：`ev_89fe3808a553b75e8893a537cabad0cf17a0280d60a79e7f8b0fe26ba92d40a3`。
- Inventory Evidence：`ev_3642a60337d09f820fbc659ee92c06ed3df11a1e7966b4d8473ae72f18f7425f`。
- Manifest：`agm_feffa2eb06942d4e1d7ab9ebe3f0d4c09e823b4fdfd0914685c469e2e9335397`。
- 包含 arm64-v8a 与 x86_64 的 `libksud.so`、`libjwsk_core.so`。
- 在逻辑归档成员名中未见独立的 `.ko`、`vmlinux`、`Image` 条目；这不排除可执行文件内嵌资源，也不能证明设备有没有 KernelSU。
- REA APK 反编译缺少 `REA_JADX_MCP_JAR`（jadx-headless-mcp 0.7.1），该检查未执行。
- 归档检查只覆盖静态条目，不代表执行、管理器身份认可、内核兼容性或签名验证；大小写折叠冲突也不能解释为实际文件系统冲突。

## 源码观察（非 REA 反编译结果）

- `RootManager.kt` 调用 libsu Shell 和 ksud 命令。
- `RootPolicyScreen.kt` 的 KernelSU 分支只显示限制说明，没有应用策略查询/提交实现。
- `scripts/build-rust-android.sh` 构建 JWSK 日志辅助库，不构建 KernelSU 内核。

## 下一阶段与未知项

用户指定 Google Material 3、KernelSU 新版 GKI、Magisk Alpha，先批准效果图再实现。
官方 API 实时查询显示 KernelSU v3.3.1（2026-10-12 发布）；实施时锁定源码提交并复核。
尚未提供目标设备/KMI。尚无本轮真机结果，不发布为完整 Root 管理器。
