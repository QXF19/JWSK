# JWSK v1.1.1：稳定构建与签名维护版

JWSK v1.1.1 是面向 Android 12–16 的稳定性维护版本。它保留 v1.1.0 的统一 Root 管理能力，重点修复 GitHub 自动构建、发布签名和编译警告，确保后续 APK 可以使用同一证书连续升级。

## 主要能力

- 状态驱动主界面：识别 Magisk/Kitsune、KernelSU、双框架、ADB 和未激活状态。
- 独立的 Magisk/KernelSU 模块、Magisk 授权策略、Root 日志和江望su Comput 界面。
- Magisk 30.7 Boot/init_boot 修补与 KernelSU 3.2.5 LKM/自定义内核修补。
- 精简 ADB 管理：无线配对、无线启动、启动命令、终端和 TCP 5555。
- Kotlin/Java + Rust JNI 日志完整性核心，并提供 Kotlin 自动回退。
- Android 12、13、14、15、16（API 31–36）。

## v1.1.1 修复

- 修复 GitHub Actions 中 `./gradlew: Permission denied`。
- 修复 Release/Debug APK 输出查找与附件上传路径。
- 配置与 v1.1.0 相同证书的加密自动签名，保持 Android 覆盖升级兼容性。
- GitHub Actions 升级为 Node.js 24 兼容版本。
- 修复 Release 构建缺少默认 keystore 的问题。
- 清理 `Shizuku` 的 `dep-ann` 与 `FileObserver` 构造函数弃用警告。
- 补充 Root 探测、执行器选择和完整性日志写入时序图。

## 安全边界

JWSK 只生成新的修补镜像，不自动写入设备分区。项目不包含 Shamiko、MagiskHide、完整性绕过、反检测或隐藏 Root 功能。刷写前请备份原始镜像，并确认文件与设备型号、固件版本及 KMI 完全匹配。

## 升级说明

v1.1.1 与 v1.1.0 使用同一发布证书，可直接覆盖安装。首次安装用户可下载 `JWSK-v1.1.1-release.apk`。
