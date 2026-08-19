# JWSK / 江望sk

> 面向 Android 12–16 的开源 Root 管理器、权限服务与启动镜像修补工具。一个 App 中整合 Magisk 30.7、KernelSU 3.2.5、ADB、模块、Comput 和日志工作流。

![Android](https://img.shields.io/badge/Android-12--16-3DDC84?logo=android&logoColor=white)
![Magisk](https://img.shields.io/badge/Magisk-30.7-00AF9C)
![KernelSU](https://img.shields.io/badge/KernelSU-3.2.5-6C5CE7)
![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)

JWSK（江望sk）以独立应用 ID `cn.jiangwang.jwsk` 发布。v1.1 主界面融合 KernelSU 的状态优先布局与 Kitsune/Magisk 的功能分区：应用会识别 KernelSU、Magisk、双框架、ADB 和未激活状态，并选择对应执行器。镜像修补在设备本地完成，结果通过 Android 文件选择器导出；应用不会自行写入设备分区。

## 核心功能

- Android 12、13、14、15、16：最低 API 31，目标 API 36。
- 状态驱动主界面：区分内核修补、Boot 修补、ADB 激活和双框架冲突，优先展示当前可用的 Root 能力。
- Kitsune/Magisk 模块：读取标准 `/data/adb/modules`，支持 ZIP 安装、启停、操作脚本和重启后卸载。
- KernelSU 模块：通过官方 `ksud module` 生命周期命令安装、启停、执行与卸载。
- Magisk 应用授权：读取并管理 Magisk 30.7 已记录的允许/拒绝策略，可恢复为首次询问。
- 精简 ADB 管理：保留无线配对、无线启动、启动命令、终端和 TCP 5555 等主要入口。
- 江望su Comput：保留独立命令与诊断界面，不与模块操作自动串联。
- 日志管理：记录框架检测、模块、授权和镜像修补摘要，支持 1 MB 轮转、敏感字段隐藏、复制、导出与清空。
- Kotlin/Java + Rust：Android 服务与 UI 使用 Kotlin/Java，依赖为零的 Rust JNI 核心为日志记录生成完整性标记；缺少原生 ABI 时自动使用 Kotlin 回退。
- Magisk 30.7 镜像修补：集成官方 `magiskboot`、`magiskinit`、BusyBox、补丁脚本及 Stub 资源。
- KernelSU 3.2.5 LKM 修补：可选择原厂 `boot` / `init_boot`，并可填写设备 KMI。
- KernelSU 自定义内核修补：将用户提供的匹配内核写入新镜像。
- 镜像检查：导入后识别 Android boot/vendor_boot 文件头并显示输入 SHA-256。
- 结果校验：修补完成后显示输出文件名、SHA-256 和引擎日志。
- 安全导出：只生成新文件，不直接调用 `dd`、fastboot 或分区刷写接口。
- 独立品牌：新的应用 ID、权限名、启动图标和发布签名，与上游应用分开安装。

## 修补模式

| 模式 | 输入 | 适用情况 | 输出 |
| --- | --- | --- | --- |
| Magisk 30.7 | 原厂 `boot.img` 或 `init_boot.img` | 希望使用 Magisk 用户空间与模块体系 | `JWSK-magisk-30.7.img` |
| KernelSU 3.2.5 LKM | 原厂 `boot.img` / `init_boot.img`，可选 KMI | 设备内核与 KernelSU 内置 LKM 兼容 | `JWSK-kernelsu-lkm-3.2.5.img` |
| KernelSU 自定义内核 | 原厂镜像 + 匹配设备/KMI 的内核文件 | 已自行构建或取得可信的 KernelSU 内核 | `JWSK-kernelsu-kernel-3.2.5.img` |

> Android 13 及之后的部分设备需要修补 `init_boot`，但分区布局由厂商和设备决定，不能只按 Android 版本判断。请以设备官方固件和对应项目文档为准。

## Root 管理模式

完整的启动探测、执行器选择与日志写入时序见
[Root detection and journaling flow](docs/root-detection-and-journaling.md)。

| 当前环境 | JWSK 使用的管理能力 | 限制 |
| --- | --- | --- |
| Magisk / Kitsune | Magisk 模块、Magisk 授权策略、Boot 修补、Comput、日志 | 新的 su 请求仍由 Magisk 守护进程确认 |
| KernelSU | `ksud` 模块、LKM/自定义内核修补、Comput、日志 | 量产 KernelSU 内核只允许其认可签名的管理器修改应用授权 |
| ADB 激活 | ADB 模块、应用权限服务、无线调试、终端、Comput | 不具备系统 Root 授权数据库 |
| Magisk + KernelSU | 只读提示与安全工具 | 为避免数据库/模块冲突，自动模块安装被锁定 |

KernelSU 的管理器身份由内核校验，不是改包名就能兼容。JWSK 不伪造或绕过该校验；在这种设备上，KernelSU 应用授权需使用该内核认可的管理器。JWSK 仍可稳定提供模块、镜像修补、ADB、Comput 与日志功能。

## 使用方法

1. 从设备厂商的完整固件中取得与当前系统版本一致的原厂 `boot.img` 或 `init_boot.img`。
2. 备份原始镜像，并确认 Bootloader 已解锁、电脑端 fastboot 可用于恢复。
3. 打开江望sk，进入“JWSK 镜像修补中心”。
4. 选择原厂镜像和修补引擎。
5. KernelSU 模式下按设备内核填写 KMI，例如 `android13-5.10`；自定义内核模式还需选择匹配的内核文件。
6. 阅读风险提示并确认，等待修补完成。
7. 保存导出的镜像，记录界面显示的 SHA-256。
8. 在充分了解设备分区布局后，通过设备支持的恢复/fastboot 流程手动刷入。

## 重要警告

- 刷入错误分区、错误 KMI、错误压缩格式或不匹配安全补丁级别的镜像，可能导致无法启动或数据丢失。
- 不要使用其他机型、其他固件版本或来历不明的 boot/init_boot/内核文件。
- 升级系统后应基于新版本原厂镜像重新修补，不要直接复用旧输出。
- JWSK 不承诺所有 Android 12–16 设备都能使用同一种修补方式；厂商启动链和内核配置存在差异。
- JWSK 不包含 Shamiko、MagiskHide、完整性绕过、反检测或隐藏 root 能力。
- “Kitsune”在本项目中指兼容 Magisk 模块工作流与界面分区，不代表集成其隐藏/反检测功能。

## 架构支持

| ABI | Magisk 修补 | KernelSU 修补 |
| --- | --- | --- |
| arm64-v8a | 支持 | 支持 |
| armeabi-v7a | 支持 | 当前不提供 |
| x86_64 | 支持 | 支持 |
| x86 | 支持 | 当前不提供 |

KernelSU 主要面向 arm64 GKI 设备。界面可随 APK 安装在其他 ABI 上，但只有包含 `ksud` 的 ABI 才能执行 KernelSU 修补。

## 构建

环境要求：

- JDK 21
- Android SDK Platform 37 / Build Tools 37.0.0
- Android NDK 29.0.14206865
- CMake 3.22.1
- Rust stable（仅构建 `native/jwsk-core`，无第三方 Rust 依赖）

```bash
git clone --recurse-submodules https://github.com/QXF19/JWSK.git
cd JWSK
pwsh ./scripts/build-rust-android.ps1
./gradlew :manager:assembleDebug
```

正式发布请在仓库根目录创建不纳入版本控制的 `signing.properties`，并妥善备份对应 keystore：

```properties
KEYSTORE_FILE=../release.keystore
KEYSTORE_PASSWORD=your_store_password
KEYSTORE_ALIAS=jwsk
KEYSTORE_ALIAS_PASSWORD=your_key_password
```

随后运行 `./gradlew :manager:assembleRelease`。

## 项目来源与许可证

JWSK 是独立衍生项目，并非 Magisk、KernelSU、Shizuku 或 Shevery 的官方产品，也不受这些项目作者背书。

- 权限服务与管理界面衍生自 [Shevery](https://github.com/HmnDev-Tech/shevery) / [Shizuku](https://github.com/RikkaApps/Shizuku)，原始部分采用 Apache-2.0。
- 镜像修补组件来自 [Magisk](https://github.com/topjohnwu/Magisk)，采用 GPL-3.0。
- Magisk 30.7 管理器交互参考 [QOS3/Magisk](https://github.com/QOS3/Magisk) 的 GPL-3.0 源码；JWSK 不包含该分支的隐藏或反检测改动。
- KernelSU 用户空间组件来自 [KernelSU](https://github.com/tiann/KernelSU)，采用 GPL-3.0-or-later；其内核部分采用 GPL-2.0-only。

组合发布的 JWSK 源代码按 **GPL-3.0-or-later** 提供。Apache-2.0 原文、第三方说明和上游发行包校验值见 [LICENSES/Apache-2.0.txt](LICENSES/Apache-2.0.txt)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 [VENDOR_CHECKSUMS.md](VENDOR_CHECKSUMS.md)。各上游组件仍归原作者所有，并适用各自许可证和声明。

## 当前验证状态

- 已完成 Windows 环境的 APK 构建和静态检查。
- 已检查应用 ID、SDK 范围、ABI、签名和打包后的修补资源。
- 尚未覆盖所有品牌设备的真实刷机测试；欢迎提交仅包含非敏感信息的设备型号、固件版本、分区类型和完整修补日志。

## 贡献

提交问题时，请附上 Android 版本、设备型号、当前固件版本、选择的引擎、KMI（如适用）和 JWSK 日志。请勿上传个人数据、完整设备备份、密钥或受版权保护的厂商固件。
