# JWSK · 江望 sk

面向 Android 14 及以上的独立 Root 管理器重构项目。
以 KernelSU 原生管理能力为主体，接入 Magisk Alpha，采用 Google 风格的信息结构、
Miuix 原生控件与可关闭的液态玻璃效果，保留 furry 主题、Comput 和日志。

**当前为 1.3.0-dev 开发阶段，不是已完成的双后端稳定版。**
仓库历史中的 v1.2.0、旧版截图和旧版 APK 不代表本次原生重构。
请先阅读验证状态，再决定是否自行构建或测试。

## 当前进度

| 部分 | 已完成的工作 | 尚未证明的能力 |
| --- | --- | --- |
| KernelSU | 导入 v3.3.1 源码；保留超级用户、应用配置、模块及设置实现；按 JWSK 包名与原签名构建 LKM、ksuinit、ksud | 实际内核上的管理器识别、授权、模块生命周期及重启验证 |
| 原生界面 | Android 模拟器运行首页、工具、API 设置、设备详情；显示实际状态与设备数据 | 完整深色、大字体、平板和实体设备覆盖 |
| AI 助手 | 13 种服务适配；聊天、文本建议、风险提醒与测试建议；配置加密；切换与取消测试 | 使用真实凭据逐一调用全部服务 |
| GKI 产物 | 选择已编译内核或镜像；格式、大小及 SHA-256 检查；仅保存私有副本 | 设备兼容性、签名真实性与可启动性保证 |
| Magisk Alpha | Android 14 模拟器冷启动通过；原签名识别、真实授权、策略、模块启停、设置与排除列表、离线 init_boot 修补已验证 | 实体设备与跨厂商启动兼容性、完整发布验收 |
| 发布 | KSU 原生构建流程已生成使用原签名的开发 APK | 本次重构的正式 Release、跨机型稳定性验收 |

首次 KSU 全流程成功构建见
[原生后端验证记录](https://github.com/QXF19/JWSK/actions/runs/38557933833)。
该次构建包括 8 组 KMI、arm64/x86_64 守护程序、Rust 检查、Android 单元测试及签名校验。
构建通过不等于已在手机上取得 Root。后续本地修改也不自动属于该次构建产物。
详细证据与限制见 [验证记录](docs/native-validation.md)。

## 一套清晰的入口

底部保留四个页面：

- **主页**：真实的“工作 / 不工作”状态、原因和内核信息。
- **超级用户**：以 KernelSU 的原生授权与应用配置实现为基础，不伪造已授权状态。
- **模块**：保留 KernelSU 模块管理、动作与 WebUI 等上游能力。
- **工具**：仅有 AI 助手、手动 Comput、日志管理。

设置统一从右上角进入，API 配置只放在设置内。
镜像相关操作只放在“安装与修补”；点击内核版本进入设备详情，
展示管理器版本、完整内核版本、设备型号、系统指纹、SELinux 和 Seccomp 状态。
长文本使用原生文字控件，可换行、选择和复制，不把效果图文字当作界面实现。

## 独立管理器，而不是外部授权壳

应用 ID 为 cn.jiangwang.jwsk。
配套 KSU 内核必须认可 JWSK 的包名、公钥证书和接口版本。
应用不能通过安装 APK 就让任意手机获得 Root，也不会靠删除签名检查来宣称管理器可用。

配套 LKM 继续严格比较证书长度和 SHA-256。
为兼容 JWSK 现有的 1316 字节证书，证书解析器使用有上限的堆分配；
并未取消证书验证。发布构建还会核对后端来源、版本、包名和二进制摘要，
缺失匹配后端或原发布签名时直接失败，不回退到调试签名。

未具备可用后端时，界面说明实际原因。
本地开发代码已移除向其他 Root 管理器尝试普通 su 授权的回退逻辑；
此时离线操作只使用普通应用权限。该变化仍需随下一次源码上传和运行验证交付。

## 安装与修补

| 路线 | 输入与职责 | 边界 |
| --- | --- | --- |
| KernelSU LKM | 使用与设备 KMI、架构及 JWSK 管理器身份匹配的模块与镜像 | 并非所有厂商内核都兼容 |
| Magisk Alpha | 选择 Boot / init_boot，修补私有副本并保存新镜像 | 已验证模拟器离线修补；不自动刷写，设备启动兼容性需另行验证 |
| 用户编译的 GKI | 接收用户自行编译好的内核或镜像产物 | App 不编译整核，不把 SHA-256 当作兼容性或签名证明 |

GKI 产物接收与 Boot 修补使用不同的输入规则：
只有 ramdisk 的 init_boot.img 可以用于相应镜像修补，但不是已编译的 GKI 内核。
不根据手机品牌或单一 Android 版本猜测正确分区。

修补前请保留原厂镜像，确认固件、启动链、分区和恢复方案。
错误镜像可能导致无法启动或数据丢失。产品目标是生成新镜像后由用户处理；
不会因导入文件、打开页面或收到 AI 建议而自动刷写分区。

## AI 与 Comput 各司其职

AI 助手提供聊天、命令推荐、风险提醒和测试建议，只处理用户在对话中提供的信息。
它的网络适配层不依赖 Root、Shell、JNI、ADB 或设备控制接口；
不注册工具调用，也不会把模型返回的命令自动交给终端。
Comput 是独立手动终端，用户输入命令并确认后才执行。

支持的服务配置：

| 服务类型 | 提供方 |
| --- | --- |
| 原生协议 | OpenAI Responses、Azure OpenAI、Anthropic、Google Gemini、Ollama、AWS Bedrock |
| OpenAI 兼容协议 | DeepSeek、GLM、Kimi、Qwen、MiniMax、Mistral、xAI |

Azure 配置包含 deployment 与 API version；Bedrock 区分 Bearer 和 AWS 签名认证。
各服务可保存独立配置。当前提供商的必填项成功保存后，“未配置”提示消失；
一次网络失败不会擅自清除配置或将已配置状态改回未配置。

凭据由 Android Keystore 加密保存在禁止备份的私有目录，不写入日志、导出或源码。
保存配置不会自动发起请求，也不自动上传系统指纹、设备日志、镜像或模块文件。
默认使用 HTTPS；Ollama 仅对本机回环地址开放 HTTP，局域网地址需要 HTTPS。

## 界面与技术路线

- Google 风格的信息层级、系统字体与 Android 原生启动过程。
- [Miuix](https://github.com/compose-miuix-ui/miuix) 控件与玻璃效果；保留关闭选项和普通表面回退。
- furry 主题与独立绘制的原生文字、状态、按钮，不使用整张效果图冒充可交互页面。
- Kotlin / Compose 负责界面；Rust 用于真正的 KSU 守护程序与原生后端；Java 用于镜像流式检查和构建期签名工具。
- 保留 KSU 的主题、授权、模块、日志与内核设置；通用设置统一入口，不合并语义不同的后端开关。

## 支持范围

- 新管理器最低 Android 14（API 34），目标 Android 16（API 36）。
- 当前管理器构建架构为 arm64-v8a、x86_64。
- 面向不同品牌和机型设计，不等于承诺任何内核、任何固件都能 Root。
- 图中的设备信息仅用于版式参考，不代表实体机适配或验证结果。

## 构建与签名

原生重构位于 kernelsu/manager。根目录历史 manager 工程及旧发布流程仍保留，
不能用它们的旧构建结果证明新管理器完成。

开发依赖：JDK 21、Android SDK / Build Tools 37.0.0、NDK 29.0.14206865、
CMake 3.22.1，以及仓库指定的 Rust 工具链。

推荐先运行 [原生后端工作流](.github/workflows/jwsk-native.yml)，
从同次源码构建 JWSK LKM、ksuinit、ksud，生成后端回执，再构建管理器。
拥有匹配的守护程序文件后，本地 Android 测试入口为：

~~~bash
cd kernelsu/manager
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:assembleDebug
~~~

正式签名使用原有 JWSK 发布身份，通过本地忽略的配置或 GitHub 加密 Secrets 提供。
**不要提交 keystore、密码、API Key 或完整签名配置。**
Alpha 原生构建与许可证说明见 [后端目录](backends/magisk-alpha/README.md)。
APK 协议封装在临时副本上完成，随后重新签名并验证 v2/v3，不在签名后直接篡改 ZIP 注释。

## 来源与许可证

JWSK 是独立衍生项目，不是 KernelSU、Magisk、Alpha 或 Miuix 官方产品，也不代表上游作者背书。

- KernelSU 基线：[v3.3.1](https://github.com/tiann/KernelSU/tree/v3.3.1)，
  commit 0c0fdb960178fa12d80d6cbcfdcb6ad8be6a865d。
  内核、用户空间和管理器保留各自上游许可证与署名，见 [来源记录](kernelsu/JWSK-UPSTREAM.md)。
- Magisk Alpha 基线：[vvb2060/Magisk](https://github.com/vvb2060/Magisk)，
  commit 96221b69fae9910b1c0c75c2f92a4ebb2c2dc698。
  保留 [上游 GPL 许可证](backends/magisk-alpha/LICENSE)、补丁及构建来源。
- 历史组件说明见 [第三方声明](THIRD_PARTY_NOTICES.md) 与 [历史组件摘要](VENDOR_CHECKSUMS.md)。
  这些历史清单不自动证明本次开发包中使用的后端版本。

各组件的权利归原作者所有；组合发布不得取消上游许可证、署名或对应源码义务。

## 反馈

提交问题时请注明 JWSK 版本、Android 版本、架构、内核/KMI、实际使用的后端及复现步骤。
日志请先脱敏，不要上传 API 凭据、私钥、个人数据或与问题无关的设备文件。
