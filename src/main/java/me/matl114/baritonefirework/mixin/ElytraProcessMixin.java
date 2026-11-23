package me.matl114.baritonefirework.mixin;

import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Rotation;
import baritone.behavior.LookBehavior;
import baritone.process.ElytraProcess;
import baritone.process.elytra.ElytraBehavior;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.matl114.baritonefirework.modules.BaritoneDirectFly;
import me.matl114.hacks.modules.survival.BaritoneFix;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ElytraProcess.class, remap = false)
public abstract class ElytraProcessMixin {
    @Inject(method = "onLostControl()V", at = @At("TAIL"), remap = false)
    private void slimefunhelper$clearSelectedWaypoint(CallbackInfo ci) {
        BaritoneDirectFly.clearPathTarget();
        BaritoneDirectFly.clearPendingDirectFlight();
    }

    @Inject(
            method = "onTick(ZZ)Lbaritone/api/process/PathingCommand;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void slimefunhelper$pathOnlyMotion(
            boolean calcFailed,
            boolean isSafeToCancel,
            CallbackInfoReturnable<PathingCommand> cir) {
        if (!BaritoneDirectFly.shouldRoutePathOnlyMotion()) {
            BaritoneDirectFly.updatePathTarget(((ElytraProcessAccessor) this).slimefunhelper$getBehavior());
            return;
        }

        BaritoneDirectFly.clearPendingDirectFlight();
        ElytraProcess process = (ElytraProcess) (Object) this;
        ElytraBehavior behavior = ((ElytraProcessAccessor) this).slimefunhelper$getBehavior();
        if (calcFailed) {
            process.onLostControl();
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.CANCEL_AND_SET_GOAL));
            return;
        }
        if (behavior == null) {
            BaritoneDirectFly.clearPathTarget();
            cir.setReturnValue(new PathingCommand(null, PathingCommandType.REQUEST_PAUSE));
            return;
        }

        ElytraBehaviorAccessor access = (ElytraBehaviorAccessor) behavior;
        synchronized (access.slimefunhelper$getPathfinderContext().cullingLock) {
            var pathManager = access.slimefunhelper$getPathManager();
            var path = pathManager.getPath();
            if (path.isEmpty()) {
                BaritoneDirectFly.clearPathTarget();
            } else if (access.slimefunhelper$getDestination() == null) {
                pathManager.clear();
                BaritoneDirectFly.clearPathTarget();
            } else {
                access.slimefunhelper$setBlockStateInterface(
                        new baritone.utils.BlockStateInterface(access.slimefunhelper$getPlayerContext()));
                pathManager.tick();
                BaritoneDirectFly.updatePathTarget(behavior);
            }
        }

        BaritoneFix baritoneFix = BaritoneFix.INSTANCE;
        PathingCommandType commandType = baritoneFix != null && baritoneFix.shouldPauseBaritoneElytra()
                ? PathingCommandType.REQUEST_PAUSE
                : PathingCommandType.CANCEL_AND_SET_GOAL;
        cir.setReturnValue(new PathingCommand(null, commandType));
    }

    @Inject(
            method = "onTick(ZZ)Lbaritone/api/process/PathingCommand;",
            at = @At(
                    value = "INVOKE",
                    target = "Lbaritone/process/elytra/ElytraBehavior$PathManager;c()V",
                    shift = At.Shift.AFTER,
                    remap = false),
            require = 0,
            remap = false)
    private void slimefunhelper$refreshWaypointAfterPathTick(
            boolean calcFailed,
            boolean isSafeToCancel,
            CallbackInfoReturnable<PathingCommand> cir) {
        BaritoneDirectFly.updatePathTarget(((ElytraProcessAccessor) this).slimefunhelper$getBehavior());
    }

    @WrapOperation(
            method = "onTick(ZZ)Lbaritone/api/process/PathingCommand;",
            at = @At(
                    value = "INVOKE",
                    target = "Lbaritone/behavior/LookBehavior;updateTarget(Lbaritone/api/utils/Rotation;Z)V",
                    ordinal = 1,
                    remap = false),
            require = 1,
            remap = false)
    private void slimefunhelper$submitDirectFlight(
            LookBehavior lookBehavior, Rotation target, boolean force, Operation<Void> original) {
        if (BaritoneDirectFly.shouldRoutePathOnlyMotion()) {
            BaritoneDirectFly.clearPendingDirectFlight();
            return;
        }
        if (BaritoneDirectFly.submitPendingDirectFlight()) {
            return;
        }
        original.call(lookBehavior, target, force);
    }
}
