package io.github.foundationgames.splinecart.util;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.block.TrackTiesBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class TrackPlacement {
    private TrackPlacement() {
    }

    public static BlockState stateFor(BlockPos pos, @Nullable Vec3 toward) {
        Direction bestFacing = Direction.UP;
        int bestPointing = 0;

        if (toward == null) {
            return defaultState(bestFacing, bestPointing);
        }

        double bestScore = Double.NEGATIVE_INFINITY;
        var target = new Vector3d(toward.x, toward.y, toward.z);

        for (var facing : Direction.values()) {
            for (int pointing = 0; pointing < 4; pointing++) {
                var pose = TrackTiesBlock.getPose(pos, facing, pointing);
                var forward = pose.basis().getColumn(2, new Vector3d()).normalize();
                var toTarget = new Vector3d(target).sub(pose.translation());
                if (toTarget.lengthSquared() < 1.0e-8) {
                    continue;
                }
                toTarget.normalize();

                double score = forward.dot(toTarget);
                if (score > bestScore) {
                    bestScore = score;
                    bestFacing = facing;
                    bestPointing = pointing;
                }
            }
        }

        return defaultState(bestFacing, bestPointing);
    }

    private static BlockState defaultState(Direction facing, int pointing) {
        return Splinecart.TRACK_TIES.get().defaultBlockState()
                .setValue(TrackTiesBlock.FACING, facing)
                .setValue(TrackTiesBlock.POINTING, pointing);
    }
}