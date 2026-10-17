package io.github.foundationgames.splinecart.util;

import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public record TrackProgress(BlockPos startBlock, Pose startPose, BlockPos endBlock, Pose endPose,
                            boolean banking, boolean orientationOnly, double t) {
    public static final StreamCodec<ByteBuf, TrackProgress> PACKET_CODEC = StreamCodec.of(
            (buf, progress) -> {
                BlockPos.STREAM_CODEC.encode(buf, progress.startBlock());
                Pose.PACKET_CODEC.encode(buf, progress.startPose());
                BlockPos.STREAM_CODEC.encode(buf, progress.endBlock());
                Pose.PACKET_CODEC.encode(buf, progress.endPose());
                buf.writeBoolean(progress.banking());
                buf.writeBoolean(progress.orientationOnly());
                buf.writeDouble(progress.t());
            },
            buf -> new TrackProgress(
                    BlockPos.STREAM_CODEC.decode(buf), Pose.PACKET_CODEC.decode(buf),
                    BlockPos.STREAM_CODEC.decode(buf), Pose.PACKET_CODEC.decode(buf),
                    buf.readBoolean(), buf.readBoolean(), buf.readDouble())
    );

    public static final EntityDataSerializer<TrackProgress> DATA_HANDLER = EntityDataSerializer.forValueType(PACKET_CODEC);

    public static TrackProgress of(TrackTiesBlockEntity e, double t) {
        return of(e, null, t);
    }

    public static TrackProgress of(TrackTiesBlockEntity e, @Nullable BlockPos targetEnd, double t) {
        boolean isAlt = targetEnd != null && targetEnd.equals(e.nextAltPos());
        var endpoint = isAlt ? e.renderNextAltEndpoint() : e.renderNextEndpoint();
        var nextPos = endpoint != null ? endpoint.blockPos()
                : (isAlt ? (e.nextAltPos() != null ? e.nextAltPos() : e.getBlockPos())
                         : (e.nextPos() != null ? e.nextPos() : e.getBlockPos()));
        var nextPose = endpoint != null ? endpoint.pose() : e.pose();
        boolean banking = isAlt ? e.nextAltBanking() : e.nextBanking();

        return new TrackProgress(e.getBlockPos(), e.pose(), nextPos, nextPose, banking, false, t);
    }

    public static TrackProgress empty(Vec3 pos) {
        var bpos = BlockPos.containing(pos);
        var pose = new Pose(new Vector3d(pos.x(), pos.y(), pos.z()), new Matrix3d());
        return new TrackProgress(bpos, pose, bpos, pose, false, true, 0);
    }

    public boolean getOrientation(TrackProgress prev, double delta, Vector3d pos, Quaternionf rotation) {
        if (orientationOnly()) {
            return false;
        }

        double t = Mth.lerp(delta, prev.t(), t());

        if (!prev.orientationOnly()) {
            if (prev.startBlock().equals(startBlock()) && prev.endBlock().equals(endBlock())) {
                var mat = new Matrix3d().rotate(rotation);
                TrackBanking.interpolate(startPose(), endPose(), banking(), t, pos, mat, new Vector3d());
                mat.getNormalizedRotation(rotation);
            }
        } else {
            var endPos = new Vector3d();
            startPose().interpolateTranslation(endPose(), t, endPos, new Vector3d());
            pos.lerp(endPos, delta);
        }

        return true;
    }
}
