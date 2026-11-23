package me.matl114.baritonefirework.modules;

import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.process.elytra.ElytraBehavior;
import me.matl114.baritonefirework.BaritoneFireworkPlugin;
import me.matl114.baritonefirework.state.DirectFlightPlan;
import me.matl114.baritonefirework.utils.DirectFlightRaycast;
import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.events.impl.EventContainer;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModulePath;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.MovExtra;
import me.matl114.hacks.utils.move.ElytraMotion;
import me.matl114.hacks.utils.move.FlightVelocity;
import me.matl114.managers.config.DoubleRef;
import me.matl114.managers.config.FlagRef;
import me.matl114.managers.config.KeyBindRef;
import me.matl114.managers.input.MultiKeyBind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public final class BaritoneDirectFly extends BaseModule {
    public static BaritoneDirectFly INSTANCE;

    private final ModulePath rootPath = makePath(BaritoneFireworkPlugin.CONFIG, "control-fly");
    private final ModulePath fireworkMotionPath = rootPath.add("firework-motion");
    private final ModulePath flightMotionPath = rootPath.add("flight-motion");

    {
        portConfigs(rootPath, fireworkMotionPath);
    }

    public final FlagRef enable = flagBuilder(fireworkMotionPath.addEnable()).build();
    public final KeyBindRef hotkey = toggleHotkey(
                    fireworkMotionPath.addHotkey(), new MultiKeyBind(), fireworkMotionPath.addEnable())
            .build();

    public final FlagRef flightMotionEnable = flagBuilder(flightMotionPath.addEnable()).build();
    public final DoubleRef flightMotionSpeed = doubleBuilder(flightMotionPath.add("speed"))
            .defaultValue(4.0D)
            .validator(value -> Double.isFinite(value) && value >= 0.0D && value <= 50.0D)
            .build();

    private static volatile DirectFlightPlan pendingDirectFlight;
    private static volatile Vec3d selectedWaypoint;

    public BaritoneDirectFly() {
        super("DirectFly");
        INSTANCE = this;
        bindFlag(enable);
    }

    @Override
    public boolean isActive() {
        return enable.get() || flightMotionEnable.get();
    }

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(Listener.getCustomListener().getChannel(FlightVelocity.class), this::onFlightVelocity);
    }

    private void onFlightVelocity(Event<EventContainer<FlightVelocity>> event) {
        if (!isFlightMotionActive()) {
            return;
        }

        FlightVelocity motion = event.context().getValue();
        if (motion.mode() != FlightVelocity.Mode.MOTION_FLIGHT) {
            return;
        }

        Vec3d target = selectedWaypoint;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (target == null || mc.player == null) {
            return;
        }

        Vec3d delta = target.subtract(mc.player.getPos());
        if (delta.lengthSquared() < 1.0E-6D) {
            return;
        }

        double speed = Math.min(flightMotionSpeed.get(), motion.maxVelocity());
        motion.velocity(delta.normalize().multiply(speed));
    }

    public static DirectFlightPlan findDirectFlight(Vec3d start, Vec3d target) {
        BaritoneDirectFly module = INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        ElytraExtra extra = ElytraExtra.INSTANCE;
        target = selectedWaypoint;
        if (module == null
                || !module.enable.get()
                || target == null
                || mc.player == null
                || !mc.player.isFallFlying()
                || mc.world == null
                || extra == null
                || !extra.rocketBoost.get()
                || extra.rocketBoostSpeed.get() <= 0.0D
                || !extra.canFireworkControlMotion()) {
            return null;
        }

        Vec3d delta = target.subtract(start);
        if (delta.lengthSquared() < 1.0E-6D || !DirectFlightRaycast.isClear(mc.player, start, target)) {
            return null;
        }

        Rotation rotation = RotationUtils.calcRotationFromVec3d(
                start, target, new Rotation(mc.player.getYaw(), mc.player.getPitch()));
        DirectFlightPlan plan = new DirectFlightPlan(start, target, rotation);
        pendingDirectFlight = plan;
        return plan;
    }

    public static boolean submitPendingDirectFlight() {
        DirectFlightPlan plan = pendingDirectFlight;
        pendingDirectFlight = null;
        if (plan == null) {
            return false;
        }

        BaritoneDirectFly module = INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        ElytraExtra extra = ElytraExtra.INSTANCE;
        if (module == null
                || !module.enable.get()
                || mc.player == null
                || !mc.player.isFallFlying()
                || extra == null
                || new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                        .squaredDistanceTo(plan.start()) > 1.0D
                || !extra.rocketBoost.get()
                || extra.rocketBoostSpeed.get() <= 0.0D
                || !extra.canFireworkControlMotion()) {
            return false;
        }

        ElytraExtra.Al algorithm = extra.rocketBoostUseRescale.get() && extra.autoRescale.get()
                ? extra.autoRescaleAl.get()
                : null;
        double speed = extra.rocketBoostSpeed.get();
        extra.submitFireworkMotion(new ElytraMotion(
                algorithm, plan.rotation().getPitch(), plan.rotation().getYaw(), speed, speed));
        return true;
    }

    public static boolean shouldRoutePathOnlyMotion() {
        return isFlightMotionActive();
    }

    public static boolean isFlightMotionActive() {
        BaritoneDirectFly module = INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        MovExtra extra = MovExtra.INSTANCE;
        return module != null
                && module.flightMotionEnable.get()
                && mc.player != null
                && !mc.player.isFallFlying()
                && extra != null
                && extra.isFlyingControlled();
    }

    public static void updatePathTarget(ElytraBehavior behavior) {
        BaritoneDirectFly module = INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (module == null || behavior == null || mc.player == null || mc.world == null) {
            selectedWaypoint = null;
            return;
        }

        var pathManager = behavior.pathManager;
        var path = pathManager.getPath();
        if (path.isEmpty()) {
            selectedWaypoint = null;
            return;
        }

        int firstWaypoint = Math.min(Math.max(pathManager.getNear() + 1, 0), path.size() - 1);
        int lastWaypoint = Math.min(path.size() - 1, pathManager.getNear() + 20);
        Vec3d start = mc.player.getPos();
        for (int index = lastWaypoint; index >= firstWaypoint; index--) {
            Vec3d candidate = Vec3d.ofCenter(path.get(index));
            if (DirectFlightRaycast.isClear(mc.player, start, candidate)) {
                selectedWaypoint = candidate;
                return;
            }
        }

        selectedWaypoint = null;
    }

    public static void clearPendingDirectFlight() {
        pendingDirectFlight = null;
    }

    public static void clearPathTarget() {
        selectedWaypoint = null;
    }
}
