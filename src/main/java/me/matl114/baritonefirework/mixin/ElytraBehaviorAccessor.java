package me.matl114.baritonefirework.mixin;

import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.process.elytra.ElytraBehavior;
import baritone.process.elytra.NetherPathfinderContext;
import baritone.utils.BlockStateInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ElytraBehavior.class, remap = false)
public interface ElytraBehaviorAccessor {
    @Accessor("a")
    IPlayerContext slimefunhelper$getPlayerContext();

    @Accessor("a")
    BlockStateInterface slimefunhelper$getBlockStateInterface();

    @Accessor("a")
    void slimefunhelper$setBlockStateInterface(BlockStateInterface blockStateInterface);

    @Accessor("a")
    ElytraBehavior.PathManager slimefunhelper$getPathManager();

    @Accessor("a")
    NetherPathfinderContext slimefunhelper$getPathfinderContext();

    @Accessor("b")
    BetterBlockPos slimefunhelper$getDestination();
}
