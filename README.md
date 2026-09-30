# JWSK / 江望sk

> 面向 Android 12–16 的 Magisk + KernelSU 双框架 Root 管理器，提供 Root 探测、模块管理、授权策略、Comput 超级用户终端、日志和 Boot/内核镜像修补。

![Android](https://img.shields.io/badge/Android-12--16-3DDC84?logo=android&logoColor=white)
![Magisk](https://img.shields.io/badge/Magisk-30.7-00AF9C)
![KernelSU](https://img.shields.io/badge/KernelSU-3.2.5-5277C3)
![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)

JWSK（江望sk）使用独立应用 ID `cn.jiangwang.jwsk`。v1.2.0 已彻底改为 Root-only 产品：运行入口、界面、清单组件、原生构建和依赖链均不再包含 ADB 配对、无线调试、Shizuku Binder、ADB 模块或后台 Watchdog。

## v1.2.0 核心能力

- Root 状态首页：自动识别 Magisk、KernelSU、双框架冲突和未授权状态。
- Magisk/Kitsune 模块管理：安装 ZIP、启停模块、执行 `action.sh`、标记卸载。
- KernelSU 模块管理：使用 `ksud module` 官方命令完成安装、启停、操作和卸载。
- Magisk 授权策略：查看和修改已记录的 su 允许/拒绝策略，并可恢复首次询问。
- KernelSU 安全边界：不伪造官方管理器签名；模块、镜像修补、Root 终端和日志可以使用，应用授权仍由内核认可的管理器处理。
- 江望su Comput：直接通过 Magisk/KernelSU 授权的 Root Shell 执行命令；执行前强制确认，不经过 ADB 或 Shizuku。
- 操作日志：记录 Root 探测、模块、授权、补丁和 Comput 结果摘要；支持轮转、复制、导出和清空。
- Magisk 30.7 Boot 修补：集成 `magiskboot`、`magiskinit`、BusyBox 和补丁脚本。
- KernelSU 3.2.5 修补：支持 LKM 路线及自定义 KernelSU 内核替换。
- 安全导出：只生成新的镜像文件，不自动刷写分区，不调用 `dd` 或 fastboot。
- Rust + Java + Kotlin：Rust JNI 生成日志校验标记；Java 流式计算大文件 SHA-256；Kotlin 负责界面与框架交互。日志页显示实际引擎，原生库加载失败时使用 Kotlin 回退。

## 主界面

底部仅保留五个 Root 功能区：

1. **首页**：框架状态、Root 授权、Boot/内核修补入口。
2. **模块**：根据当前框架显示 Magisk/Kitsune 或 KernelSU 模块管理。
3. **授权**：Magisk 策略管理；KernelSU 显示真实的内核签名限制说明。
4. **Comput**：直接 Root 命令终端。
5. **日志**：JWSK 操作审计、复制和导出。

## 镜像修补

| 模式 | 输入 | 输出 | 适用场景 |
| --- | --- | --- | --- |
| Magisk 30.7 | 原厂 `boot.img` / `init_boot.img` | `JWSK-magisk-30.7.img` | Magisk 用户空间、授权与模块体系 |
| KernelSU 3.2.5 LKM | 原厂镜像，可选 KMI | `JWSK-kernelsu-lkm-3.2.5.img` | 兼容 KernelSU LKM 的 arm64/x86_64 内核 |
| KernelSU 自定义内核 | 原厂镜像 + 匹配内核 | `JWSK-kernelsu-kernel-3.2.5.img` | 已取得与设备、固件、KMI 匹配的内核 |

Android 13 及以后部分设备需要修补 `init_boot`，但不能只按 Android 版本判断分区。请以设备官方固件和上游文档为准。

## 支持范围

- Android 12–16（最低 API 31，目标 API 36）。
- Magisk 修补：arm64-v8a、armeabi-v7a、x86_64、x86。
- KernelSU 修补：arm64-v8a、x86_64。
- KernelSU 主要面向 arm64 GKI 设备；厂商内核、KMI、启动链和分区差异可能影响兼容性。

## 安全说明

- 刷入不匹配机型、固件、安全补丁级别、KMI 或压缩格式的镜像可能导致无法启动或数据丢失。
- 修补前必须备份原厂镜像，并确保 Bootloader 已解锁且具备可用的恢复路径。
- 系统升级后应从新固件提取原厂镜像重新修补。
- JWSK 不包含 Shamiko、MagiskHide、完整性绕过、反检测或隐藏 Root 功能。
- 检测到 Magisk 与 KernelSU 同时活动时，JWSK 会锁定自动模块安装，避免同时修改两套状态。

## 构建

环境要求：JDK 21、Android SDK Platform 37、Build Tools 37.0.0。

```bash
git clone --recurse-submodules https://github.com/QXF19/JWSK.git
cd JWSK
./gradlew :manager:assembleDebug
```

正式发布通过 `signing.properties` 或 GitHub Actions 加密 Secrets 提供签名参数。不要把真实 keystore 或密码提交到 Git 历史。

发布 CI 从源码构建 Rust arm64-v8a/x86_64 原生库，并运行 Rust 单元测试和 Android 框架识别回归测试。Windows 本地构建原生库使用 `scripts/build-rust-android.ps1`；Linux 使用 `scripts/build-rust-android.sh`。Rust 日志标记用于诊断校验，不是防篡改签名。

## 项目来源与许可证

JWSK 是独立衍生项目，并非 Magisk 或 KernelSU 官方产品，也不受其作者背书。

- 镜像修补组件来自 [Magisk](https://github.com/topjohnwu/Magisk)，采用 GPL-3.0。
- Magisk 30.7 管理交互参考 [QOS3/Magisk](https://github.com/QOS3/Magisk) 的 GPL-3.0 源码；不包含隐藏或反检测改动。
- KernelSU 用户空间组件来自 [KernelSU](https://github.com/tiann/KernelSU)，采用 GPL-3.0-or-later；其内核部分采用 GPL-2.0-only。
- 仓库历史中的旧界面来源和完整第三方声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 [VENDOR_CHECKSUMS.md](VENDOR_CHECKSUMS.md)。

组合发布的 JWSK 源代码按 **GPL-3.0-or-later** 提供。各上游组件仍归原作者所有，并适用各自许可证。

## 验证状态

- Root-only Kotlin 编译链已通过。
- 发布前检查 APK 清单、签名、ABI、内置修补引擎和 SHA-256。
- 真实设备兼容性仍取决于机型、固件、内核和 Root 框架版本；提交问题时请附非敏感设备信息与 JWSK 日志。
