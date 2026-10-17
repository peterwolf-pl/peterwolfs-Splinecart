package io.github.foundationgames.splinecart.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.foundationgames.splinecart.SupportMode;
import io.github.foundationgames.splinecart.block.TrackTiesBlock;
import io.github.foundationgames.splinecart.util.Pose;
import io.github.foundationgames.splinecart.util.SplineSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Lightweight cuboid supports generated directly into the spline render buffer. */
public final class TrackSupportRenderer {
    private static final double SLEEPER_SPACING = 0.5;
    private static final double CONNECTOR_SPACING = 1.0;
    private static final int STEEL_COLOR = 0xFF3A3C40;
    private static final Vector3d WORLD_RIGHT = new Vector3d(1, 0, 0);
    private static final Vector3d WORLD_UP = new Vector3d(0, 1, 0);
    private static final Vector3d WORLD_FORWARD = new Vector3d(0, 0, 1);
    private static final double SHAFT_WIDTH = 0.22;
    private static final double SHAFT_STEP = 10.0;
    private static final double SHAFT_THICKEN = 1.08;
    private static final double TAPER_WIDTH = 0.12;
    private static final double NECK_WIDTH = 0.07;

    private TrackSupportRenderer() {
    }

    public enum SleeperStyle {
        WOOD,
        CONNECTOR
    }

    public static SleeperStyle sleeperStyle(SupportMode mode, Pose start, Pose end, boolean banking) {
        return sleeperStyle(mode, new SplineSegment(start, end, banking));
    }

    public static SleeperStyle sleeperStyle(SupportMode mode, SplineSegment segment) {
        // Support mode only picks the horizontal construction (wood ties vs 3-rail).
        // Steel/metal keeps the original connector pads, including on flat segments.
        boolean horizontal = segment.isHorizontal();
        return switch (mode) {
            case WOOD -> SleeperStyle.WOOD;
            case STEEL, METAL -> SleeperStyle.CONNECTOR;
            case AUTO, NONE -> horizontal ? SleeperStyle.WOOD : SleeperStyle.CONNECTOR;
        };
    }

    public static void renderSleepers(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int segs,
                                      Level level, Pose start, Pose end, boolean banking) {
        var segment = new SplineSegment(start, end, banking);

        renderRegularly(entry, buffer, overlay, segs, level, segment, SLEEPER_SPACING, (position, basis, derivative, light) -> {
            var forward = derivative.normalize(new Vector3d());
            var right = basis.getColumn(0, new Vector3d()).normalize();
            var up = basis.getColumn(1, new Vector3d()).normalize();
            var center = new Vector3d(position).fma(-0.075, up);
            cuboid(entry, buffer, overlay, light, center, right, up, forward, 1.30, 0.12, 0.22);
        });
    }

    public static void renderThirdRail(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int segs,
                                       Level level, Pose start, Pose end, boolean banking) {
        var segment = new SplineSegment(start, end, banking);
        renderRegularly(entry, buffer, overlay, segs, level, segment, 0.4, (position, basis, derivative, light) -> {
            var right = basis.getColumn(0, new Vector3d()).normalize();
            var up = basis.getColumn(1, new Vector3d()).normalize();
            var forward = derivative.normalize(new Vector3d());
            var center = new Vector3d(position).fma(-0.22, up);
            cuboid(entry, buffer, overlay, light, center, right, up, forward, 0.20, 0.10, 0.46);
        });
    }

    public static void renderSteelPillars(PoseStack.Pose entry, VertexConsumer buffer, int overlay,
                                          Level level, Pose start, Pose end, boolean banking, double[] pillarTs) {
        if (pillarTs.length == 0) {
            return;
        }
        var segment = new SplineSegment(start, end, banking);
        var position = new Vector3d();
        var basis = new Matrix3d();
        var derivative = new Vector3d();
        var up = new Vector3d();
        for (double t : pillarTs) {
            segment.interpolate(t, position, basis, derivative);
            basis.getColumn(1, up).normalize();
            renderSteelPillar(entry, buffer, overlay, level, position, up);
        }
    }

    private static void renderSteelPillar(PoseStack.Pose entry, VertexConsumer buffer, int overlay,
                                          Level level, Vector3d trackPos, Vector3d up) {
        var attach = new Vector3d(trackPos).fma(-0.22, up);
        double groundY = groundY(level, attach.x(), attach.y(), attach.z());
        double topY = attach.y();
        if (topY - groundY < 0.12) {
            return;
        }

        int light = packedLight(level, BlockPos.containing(attach.x(), attach.y(), attach.z()));
        double neckHeight = 0.20;
        double taperHeight = 0.38;
        double neckStartY = Math.max(groundY + 0.04, topY - neckHeight);
        double taperStartY = Math.max(groundY + 0.02, neckStartY - taperHeight);

        renderSteppedShaft(entry, buffer, overlay, light, attach.x(), attach.z(), topY, taperStartY, groundY);
        if (neckStartY - taperStartY > 0.03) {
            cuboid(entry, buffer, overlay, light, STEEL_COLOR,
                    new Vector3d(attach.x(), (taperStartY + neckStartY) * 0.5, attach.z()),
                    WORLD_RIGHT, WORLD_UP, WORLD_FORWARD,
                    TAPER_WIDTH, neckStartY - taperStartY, TAPER_WIDTH);
        }

        var neckRight = new Vector3d(WORLD_UP).cross(up);
        if (neckRight.lengthSquared() < 1.0e-6) {
            neckRight.set(WORLD_RIGHT);
        } else {
            neckRight.normalize();
        }
        var neckForward = new Vector3d(up).cross(neckRight).normalize();
        var neckCenter = new Vector3d(trackPos).fma(-0.34, up);
        cuboid(entry, buffer, overlay, light, STEEL_COLOR,
                neckCenter, neckRight, up, neckForward,
                NECK_WIDTH, 0.26, NECK_WIDTH);
    }

    private static void renderSteppedShaft(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int light,
                                           double x, double z, double trackY, double shaftTopY, double groundY) {
        double cursor = shaftTopY;
        while (cursor - groundY > 0.06) {
            double depth = Math.max(0.0, trackY - cursor);
            int step = (int) Math.floor(depth / SHAFT_STEP);
            double nextBoundary = trackY - (step + 1) * SHAFT_STEP;
            double yBottom = Math.max(groundY, nextBoundary);
            double height = cursor - yBottom;
            if (height > 0.04) {
                double width = SHAFT_WIDTH * Math.pow(SHAFT_THICKEN, step);
                cuboid(entry, buffer, overlay, light, STEEL_COLOR,
                        new Vector3d(x, (cursor + yBottom) * 0.5, z),
                        WORLD_RIGHT, WORLD_UP, WORLD_FORWARD,
                        width, height, width);
            }
            if (yBottom >= cursor - 1.0e-6) {
                break;
            }
            cursor = yBottom;
        }
    }

    private static double groundY(Level level, double x, double y, double z) {
        int bx = net.minecraft.util.Mth.floor(x);
        int bz = net.minecraft.util.Mth.floor(z);
        int by = net.minecraft.util.Mth.floor(y) - 1;
        int minY = level.getMinY();
        for (int gy = by; gy >= minY; gy--) {
            var pos = new BlockPos(bx, gy, bz);
            var state = level.getBlockState(pos);
            if (isIgnoredGround(state)) {
                continue;
            }
            return gy + 1.0;
        }
        return minY;
    }

    private static boolean isIgnoredGround(BlockState state) {
        return state.isAir()
                || state.is(BlockTags.REPLACEABLE)
                || state.getBlock() instanceof TrackTiesBlock
                || state.getBlock() instanceof FenceBlock
                || state.is(Blocks.IRON_BARS)
                || state.is(Blocks.IRON_BLOCK);
    }

    public static void renderConnectors(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int segs,
                                        Level level, Pose start, Pose end, boolean banking) {
        var segment = new SplineSegment(start, end, banking);

        renderRegularly(entry, buffer, overlay, segs, level, segment, CONNECTOR_SPACING, (position, basis, derivative, light) -> {
            var right = basis.getColumn(0, new Vector3d()).normalize();
            var up = basis.getColumn(1, new Vector3d()).normalize();
            var forward = derivative.normalize(new Vector3d());

            cuboid(entry, buffer, overlay, light, new Vector3d(position).fma(-0.055, up),
                    right, up, forward, 1.14, 0.10, 0.16);
            cuboid(entry, buffer, overlay, light, new Vector3d(position).fma(0.39, right).fma(0.015, up),
                    right, up, forward, 0.14, 0.16, 0.22);
            cuboid(entry, buffer, overlay, light, new Vector3d(position).fma(-0.39, right).fma(0.015, up),
                    right, up, forward, 0.14, 0.16, 0.22);
        });
    }

    private static void renderRegularly(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int segs,
                                        Level level, SplineSegment segment, double preferredSpacing, SupportConsumer consumer) {
        int samples = Math.clamp(segs * 2, 8, 128);
        var cumulative = new double[samples + 1];
        var previous = new Vector3d(segment.startPose().translation());
        var position = new Vector3d();
        var basis = new Matrix3d();
        var derivative = new Vector3d();

        for (int i = 1; i <= samples; i++) {
            segment.interpolate((double) i / samples, position, basis, derivative);
            cumulative[i] = cumulative[i - 1] + position.distance(previous);
            previous.set(position);
        }

        double length = cumulative[samples];
        if (length < 0.1) return;
        int count = Math.max(1, (int) Math.round(length / preferredSpacing));
        double spacing = length / count;
        int sample = 1;

        for (int i = 0; i < count; i++) {
            double target = (i + 0.5) * spacing;
            while (sample < samples && cumulative[sample] < target) sample++;
            double sectionLength = cumulative[sample] - cumulative[sample - 1];
            double local = sectionLength > 1.0e-8 ? (target - cumulative[sample - 1]) / sectionLength : 0.0;
            double t = (sample - 1 + local) / samples;
            segment.interpolate(t, position, basis, derivative);
            int light = packedLight(level, BlockPos.containing(position.x(), position.y(), position.z()));
            consumer.render(position, basis, derivative, light);
        }
    }

    private static int packedLight(net.minecraft.world.level.Level world, BlockPos pos) {
        return (world.getBrightness(LightLayer.BLOCK, pos) << 4) | (world.getBrightness(LightLayer.SKY, pos) << 20);
    }

    private static void cuboid(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int light,
                               Vector3d center, Vector3d right, Vector3d up, Vector3d forward,
                               double width, double height, double depth) {
        cuboid(entry, buffer, overlay, light, TrackTiesBlockEntityRenderer.WHITE, center, right, up, forward,
                width, height, depth);
    }

    private static void cuboid(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int light, int color,
                               Vector3d center, Vector3d right, Vector3d up, Vector3d forward,
                               double width, double height, double depth) {
        double x = width * 0.5;
        double y = height * 0.5;
        double z = depth * 0.5;
        var corners = new Vector3d[8];
        int index = 0;
        for (int yi = -1; yi <= 1; yi += 2) {
            for (int zi = -1; zi <= 1; zi += 2) {
                for (int xi = -1; xi <= 1; xi += 2) {
                    corners[index++] = new Vector3d(center)
                            .fma(xi * x, right).fma(yi * y, up).fma(zi * z, forward);
                }
            }
        }

        face(entry, buffer, overlay, light, color, corners, 1, 5, 7, 3, right);
        face(entry, buffer, overlay, light, color, corners, 4, 0, 2, 6, new Vector3d(right).negate());
        face(entry, buffer, overlay, light, color, corners, 2, 3, 7, 6, up);
        face(entry, buffer, overlay, light, color, corners, 4, 5, 1, 0, new Vector3d(up).negate());
        face(entry, buffer, overlay, light, color, corners, 5, 4, 6, 7, forward);
        face(entry, buffer, overlay, light, color, corners, 0, 1, 3, 2, new Vector3d(forward).negate());
    }

    private static void face(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int light, int color,
                             Vector3d[] corners, int a, int b, int c, int d, Vector3d normal) {
        vertex(entry, buffer, overlay, light, color, corners[a], normal, 0, 0);
        vertex(entry, buffer, overlay, light, color, corners[b], normal, 0, 1);
        vertex(entry, buffer, overlay, light, color, corners[c], normal, 1, 1);
        vertex(entry, buffer, overlay, light, color, corners[d], normal, 1, 0);
    }

    private static void vertex(PoseStack.Pose entry, VertexConsumer buffer, int overlay, int light, int color,
                               Vector3d point, Vector3d normal, float u, float v) {
        buffer.addVertex(entry, new Vector3f((float) point.x(), (float) point.y(), (float) point.z()))
                .setColor(color).setUv(u, v).setOverlay(overlay).setLight(light)
                .setNormal(entry, (float) normal.x(), (float) normal.y(), (float) normal.z());
    }

    @FunctionalInterface
    private interface SupportConsumer {
        void render(Vector3d position, Matrix3d basis, Vector3d derivative, int light);
    }
}
