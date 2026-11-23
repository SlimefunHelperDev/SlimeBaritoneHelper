# Slimefun Baritone Firework Boost Controller

Fabric 1.21.11 插件。`BaritoneFirework`、`GlidePoseFix` 和 `BaritoneDirectFly` 注册在插件自己的 `Baritone` 模块组中。

`BaritoneFirework` 监听 SlimefunHelper 的 `ElytraExtra.getFireworkMotion()` 事件，在接近路径目标的 tick 取消烟花助推。`BaritoneDirectFly` 选择视线无遮挡的 Baritone 路径点，并通过 `ElytraExtra.submitFireworkMotion()` 提交单 tick 的 `ElytraMotion`。当前 SlimefunHelper 将该请求交给 `RotationManager` 和轴向速度限制流程处理。速度重缩放使用 `ElytraOptimizeUtils.simulateAxisLimitSpeed()`。

SlimefunHelper 依赖通过 JitPack 固定到 `1.21.11` 分支提交 `d942e9aab12b7c9163d6e06d9567d6c065338ae5`，提交号保存在 `gradle.properties`。运行时要求 SlimefunHelper `1.2.2.1` 或更新版本。`libs/baritone-meteor.jar` 是编译用的 Baritone 依赖；映射文件位于 `mapping/`。项目使用 Java 21。

Baritone 预测使用当前 tick 的实际旋转，未来 tick 的 yaw 按每 tick 最多转向 30 度近似。实际飞行轨迹仍需在游戏内校准。
