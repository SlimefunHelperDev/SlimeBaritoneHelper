package me.matl114.baritonefirework.mixin;

import baritone.process.ElytraProcess;
import baritone.process.elytra.ElytraBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ElytraProcess.class, remap = false)
public interface ElytraProcessAccessor {
    @Accessor("a")
    ElytraBehavior slimefunhelper$getBehavior();
}
