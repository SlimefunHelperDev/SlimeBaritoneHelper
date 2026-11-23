package me.matl114.baritonefirework.modules;

import java.util.concurrent.atomic.AtomicInteger;
import me.matl114.baritonefirework.BaritoneFireworkPlugin;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.utils.move.ElytraMotion;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public final class BaritoneFirework extends BaseModule {
    private static final int NO_MARKED_TICK = Integer.MIN_VALUE;
    private static final AtomicInteger NO_AXIS_ACCELERATION_TICK = new AtomicInteger(NO_MARKED_TICK);
    private static final ThreadLocal<Boolean> SUPPRESS_TARGET_SIMULATION = ThreadLocal.withInitial(() -> false);

    public static BaritoneFirework INSTANCE;

    private final ModulePath path = makePath(BaritoneFireworkPlugin.CONFIG, "baritone-firework");

    public final FlagRef enable = flagBuilder(path.addEnable()).build();
    public final FlagRef nearTargetFixEnable = flagBuilder(path.add("near-target-fix-enable")).defaultValue(false).build();
    public final KeyBindRef hotkey = toggleHotkey(path.addHotkey(), new MultiKeyBind(), path.addEnable())
            .build();
    public final DoubleRef nearTargetFixDistance = doubleBuilder(path.add("near-target-fix-distance"))
            .defaultValue(1.0D)
            .validator(value -> Double.isFinite(value) && value >= 0.0D)
            .build();

    public BaritoneFirework() {
        super("BaritoneFirework");
        INSTANCE = this;
        bindFlag(enable);
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(ElytraExtra.getFireworkMotion(), this::onFireworkMotion);
    }

    private void onFireworkMotion(me.matl114.events.Event<ElytraMotion> event) {
        if (!isActive() || !nearTargetFixEnable.get()) {
            return;
        }

        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            NO_AXIS_ACCELERATION_TICK.set(NO_MARKED_TICK);
            return;
        }

        int markedTick = NO_AXIS_ACCELERATION_TICK.get();
        if (markedTick == Tasks.getTick()) {
            event.cancel();
        } else if (markedTick != NO_MARKED_TICK) {
            NO_AXIS_ACCELERATION_TICK.compareAndSet(markedTick, NO_MARKED_TICK);
        }
    }

    public static boolean beginTargetSimulation(Vec3d start, Vec3d target) {
        boolean previous = SUPPRESS_TARGET_SIMULATION.get();
        if (previous || isWithinConfiguredDistance(start, target)) {
            SUPPRESS_TARGET_SIMULATION.set(true);
        }
        return previous;
    }

    public static void restoreTargetSimulation(boolean previous) {
        if (previous) {
            SUPPRESS_TARGET_SIMULATION.set(true);
        } else {
            SUPPRESS_TARGET_SIMULATION.remove();
        }
    }

    public static boolean isTargetSimulationSuppressed() {
        return SUPPRESS_TARGET_SIMULATION.get();
    }

    public static void markNoAxisAccelerationIfNearTarget(Vec3d start, Vec3d target) {
        if (!isWithinConfiguredDistance(start, target)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.isOnThread()) {
            NO_AXIS_ACCELERATION_TICK.set(Tasks.getTick());
        }
    }

    private static boolean isWithinConfiguredDistance(Vec3d start, Vec3d target) {
        BaritoneFirework module = INSTANCE;
        if (module == null
                || !module.isActive()
                || !module.nearTargetFixEnable.get()
                || start == null
                || target == null) {
            return false;
        }

        double distance = module.nearTargetFixDistance.get();
        return Double.isFinite(distance)
                && distance >= 0.0D
                && start.squaredDistanceTo(target) <= distance * distance;
    }

    @Override
    public void onDisableModule() {
        NO_AXIS_ACCELERATION_TICK.set(NO_MARKED_TICK);
        super.onDisableModule();
    }
}
