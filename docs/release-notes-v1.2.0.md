# JWSK v1.2.0 — Magisk + KernelSU Root 管理器

这一版替换了旧 ADB/Shizuku 首页，将应用入口、模块管理和 Comput 执行路径统一到 Magisk 与 KernelSU。主界面采用 Magisk 绿色与 KernelSU 蓝色，只保留首页、模块、授权、Comput、日志五个功能区。

## 更新内容

- 移除管理器中的 ADB 配对、无线调试、Shizuku Binder、守护服务、ADB 模块等旧源码与运行组件。
- 修复内置 ksud 返回内核版本 0 时的误判，Magisk 设备不会再因为内置工具被误判为双框架。
- 重新检测时重建曾被拒绝的 Root 会话，允许 JWSK 后可重新获得管理能力。
- Magisk 模块支持 ZIP 安装、启停、操作脚本和卸载标记；KernelSU 使用活动设备上的 ksud 管理模块。
- Magisk 授权页管理已记录的允许/拒绝策略并恢复询问，失败原因会显示在界面。
- Comput 改为直接 Root 子 Shell，执行前确认、120 秒超时、有限输出与结果日志。
- 保留 Magisk Boot、KernelSU LKM/自定义内核修补，以及日志复制、导出、轮转和清空。
- Rust JNI 核心升级到 1.2.0，增加版本和加载自检；Java 负责镜像与模块文件的流式 SHA-256；Kotlin 负责 Android 界面。
- 发布流水线从源码构建 Rust 双 ABI 原生库并运行测试，标签发布必须使用既有签名密钥。

## 安装与支持

支持 Android 12–16（API 31–36）。从附件下载 `JWSK-v1.2.0-release.apk`，安装后在当前 Root 框架中允许 JWSK。首次使用建议在 Comput 执行 `id` 检查授权。

KernelSU 的应用授权仍需要内核认可的管理器；JWSK 提供模块、修补、终端与日志，并明确显示这个限制。新 su 弹窗仍由设备现有 Root 框架处理。本版本不声称完整替代官方管理器全部高级功能。

镜像修补仅生成并导出文件。使用与设备和固件匹配的原厂镜像，刷入前保留恢复路径。尚未完成 Android 12–16 所有机型的真机刷写验证。
