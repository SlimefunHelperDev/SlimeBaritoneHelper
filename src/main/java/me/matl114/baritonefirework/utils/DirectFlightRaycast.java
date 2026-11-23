package me.matl114.baritonefirework.utils;

import java.util.Iterator;
import me.matl114.hacks.utils.RaycastUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

public final class DirectFlightRaycast {
    private DirectFlightRaycast() {}

    public static boolean isClear(Entity entity, Vec3d start, Vec3d end) {
        var world = MinecraftClient.getInstance().world;
        if (world == null) {
            return false;
        }
        Box playerBox = entity.getBoundingBox();
        Box playerBoundsRelativeToStart = playerBox.offset(-start.x, -start.y, -start.z);
        Vec3d delta = end.subtract(start);
        Box sweptStartBox = new Box(
                start.x + playerBoundsRelativeToStart.minX,
                start.y + playerBoundsRelativeToStart.minY,
                start.z + playerBoundsRelativeToStart.minZ,
                start.x + playerBoundsRelativeToStart.maxX,
                start.y + playerBoundsRelativeToStart.maxY,
                start.z + playerBoundsRelativeToStart.maxZ);
        Iterator<BlockPos> candidates = RaycastUtils.createRaycastBlockPosIterator(sweptStartBox, delta, true);

        while (candidates.hasNext()) {
            var pos = candidates.next();
            BlockState state = world.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(world, pos, ShapeContext.of(entity));
            if (!shape.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
