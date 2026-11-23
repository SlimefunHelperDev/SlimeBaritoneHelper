package me.matl114.baritonefirework.state;

import baritone.api.utils.Rotation;
import net.minecraft.util.math.Vec3d;

public record DirectFlightPlan(Vec3d start, Vec3d target, Rotation rotation) {}
