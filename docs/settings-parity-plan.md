# 设置与模块接入清单（待实现，非已完成功能）

## 锁定的参考

- KernelSU v3.3.1：`0c0fdb960178fa12d80d6cbcfdcb6ad8be6a865d`。
- Magisk Alpha 发布源：`vvb2060/Magisk`，本轮查询为 `31000 / 96221b69-alpha`。
- Miuix v0.9.4：用户指定 compose-miuix-ui；读取 `docs/zh_CN/guide/blur.md` 与 `example/.../liquid/LiquidGlassNavigationBar.kt`。

## 已读取的 KernelSU 设置源码

`manager/app/src/main/java/me/weishu/kernelsu/` 下：

- `ui/screen/settings/SettingsUiState.kt`
- `ui/viewmodel/SettingsViewModel.kt`
- `data/repository/SettingsRepository.kt`
- `Natives.kt`

原文：https://github.com/tiann/KernelSU/tree/v3.3.1/manager/app/src/main/java/me/weishu/kernelsu

## 需要保留的设置能力

| 分组 | 上游状态/动作 | 实施要求 |
|---|---|---|
| 更新 | 应用检查更新、模块检查更新 | 独立版本源，不用假版本数据 |
| 主题 | UI 模式、浅深色/AMOLED、Monet、自定义关键色、调色风格、色彩规范 | Google 为默认，保留上游可选择能力 |
| 操作与显示 | 预测返回、滑动关闭、分页拦截模式、页面缩放、导航徽章/导航栏展开 | 尊重系统设置，保留状态与范围限制 |
| 材质 | 模糊、悬浮底栏、底栏模糊 | 液态玻璃可关，平台与性能能力检查 |
| 模块显示 | 描述最大行数、已启用优先、操作脚本优先、仓库排序 | 原模块实现，不简化替换 |
| 超级用户显示 | 显示系统应用、仅主用户、排序、配置模板 | 配套内核原生接口，不能依赖普通 su Shell |
| SU 兼容 | 启用、暂时停用至重启、始终停用 | forced 状态优先；成功后持久化并读回验证 |
| 内核特性 | kernel umount、SELinux hide、SU log | 检查 supported/forced 状态，处理失败和重启要求 |
| 卸载模块 | 默认对未获 Root 的应用卸载模块 | 与默认应用 Profile 接口对应 |
| Shell/ADB | ADB Root、Shell SU 仅限 ADB | 属于 KSU 内核特性，不恢复旧 ADB 配对框架；不自动开启 |
| 调试与日志 | Web 调试、SU 日志过滤 | 保留原能力，明确调试开关风险 |
| 特定加载模式 | 自动 jailbreak、软重启偏好、LKM/late-load 模式 | 按上游条件显示；GKI 不伪装为其他模式 |
| 信息 | 关于 | 上游来源、版本与许可证真实可查 |

此表为源码映射与验收计划，不代表上述能力已实现或通过运行验证。
Magisk Alpha 专属设置尚需逐项读取对应源码/发行包；不能假定与 KSU 完全等价。

## 管理器身份

`Natives.isFullFeatured()` 检查 `isManager`、内核/管理器 UAPI 匹配与后端可用性。
JWSK 按独立管理器实现，配套 LKM 或用户编译的 GKI 内核须认可 JWSK 身份；不能通过删除安全检查声称独立。
产品面向 Android 14+，不绑定截图机型。KMI、编译配置和管理器公钥匹配须按设备验证。APK 不等于设备已经 Root。

## 模块

移植上游 `ModuleScreen` / `ModuleMaterial`、`ModuleViewModel`、`ModuleRepository` 与实现，以及仓库、安装执行、动作、WebUI 等依赖。
保留许可证与署名、功能与错误处理；只统一主题和入口，不重写成缩水页面。
当前只确认源码路径，完整依赖链移植和真机验收尚未进行。

## Android 14+ 与玻璃

Miuix v0.9.4 文档说明完整 blur/blend/highlight 的 RuntimeShader 路径要求 API 33。
用户后续明确将最低支持范围改为 Android 14（API 34），取代早期 Android 12–16 要求。
新管理器 minSdk 为 34；关闭玻璃时仍使用普通表面回退，并验证深色和系统字体缩放。
大面积多层实时模糊禁止作为默认方案，保留关闭与性能回退。

## 首页状态

用户指定显示“工作 / 不工作”，不把“KernelSU GKI”作为首页大标题。
状态由真实内核与管理器接口结果决定，检测中单独显示；失败时说明原因。
效果图不作为工作状态或性能验证证据。
