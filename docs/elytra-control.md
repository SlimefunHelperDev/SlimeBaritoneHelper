# Baritone 鞘翅控制逻辑

本文按当前源码说明鞘翅导航的运行流程，重点介绍烟花速度判断、烟花助推预测，以及预测结果如何决定视角。主要实现位于 `src/main/java/baritone/process/ElytraProcess.java` 和 `src/main/java/baritone/process/elytra/ElytraBehavior.java`。

## 运行入口

`ElytraProcess` 管理导航状态和生命周期。收到目标后，它创建 `ElytraBehavior`；玩家处于滑翔状态时，每个 pathing tick 调用 `behavior.tick()`，并取消普通地面寻路控制。落地状态则交由 `LANDING` 分支处理。

飞行 tick 的控制顺序如下：

1. 从当前位置、当前速度、碰撞箱、路径进度和烟花状态构造 `SolverContext`。
2. 使用与当前上下文匹配的异步预计算结果；结果不存在或已过期时，就在当前 tick 求解。
3. 求出朝向后调用 `LookBehavior.updateTarget`，交由 look 行为平滑应用旋转。
4. 若 pitch 求解成功，根据目标点及速度条件决定是否使用烟花。
5. 在 tick 后异步计算下一次朝向，供后续 tick 使用。

## 烟花与速度

### 发射阈值

发射判断在 `ElytraBehavior.tickUseFireworks`。玩家当前运动向量是：

```text
v = (vx, vy, vz)
speedSquared = vx*vx + vy*vy + vz*vz
```

如果玩家低于选定目标高度，且正向下运动，计算时把 `vy` 钳制到不小于 0；这会忽略向下速度，避免把下坠速度当成有效前进速度。其他情况下使用实际 `vy`。因此这里判断的是三维速度大小，而不是水平速度。

正常发射条件要求当前没有烟花助推，允许在当前高度使用烟花，距离/高度条件表明仍需前进，并且：

```text
speedSquared < elytraFireworkSpeed^2
```

等价地，参与比较的速度小于 `elytraFireworkSpeed`。该设置可理解为方块/游戏 tick 的速度阈值。源码中的命令提示以 `0.6` 作为调低速度的示例；它不是这里宣称的默认值。

`elytraConserveFireworks` 开启时，只有玩家低于目标高度加 5 格才允许按普通条件发射；关闭时则不施加这个高度限制。求解器判断“当前路线无解，但若在预测中使用烟花可脱困”时会设置强制使用标记。强制标记仍受发射冷却和着陆模式限制。

发射后，代码设置 10 tick 的烟花使用间隔，并优先从背包选择具有助推效果的烟花。源码将带烟花数据且爆炸效果列表为空的烟花视为可助推烟花；道具中没有这种烟花时，会退回选择普通烟花。

### 助推时间和模拟速度

烟花组件的 `flightDuration` 用来估计助推时长下限：

```text
minimumBoostTicks = 10 * (1 + flightDuration)
maximumBoostTicks = minimumBoostTicks + 11
```

代码根据附着在玩家身上的烟花实体 `tickCount` 估算剩余时间：

```text
保证剩余 tick = max(0, minimumBoostTicks - fireworkTickCount)
最大剩余 tick = max(0, maximumBoostTicks - fireworkTickCount)
```

它不是单独预测一个“烟花最终速度”；求解器逐 tick 推演玩家速度。每个模拟 tick 先按鞘翅飞行模型更新当前速度，再在助推有效时追加烟花加速度。代码中的烟花速度更新为：

```text
motion' = motion + lookDirection * 0.1
                 + (lookDirection * 1.5 - motion) * 0.5
```

`lookDirection` 是该模拟 tick 经瞄准处理后的单位视线方向。也就是说，烟花助推不仅取决于烟花等级，也受模拟期间的视角和玩家已有速度影响。鞘翅速度更新包含重力、俯冲/抬升相关项、水平速度对齐和阻力；每 tick 更新公式见 `ElytraBehavior.step`。

## 视角如何求出

求解器先在路径前方搜索候选目标点。飞行中有烟花助推时，会先尝试高于路径点 20、10、5、0 格的位置，以利用助推争取高度；没有助推时只尝试路径高度。候选点必须满足视线和碰撞箱避障检查。若严格搜索失败，会逐级放宽碰撞安全边距并增加路径点插值候选。

对每个可行候选点：

1. **Yaw**：用当前位置到候选点的方向直接计算水平朝向。
2. **Pitch**：从当前位置到目标点的直线 pitch 附近枚举角度；紧急放宽阶段还会搜索更大范围。
3. **模拟**：从玩家当前 `motion` 开始，逐 tick 使用候选 pitch、瞄准处理后的视线、鞘翅运动公式和预计烟花 tick 数模拟轨迹，同时检测扩展碰撞箱是否撞上不可通过方块。
4. **评分**：普通飞行选择模拟末端位移方向与目标方向点积最大的 pitch；着陆模式则选择模拟终点与目标位置距离最小的 pitch。
5. **视线校验**：验证模拟轨迹上的位置仍能看到目标点。严格阶段要求沿途可见，最后的放宽阶段只检查模拟终点。

助推持续时间会影响预测窗口。正在助推时，求解器会额外测试保证助推结束附近的情形；随后再测试“整个窗口持续助推”或“不助推”的标准情形。普通飞行的预测 tick 数由 `elytraSimulationTicks` 控制；紧急放宽阶段使用较短模拟窗口，并额外测试“延迟 1 至 3 tick 后使用 10 tick 烟花”的方案。如果该方案才有可行 pitch，求解结果会要求当前实际发射烟花。

找到旋转后，`LookBehavior` 会按当前视角处理每 tick 可应用的旋转，并将其交给游戏控制。求解模拟也 fork 当前 aim processor 来模拟这些逐 tick 旋转变化，因此模拟中使用的视线并非假设玩家瞬间转到目标角度。

## 仅替换助推速度递推时的修改指南

如果新版只改变“烟花正在助推时，如何从上一速度和视角计算下一速度”，应把改动限制在 `ElytraBehavior.simulate` 的逐 tick 模拟中。该模拟同时用于 pitch 搜索和碰撞检查；新速度会自然改变后续候选 pitch 的评分与可行性。

### 现有状态对应关系

| 新递推需要的量 | 当前实现中的来源 | 修改建议 |
| --- | --- | --- |
| `lastVelocity` | 没有独立变量；循环开始处的 `motion` 是上一模拟 tick 结束后的速度状态，模拟起点为 `context.motion` | 如果新版定义的 `lastVelocity` 是上一模拟 tick 的速度，应在每轮 `step()` 之前保存它。注意：现有烟花公式使用的是本 tick `step()` 之后的 `motion`，两者不应混为一谈。 |
| `currentYaw` / `currentPitch` | 本 tick 的局部变量 `rotation`，由 `aimProcessor.nextRotation(...)` 返回 | 使用 `rotation.getYaw()` 和 `rotation.getPitch()`。这代表瞄准处理之后模拟实际采用的旋转，而不是候选目标角度。 |
| `lastYaw` / `lastPitch` | 没有独立变量；前一模拟 tick 的旋转状态保存在 aim processor fork 的内部 | 在模拟循环外维护 `lastRotation`，以起始旋转初始化；每轮递推完成后令其等于本轮 `rotation`。下一轮就能读取上一模拟 tick 的角度。 |
| 起始旋转 | 当前没有保存在 `SolverContext` 的独立字段。旋转参考通过 `ctx.playerRotations()` 取得；aim processor fork 也从其源处理器的上一旋转开始 | 在构造 `SolverContext` 时保存 `ctx.playerRotations()` 快照，并在异步求解时一起传给模拟。将它纳入上下文相等性判断，避免玩家视角已变但仍复用旧求解结果。 |

`lastYaw` 与 `lastPitch` 应表示上一个**模拟 tick 实际采用**的角度，不是上一个 pathing tick 的目标角度。第一轮的 `lastRotation` 应从 `SolverContext` 的起始视角快照初始化；之后以每轮 `rotation` 更新。角度单位沿用 `Rotation` 的度数。如果公式使用 yaw 差值，需对跨越 `-180/180` 度边界的差值做环绕处理，避免把小转向算成接近 360 度。

### 接入位置和更新顺序

当前每轮的顺序是：计算本 tick 实际旋转和视线；调用鞘翅 `step()`；用所得速度推进位置并执行碰撞检查；最后应用烟花速度递推。若新版只替换烟花递推，保留这个顺序。示意代码如下，`newFireworkRecurrence` 代表待接入的算法，不是现有方法：

```java
Rotation previousRotation = context.startRotation;
Vec3 motion = context.motion;

for (int i = 0; i < ticks; i++) {
    Vec3 lastVelocity = motion; // 上一模拟 tick 的最终速度；第一轮是 context.motion
    Rotation lastRotation = previousRotation;
    Rotation rotation = aimProcessor.nextRotation(targetRotation);
    Vec3 lookDirection = RotationUtils.calcLookDirectionFromRotation(rotation);

    motion = step(motion, lookDirection, rotation.getPitch());
    Vec3 velocityBeforeBoost = motion; // 本 tick 鞘翅 step 后、烟花递推前

    // 使用当前 motion 推进 hitbox、检查碰撞并累计 displacement。

    if (i >= ticksBoostDelay && remainingTicksBoosted-- > 0) {
        motion = newFireworkRecurrence(
                lastVelocity,
                velocityBeforeBoost,
                lastRotation.getPitch(), lastRotation.getYaw(),
                rotation.getPitch(), rotation.getYaw()
        );
    }

    previousRotation = rotation;
}
```

如果新版递推只需要“应用烟花效果前的当前速度”，传入 `velocityBeforeBoost` 即可；如果它需要上一 tick 的速度历史，则传入 `lastVelocity`。若公式同时依赖两者，应像示意签名一样显式传入两个值。不要仅因变量名叫 `lastVelocity` 就把 `step()` 后的速度当成上一 tick 状态。

具体实现时应保留现有的 `delta`、hitbox 和 displacement 更新逻辑，而不是直接照搬省略了碰撞细节的示意代码。当前递推发生在本 tick 的位置推进之后；递推输出的速度从下一次 `step()` 开始影响移动。若新版引擎把速度更新定义为本 tick 立即生效，则必须明确调整速度递推与位置推进、碰撞检测的先后次序，因为那会改变预测轨迹语义。

### 通常无需改动的部分

- `solvePitch` 继续提供模拟 tick 数、助推 tick 数和启动延迟；`simulate` 继续返回位移轨迹。只要新版不改变助推持续时间及启用时点，就无需改这些参数。
- 候选 pitch 评分、可视性检查、碰撞箱检查和 yaw 指向目标点的计算都消费模拟轨迹或候选目标点，通常不需要专门适配递推公式。
- `tickUseFireworks` 的 `elytraFireworkSpeed` 是基于玩家实测速度的发射阈值，与模拟中的助推递推分开。若只改预测公式，不要顺带改变这个阈值逻辑。

### 实现时需要核对

- 只在 `ticksBoosted` 生效且超过 `ticksBoostDelay` 后运行新版递推；没有助推的 tick 仍只运行鞘翅 `step()`。
- 使用每个模拟 tick 的 `rotation`，不要用最终候选 pitch 或初始玩家视角代替当前视角；新版算法若需要角度变化量，应同时使用上一模拟旋转和本轮旋转。
- 保留当前 boost 计数的递减语义，避免助推比设定的 tick 数多或少运行一次。
- 异步和同步求解都使用同一份起始视角快照约定；异步构造上下文时 aim processor 会预进一 tick，这一既有行为要和快照所代表的模拟起点对齐。
- 递推输出必须仍是每 tick 的玩家速度向量，并与 `motion` 的坐标轴和单位一致；不要在递推函数内再次累计位置，否则会和外层 displacement/hitbox 推进重复。
- 更新后确认预测轨迹、模拟碰撞检测和游戏实际运动在烟花启动、持续助推及助推结束三个阶段的 tick 对齐。若新版也改变寿命或启用时点，应另行修改 `FireworkBoost` 和 `solvePitch` 的情景枚举，不再属于“只替换递推”。

## 读代码时的边界

- 发射阈值 `elytraFireworkSpeed` 和轨迹模拟是两套不同计算：前者决定正常情况下何时发射，后者评估候选 pitch 在助推期间如何改变位移。
- `FireworkBoost` 的 `minimumBoostTicks` / `maximumBoostTicks` 是源码用于处理时长不确定性的估算；具体游戏速度仍按每 tick 运动公式模拟。
- 求解器预测的是候选目标点附近的短期轨迹，不是一次性生成完整航程的连续控制曲线；每 tick 会根据新位置、速度、路径和烟花状态重新校验或计算。
- `ElytraProcess` 的自动起飞、路径完成后的安全着陆和紧急着陆属于状态管理；本文聚焦滑翔中的速度、烟花和朝向控制。
