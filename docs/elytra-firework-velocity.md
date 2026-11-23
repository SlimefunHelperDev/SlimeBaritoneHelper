# 鞘翅烟花速度递推补丁说明

本文说明 [补丁文件](elytra-firework-velocity.patch) 的目的、改动点和接入约定。补丁用于设计审阅；它不是已经应用到源码的改动。

## 目标

当前 `ElytraBehavior.simulate` 逐 tick 模拟鞘翅飞行。每 tick 先调用 `step` 计算鞘翅运动，再推进位置并检查碰撞；处于预测烟花助推期间时，最后执行内置的烟花速度递推。补丁将这段助推速度递推替换为调用：

```java
tickSimulateFireworkVelocity(
    lastVelocity,
    lastPitch, lastYaw,
    currentTickPitch, currentTickYaw
)
```

函数根据上一模拟 tick 的速度和前后两 tick 的实际旋转，计算新速度。它的返回值用于后续模拟 tick；因此，候选 pitch 的轨迹、位移评分和碰撞结果都会反映新递推模型。

## 改动位置

补丁只针对 `src/main/java/baritone/process/elytra/ElytraBehavior.java`：

1. **`SolverContext` 保存 `startRotation`**：从当前玩家旋转取得模拟起点快照，作为第一 tick 的 `lastPitch` 和 `lastYaw`。
2. **上下文相等性包含起始旋转**：异步预测结果只有在起始旋转也相同的情况下才可复用。否则视角虽变、速度和位置却相同的旧结果可能被误认为仍然有效。
3. **`simulate` 追踪前一 tick 状态**：每轮调用新递推前传入 `lastVelocity`、`lastRotation` 和本轮 aim processor 实际产出的 `rotation`。每轮结束时更新前一 tick 状态，供下一轮使用。
4. **保留外层预测流程**：新递推仍在原有烟花 boost 计数条件中运行。`step`、位移累计和碰撞检测的位置不变，发射决策及 boost 时长估算也不属于本补丁的改动范围。

## 参数与时序约定

- `lastVelocity` 是上一模拟 tick 最终保留的 `motion`。第一轮取 `context.motion`。
- `lastPitch` / `lastYaw` 是上一模拟 tick 实际采用的 `Rotation`，第一轮取 `context.startRotation`。
- `currentTickPitch` / `currentTickYaw` 来自当前轮 `aimProcessor.nextRotation(...)` 的返回值。它体现 look 平滑处理后的模拟旋转，不是候选目标旋转本身。
- 模拟 tick 的顺序仍是：计算当前旋转、调用鞘翅 `step`、推进位移并检查碰撞、满足 boost 条件时调用新速度递推、保存状态供下一轮使用。
- 递推结果只更新速度，不应再次累计位移或移动 hitbox；外层循环会在下一轮用新速度继续模拟。

该顺序沿用“只替换烟花助推递推”的假设。若目标函数表示的是完整 tick 的速度更新、也要作用于未助推 tick，或要求新速度在本 tick 立即参与碰撞与位移，则必须重新定义它相对于 `step`、碰撞检查和位移推进的时序，并相应调整补丁。

## 函数实现与向量类型

补丁只展示调用点，没有定义 `tickSimulateFireworkVelocity` 的实现或归属。实现时可将它作为 `ElytraBehavior` 的方法，或放在适当的鞘翅模拟工具类中并从调用点引用。签名中的返回值应与 Baritone 当前源码使用的 Minecraft 向量类型兼容：这里是 `net.minecraft.world.phys.Vec3`。若公式来自使用 `Vec3d` 命名的其他版本或映射，需要在接口处转换类型；`Vec3d` 不是本文件当前使用的类型名。

接入前还需明确角度单位和计算约定。Baritone 的 `Rotation` 以度表示 pitch/yaw；函数应按自身公式所需的单位转换。若公式对 yaw 差值做运算，应处理跨越 `-180°/180°` 边界的环绕，避免把小角度转向解释为接近整圈的变化。

## 文件清单

- `docs/elytra-firework-velocity.patch`：建议的源码改动片段。
- `src/main/java/baritone/process/elytra/ElytraBehavior.java`：应用补丁并补齐新函数实现或调用。
- `docs/elytra-control.md`：项目当前鞘翅行为说明。若最终函数语义、参数时序或 boost 使用规则与现有描述不同，应同步维护此文档。

目前只有前两项文档产物已创建；源码尚未按补丁修改。
