# JWSK / 江望sk v1.0.0

JWSK 的第一个正式版本，面向 Android 12–16，将权限服务、Magisk 30.7 修补、KernelSU 3.2.5 LKM 修补和自定义内核修补集中在一个离线工具中。

## 主要功能

- 独立应用 ID `cn.jiangwang.jwsk`、JWSK 权限名、图标和正式发布证书。
- 支持 Android 12、13、14、15、16（API 31–36）。
- 使用官方 Magisk 30.7 组件修补 boot/init_boot。
- 使用官方 KernelSU 3.2.5 `ksud` 修补 LKM 镜像。
- 支持使用用户提供的匹配内核生成 KernelSU 镜像。
- 显示输入与输出 SHA-256、修补日志和镜像格式。
- 只将新镜像导出到用户选择的位置，不自动写入任何设备分区。

## 架构

- Magisk：arm64-v8a、armeabi-v7a、x86、x86_64。
- KernelSU：arm64-v8a、x86_64。

## 安全说明

请仅使用与设备当前固件完全匹配的原厂 boot/init_boot，并提前备份。错误的分区、KMI、内核、压缩格式或安全补丁级别可能造成无法启动。JWSK 不包含 root 隐藏、Shamiko、反检测或完整性绕过功能。

## 文件校验

- APK: `JWSK-v1.0.0-release.apk`
- SHA-256: `325CE9C9252D02D8C1E112FF7BAA2AFA0B4F95B4EBC7A907F8E86D2312A23BE2`
- 签名证书 SHA-256: `1f934155f4ffc1aeefb44faa15bdbef277ca4472435ed45e09fa583b9d67a76f`

此项目并非 Magisk、KernelSU、Shizuku 或 Shevery 官方产品。第三方来源和许可证见仓库中的 `THIRD_PARTY_NOTICES.md`。
