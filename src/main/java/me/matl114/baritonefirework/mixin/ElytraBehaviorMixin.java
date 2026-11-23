package me.matl114.baritonefirework.mixin;

import baritone.process.elytra.ElytraBehavior;
import baritone.api.behavior.look.IAimProcessor;
import baritone.api.behavior.look.ITickableAimProcessor;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.baritonefirework.modules.BaritoneFirework;
import me.matl114.baritonefirework.modules.BaritoneDirectFly;
import me.matl114.baritonefirework.state.DirectFlightPlan;
import me.matl114.baritonefirework.state.BaritoneSimulationState;
import me.matl114.hacks.modules.move.ElytraExtra;
import me.matl114.hacks.modules.move.PlayerStateManager;
import me.matl114.hacks.modules.survival.BaritoneFix;
import me.matl114.hacks.utils.move.ElytraOptimizeUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import baritone.api.utils.Pair;

@Environment(EnvType.CLIENT)
@Mixin(value = ElytraBehavior.class, remap = false)
public abstract class ElytraBehaviorMixin {

    private final ThreadLocal<BaritoneSimulationState> slimefunhelper$state = ThreadLocal.withInitial(BaritoneSimulationState::new);

    @Inject(
            method = "a(Lbaritone/process/elytra/ElytraBehavior$SolverContext;)Lbaritone/process/elytra/ElytraBehavior$Solution;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void slimefunhelper$clearDirectFlight(CallbackInfoReturnable<?> cir) {
        BaritoneDirectFly.clearPendingDirectFlight();
        if (BaritoneDirectFly.shouldRoutePathOnlyMotion()) {
            cir.setReturnValue(null);
        }
    }

    @WrapOperation(
            method = "a(Lbaritone/process/elytra/ElytraBehavior$SolverContext;)Lbaritone/process/elytra/ElytraBehavior$Solution;",
            at = @At(
                    value = "INVOKE",
                    target = "Lbaritone/process/elytra/ElytraBehavior;a(Lbaritone/process/elytra/ElytraBehavior$SolverContext;Lnet/minecraft/util/math/Vec3d;I)Lbaritone/api/utils/Pair;",
                    remap = true),
            require = 1,
            remap = true)
    private Pair<Float, Boolean> slimefunhelper$tryDirectFlight(
            ElytraBehavior behavior,
            @Coerce Object solverContext,
            Vec3d target,
            int relaxation,
            Operation<Pair<Float, Boolean>> original,
            @Local(ordinal = 0) Vec3d start) {
        boolean previousSuppression = BaritoneFirework.beginTargetSimulation(start, target);
        try {
            DirectFlightPlan plan = BaritoneDirectFly.findDirectFlight(start, target);
            if (plan != null) {
                BaritoneFirework.markNoAxisAccelerationIfNearTarget(start, target);
                return new Pair<>(plan.rotation().getPitch(), false);
            }
            Pair<Float, Boolean> result = original.call(behavior, solverContext, target, relaxation);
            if (result != null) {
                BaritoneFirework.markNoAxisAccelerationIfNearTarget(start, target);
            }
            return result;
        } finally {
            BaritoneFirework.restoreTargetSimulation(previousSuppression);
        }
    }

    @WrapOperation(
            method = "a(Lbaritone/process/elytra/ElytraBehavior$SolverContext;Lnet/minecraft/util/math/Vec3d;ILit/unimi/dsi/fastutil/floats/FloatArrayList;III)Lbaritone/process/elytra/ElytraBehavior$PitchResult;",
            at = @At(value = "INVOKE", target = "Lbaritone/api/behavior/look/IAimProcessor;fork()Lbaritone/api/behavior/look/ITickableAimProcessor;"),
            require = 1,
            remap = true)
    private ITickableAimProcessor slimefunhelper$startSimulation(
            IAimProcessor processor, Operation<ITickableAimProcessor> original) {
        BaritoneSimulationState state = slimefunhelper$state.get();
        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            state.currentRotation = new Rotation(0.0F, 0.0F);
        } else {
            state.currentRotation = new Rotation(player.getYaw(), player.getPitch());
        }
        state.previousRotation = state.currentRotation;
        state.previousVelocity = null;
        return original.call(processor);
    }

    @WrapOperation(
            method = "a(Lbaritone/process/elytra/ElytraBehavior$SolverContext;Lnet/minecraft/util/math/Vec3d;ILit/unimi/dsi/fastutil/floats/FloatArrayList;III)Lbaritone/process/elytra/ElytraBehavior$PitchResult;",
            at = @At(value = "INVOKE", target = "Lbaritone/api/behavior/look/ITickableAimProcessor;nextRotation(Lbaritone/api/utils/Rotation;)Lbaritone/api/utils/Rotation;"),
            require = 1,
            remap = true)
    private Rotation slimefunhelper$captureSimulationTick(
            ITickableAimProcessor processor, Rotation requested, Operation<Rotation> original,
            @Local(index = 18) Vec3d previousVelocity) {
        BaritoneSimulationState state = slimefunhelper$state.get();
        state.previousVelocity = previousVelocity;
        state.previousRotation = state.currentRotation;
        state.currentRotation = original.call(processor, requested);
        return (Rotation) state.currentRotation;
    }

    @WrapOperation(
            method =
                    "a(Lbaritone/process/elytra/ElytraBehavior$SolverContext;Lnet/minecraft/util/math/Vec3d;ILit/unimi/dsi/fastutil/floats/FloatArrayList;III)Lbaritone/process/elytra/ElytraBehavior$PitchResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/Vec3d;add(DDD)Lnet/minecraft/util/math/Vec3d;",
                    ordinal = 0,
                    remap = true),
            require = 1,
            remap = true)
    private Vec3d slimefunhelper$simulateFireworkBoost(
            Vec3d motion,
            double vanillaX,
            double vanillaY,
            double vanillaZ,
            Operation<Vec3d> original) {
        if (BaritoneFirework.isTargetSimulationSuppressed()) {
            return original.call(motion, vanillaX, vanillaY, vanillaZ);
        }

        ElytraExtra extra = ElytraExtra.INSTANCE;
        var player = MinecraftClient.getInstance().player;
        if (BaritoneFirework.INSTANCE == null
                || !BaritoneFirework.INSTANCE.isActive()
                || extra == null
                || !extra.rocketBoost.get()
                || !BaritoneFix.INSTANCE.baritoneUseRocketBoost.get()
                || extra.rocketBoostSpeed.get() <= 0.0D
                || player == null
                || !extra.canFireworkControlMotion()) {
            return original.call(motion, vanillaX, vanillaY, vanillaZ);
        }

        Rotation rotation = slimefunhelper$state.get().currentRotation;
        if (rotation == null) {
            rotation = new Rotation(player.getYaw(), player.getPitch());
        }
        double speed = extra.rocketBoostSpeed.get();
        Vec3d target = RotationUtils.calcLookDirectionFromRotation(rotation).normalize().multiply(1.7D);
        if (!extra.rocketBoostUseRescale.get()) {
            Vec3d speedTarget = target.normalize().multiply(speed);
            return motion.length() < speed ? speedTarget : motion;
        }

        if (!extra.autoRescale.get()) {
            return target.normalize().multiply(speed);
        }

        BaritoneSimulationState state = slimefunhelper$state.get();
        PlayerStateManager manager = PlayerStateManager.INSTANCE;
        Vec3d savedVelocity = manager.lastKnownClientVelocity;
        float savedPitch = manager.lastPitch;
        float savedYaw = manager.lastYaw;
        Rotation previousRotation = state.previousRotation == null ? rotation : state.previousRotation;
        manager.lastKnownClientVelocity = state.previousVelocity == null ? motion : state.previousVelocity;
        manager.lastPitch = previousRotation.getPitch();
        manager.lastYaw = previousRotation.getYaw();
        try {
            Vec3d rescaled = ElytraOptimizeUtils.simulateAxisLimitSpeed(
                    extra.autoRescaleAl.get(), target, rotation.getPitch(), rotation.getYaw());
            return rescaled;
        } finally {
            manager.lastKnownClientVelocity = savedVelocity;
            manager.lastPitch = savedPitch;
            manager.lastYaw = savedYaw;
        }
    }
}
