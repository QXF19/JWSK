# Root 首页重做：效果图阶段

Mode: Operate. 用户明确指定 Google 应用 Material 3/Material You，并选择先确认效果图。
Seed: 7fa0b82a；用户指定的原生 Google 方向优先于随机参考。

## Direction contract

THESIS: 独立管理器直接管理配套内核，打开即知道内核是否安装及下一步如何处理。不要用营销卡片掩盖后端缺失。
OWN-WORLD: Pixel 设置/Files 的 Material 3，Roboto 与系统中文字体、浅蓝动态色示意、原生表面与触摸目标；真实实现随壁纸和深色模式变化。
STORY: 先读真实状态，再检查设备；应用授权、模块与工具明确区分，共用设置入口。
FIRST VIEWPORT: JWSK 标题及唯一设置按钮，真实“工作 / 不工作”状态及原因，唯一“安装与修补”，内核版本打开设备详情，底部四个原生导航目标。
FORM: 用户锁定 Google 原生惯例，seed 7fa0b82a；对比状态优先、设备信息优先、任务优先三种结构，而不是三个不同皮肤。
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## 当前边界

最终 `home-unique.png` 与 `tools-unique.png` 已由用户确认可实现。旧三张“未连接”图和中间版本被取代，不代表获准实现。它们都是效果图，不是运行验证。原生界面采用 Miuix；工具页仅有 AI 助手、手动 Comput、日志；唯一设置入口是右上角，镜像修补只在首页“安装与修补”；内核版本点开唯一设备详情。图中的设备示例不是目标手机，产品面向 Android 14+，不绑定该机型。GKI 仅接收用户编译的产物。字体按用户锁定的 Google 原生方向使用 Roboto 与系统中文字体；图像度量工具的西文目录回退不替代原生字体。
