# JWSK v1.1.0：统一 Root 管理主界面

JWSK v1.1.0 将 KernelSU、Magisk/Kitsune 和 ADB 三条工作流统一到同一个状态驱动的中文主界面，同时继续支持 Android 12–16。

## 新增

- 自动识别 KernelSU、Magisk、ADB、未激活和双框架冲突状态。
- KernelSU 风格大状态卡与 Kitsune/Magisk 分区式快捷入口。
- Kitsune/Magisk 标准模块安装、启停、操作脚本和卸载标记管理。
- KernelSU `ksud module` 标准模块生命周期管理。
- Magisk 30.7 已记录的 Root 应用策略管理。
- 独立日志中心：轮转、完整性标记、敏感字段隐藏、复制、导出和清空。
- 精简 ADB 卡片，保留无线配对、启动、命令、终端和 TCP 5555。
- Rust + Kotlin/Java 混合核心；Rust 组件无第三方依赖并提供 Kotlin 回退。

## 保留

- 江望su Comput 命令与诊断界面。
- Magisk 30.7 Boot/init_boot 离线修补。
- KernelSU 3.2.5 LKM 与自定义内核离线修补。
- ADB/root 权限服务与 ADB 模块模式。

## 稳定性和安全边界

- 双 Root 框架下锁定自动模块安装，避免同时改写模块目录和数据库。
- 所有模块 ID 均经过白名单校验；模块路径不会直接拼接未校验输入。
- 镜像修补仍只导出新文件，不自动刷写设备分区。
- KernelSU 应用授权遵循内核管理器签名校验，JWSK 不伪造或绕过管理器身份。
- 不包含 Shamiko、MagiskHide、完整性绕过、Root 隐藏或反检测功能。
