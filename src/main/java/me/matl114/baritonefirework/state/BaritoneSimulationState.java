package me.matl114.baritonefirework.state;

import baritone.api.utils.Rotation;
import net.minecraft.util.math.Vec3d;

public final class BaritoneSimulationState {
    public Rotation previousRotation;
    public Rotation currentRotation;
    public Vec3d previousVelocity;
}