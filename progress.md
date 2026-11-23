# 实施进度

- [x] 定位示例插件、Slime 1.21.11 和 Baritone 源码及设计文档。
- [x] 按示例插件建立独立 Fabric/Slime 插件项目，并复制 Baritone 依赖。
- [x] 将 BaritoneFirework 模块注册到 SurvivalTasks 的模块组。
- [x] 按 Baritone 文档和 Slime 鞘翅模型实现逐 tick 角度预测及烟花助推。
- [x] 注册优先级 114514 的 MovementPipeline，并通过 `gradle compileJava --offline`。

## docs 补丁核对（2026-11-09）

- [x] 已阅读 `docs/elytra-control.md`、`docs/elytra-firework-velocity.md` 和 `docs/elytra-firework-velocity.patch`，并对照父目录 `baritone/src/main/java/baritone/process/elytra/ElytraBehavior.java`。补丁是供审阅、手动应用的建议片段，目标是仅替换 `simulate` 中助推期的速度递推；它还要求保存 `SolverContext.startRotation`，在模拟循环中传递上一 tick 的速度和旋转。`tickSimulateFireworkVelocity` 只有调用示意，公式实现仍是 TODO。
- [x] 已检查 `libs/baritone-meteor.jar`：其中存在 `baritone.process.elytra.ElytraBehavior` 及其 `SolverContext`、`PitchResult` 类；`javap` 显示对应的私有混淆方法 `a(SolverContext, Vec3d, int, FloatArrayList, int, int, int)`，与当前 Mixin 指定的方法签名相符。
- [x] 当前项目**以 Mixin 形式实现了烟花速度递推调用点的替换**：`fabric.mod.json` 注册 `slimefunbaritonefirework.mixins.json`，后者加载 `ElytraBehaviorMixin`；该类用 `@WrapOperation` 包裹目标方法中的 `Vec3d.add(DDD)`，并在模块及 Slime 鞘翅烟花控制条件满足时返回自定义预测速度，否则调用原操作。
- [x] 已按临时公式补齐 Mixin 的助推递推：逐模拟 tick 捕获上一最终速度与上一实际旋转；以当前模拟旋转的方向向量归一化乘 `1.7` 作为 `simulateAxisLimitSpeed` 的 `current`；调用前临时写入 `PlayerStateManager.lastKnownClientVelocity`、`lastPitch`、`lastYaw`，并在 `finally` 恢复。当前使用的依赖字段名是 `lastKnownClientVelocity`。
- [x] `gradlew.bat compileJava --offline` 通过。Mixin 的 `@Shadow` 使用混淆 JAR 中 `IPlayerContext a` 字段作为别名。
- [ ] docs 中关于 `SolverContext.startRotation` 快照和上下文相等性检查尚未接入；当前第一轮模拟旋转在候选轨迹开始时由 MinecraftClient 玩家 yaw/pitch 初始化。异步计算与上下文复用时仍需运行验证。
- [x] 从 runClient 导出的目标类和 debug 日志确认 ElytraBehaviorMixin 已应用；执行 #elytra 时发现 Mixin 包内嵌套状态类触发 IllegalClassLoadError，现已将状态类移至 me.matl114.baritonefirework.state。

## runClient 启动修复（2026-11-09）

- [x] 首次启动发现 @Shadow aliases="a" 不能别名目标类的 public 字段，导致 ElytraBehavior Mixin 应用失败；已移除该 shadow，初始旋转改从当前客户端玩家读取。
- [x] 第二次启动发现速度递推调用点无法隐式捕获 Rotation 局部变量（该变量在混淆字节码中已被复用）；已改为使用同一模拟循环 nextRotation 包装器记录的当前模拟旋转。
- [x] 再次运行 gradlew.bat runClient --offline 后，Mixin 与 SlimefunHelper 插件初始化通过，Minecraft 客户端已启动并保持运行。启动日志未发现本项目 Mixin 错误。
- [ ] Realms 请求因开发环境的模拟登录凭据返回 401；未阻止客户端启动。尚未在实际鞘翅烟花助推中触发并验证速度递推轨迹。
- [x] 外置 BaritoneSimulationState 后，runClient 再次启动并应用 Mixin；当前客户端正在运行，等待重试 #elytra 的实机调用确认。
## SlimefunHelper 最新 API 依赖（2026-11-09）

- [x] 将相邻 slimefunhelper-1.21.11/build/libs 中最新正式 JAR SlimefunHelper-1.21.11-beta-1.2.1.4.jar 复制到本项目 libs/。
- [x] Gradle 改用 modImplementation files('libs/SlimefunHelper-1.21.11-beta-1.2.1.4.jar')，移除 commit 号版本属性及不再使用的 JitPack 仓库。
- [x] 更新 README 中旧的 1.2.1.2 依赖说明。

## ControlFly 直达飞行动量模块（2026-11-09）

- [x] 新增独立 ControlFly 模块并注册到 Baritone 模块组。
- [x] Baritone 确定候选飞行目标后、pitch 求解前，在存在可控烟花时用 RaycastUtils 遍历路径方块，并对平移后的玩家碰撞箱做扫掠碰撞检测。
- [x] 直达路径可行时跳过 Baritone 视角更新，将目标方向和 ElytraExtra 当前配置的速度/限速算法封装为 ElytraMotion，通过 ElytraExtra.submitFireworkMotion 提交本 tick 动量。
- [x] gradlew.bat compileJava --offline 通过；unClient --offline 启动到主菜单，未观察到本项目 Mixin 启动错误。直达分支尚未在实际飞行中触发验证。
