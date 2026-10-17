package io.github.foundationgames.splinecart.util;

import io.github.foundationgames.splinecart.Splinecart;
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

    public static TrackTiesBlock tieBlockFor(SupportMode supports) {
        return supports == SupportMode.STEEL ? Splinecart.STEEL_TRACK_TIES : Splinecart.WOOD_TRACK_TIES;
    }

    public static boolean placeTie(ServerLevel level, BlockPos pos, @Nullable BlockPos toward) {
        return placeTie(level, pos, toward, Splinecart.WOOD_TRACK_TIES);
    }

    public static boolean placeTie(ServerLevel level, BlockPos pos, @Nullable BlockPos toward, TrackTiesBlock tieBlock) {
        return placeTie(level, pos, toward, tieBlock, true);
    }

    public static boolean placeTie(ServerLevel level, BlockPos pos, @Nullable BlockPos toward, TrackTiesBlock tieBlock, boolean smoothing) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof TrackTiesBlock existingTieBlock) {
            if (toward != null) {
                var oriented = state;
                if (state.hasProperty(TrackTiesBlock.FACING) && state.hasProperty(TrackTiesBlock.POINTING)) {
                    var facing = state.getValue(TrackTiesBlock.FACING);
                    var target = new org.joml.Vector3d(toward.getX() - pos.getX(), toward.getY() - pos.getY(), toward.getZ() - pos.getZ());
                    if (target.lengthSquared() > 1.0e-8) {
                        target.normalize();
                        int bestPointing = TrackPlacement.bestPointingFor(pos, facing, target);
                        oriented = state.setValue(TrackTiesBlock.POINTING, bestPointing);
                    }
                }
                level.setBlock(pos, oriented, 3);
                if (level.getBlockEntity(pos) instanceof TrackTiesBlockEntity tie) {
                    tie.setSmoothing(smoothing);
                    tie.updatePose(pos, oriented);
                    tie.setChanged();
                }
            }
            return true;
        }

        if (!state.isAir() && !state.canBeReplaced()) {
            return false;
        }

        var placement = TrackPlacement.stateFor(pos, toward != null ? Vec3.atCenterOf(toward) : null, tieBlock);
        if (!level.setBlock(pos, placement, 3)) {
            return false;
        }

        if (level.getBlockEntity(pos) instanceof TrackTiesBlockEntity tie) {
            tie.setSmoothing(smoothing);
            tie.updatePose(pos, placement);
            tie.setChanged();
        }

        return true;
    }

    public static int buildChain(ServerLevel level, List<BlockPos> positions, TrackType type, SupportMode supports) {
        return buildChain(level, positions, type, supports, false, true);
    }

    public static int buildChain(ServerLevel level, List<BlockPos> positions, TrackType type, SupportMode supports,
                                 boolean banking) {
        return buildChain(level, positions, type, supports, banking, true);
    }

    public static int buildChain(ServerLevel level, List<BlockPos> positions, TrackType type, SupportMode supports,
                                 boolean banking, boolean smoothing) {
        if (positions.size() < 2) {
            return 0;
        }

        var tieBlock = tieBlockFor(supports);
        for (int i = 0; i < positions.size(); i++) {
            BlockPos toward = i < positions.size() - 1 ? positions.get(i + 1) : positions.get(i - 1);
            if (!placeTie(level, positions.get(i), toward, tieBlock, smoothing)) {
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
        return connect(level, from, to, type, supports, false, true);
    }

    public static boolean connect(ServerLevel level, BlockPos from, BlockPos to, TrackType type, SupportMode supports,
                                  boolean banking) {
        return connect(level, from, to, type, supports, banking, true);
    }

    public static boolean connect(ServerLevel level, BlockPos from, BlockPos to, TrackType type, SupportMode supports,
                                  boolean banking, boolean smoothing) {
        var fromTie = TrackTiesBlockEntity.of(level, from);
        var toTie = TrackTiesBlockEntity.of(level, to);
        if (fromTie == null || toTie == null) {
            return false;
        }

        var tieBlock = tieBlockFor(supports);
        if (!placeTie(level, from, to, tieBlock, smoothing)) {
            return false;
        }
        if (!placeTie(level, to, from, tieBlock, smoothing)) {
            return false;
        }

        fromTie.setNext(to, type, supports, banking);
        return true;
    }
}
