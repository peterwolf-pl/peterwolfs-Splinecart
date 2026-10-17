package io.github.foundationgames.splinecart.util;

import io.github.foundationgames.splinecart.SupportMode;
import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.block.TrackTiesBlock;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TrackBuilder {
    private TrackBuilder() {
    }

    public static List<BlockPos> interpolateLine(BlockPos from, BlockPos to, int segments) {
        segments = Mth.clamp(segments, 1, 128);
        var positions = new ArrayList<BlockPos>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            int x = Mth.floor(from.getX() + (to.getX() - from.getX()) * t + 0.5);
            int y = Mth.floor(from.getY() + (to.getY() - from.getY()) * t + 0.5);
            int z = Mth.floor(from.getZ() + (to.getZ() - from.getZ()) * t + 0.5);
            positions.add(new BlockPos(x, y, z));
        }
        return positions;
    }

    public static int defaultSegmentCount(BlockPos from, BlockPos to) {
        return Mth.clamp((int) Math.ceil(Math.sqrt(from.distSqr(to))), 1, 128);
    }

    public static boolean placeTie(ServerLevel level, BlockPos pos, @Nullable BlockPos toward) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof TrackTiesBlock) {
            if (toward != null) {
                var oriented = TrackPlacement.stateFor(pos, Vec3.atCenterOf(toward));
                level.setBlock(pos, oriented, 3);
                if (level.getBlockEntity(pos) instanceof TrackTiesBlockEntity tie) {
                    tie.updatePose(pos, oriented);
                    tie.setChanged();
                }
            }
            return true;
        }

        if (!state.isAir() && !state.canBeReplaced()) {
            return false;
        }

        var placement = TrackPlacement.stateFor(pos, toward != null ? Vec3.atCenterOf(toward) : null);
        if (!level.setBlock(pos, placement, 3)) {
            return false;
        }

        if (level.getBlockEntity(pos) instanceof TrackTiesBlockEntity tie) {
            tie.updatePose(pos, placement);
            tie.setChanged();
        }

        return true;
    }

    public static int buildChain(ServerLevel level, List<BlockPos> positions, TrackType type, SupportMode supports) {
        return buildChain(level, positions, type, supports, false);
    }

    public static int buildChain(ServerLevel level, List<BlockPos> positions, TrackType type, SupportMode supports,
                                 boolean banking) {
        if (positions.size() < 2) {
            return 0;
        }

        for (int i = 0; i < positions.size(); i++) {
            BlockPos toward = i < positions.size() - 1 ? positions.get(i + 1) : positions.get(i - 1);
            if (!placeTie(level, positions.get(i), toward)) {
                return i;
            }
        }

        int segments = 0;
        for (int i = 0; i < positions.size() - 1; i++) {
            var current = TrackTiesBlockEntity.of(level, positions.get(i));
            if (current == null) {
                continue;
            }
            current.setNext(positions.get(i + 1), type, supports, banking);
            segments++;
        }

        return segments;
    }

    public static boolean connect(ServerLevel level, BlockPos from, BlockPos to, TrackType type, SupportMode supports) {
        return connect(level, from, to, type, supports, false);
    }

    public static boolean connect(ServerLevel level, BlockPos from, BlockPos to, TrackType type, SupportMode supports,
                                  boolean banking) {
        var fromTie = TrackTiesBlockEntity.of(level, from);
        var toTie = TrackTiesBlockEntity.of(level, to);
        if (fromTie == null || toTie == null) {
            return false;
        }

        if (!placeTie(level, from, to)) {
            return false;
        }
        if (!placeTie(level, to, from)) {
            return false;
        }

        fromTie.setNext(to, type, supports, banking);
        return true;
    }
}
