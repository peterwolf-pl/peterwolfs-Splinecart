package io.github.foundationgames.splinecart.block.entity;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.SupportMode;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.util.Pose;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3d;
import net.minecraft.client.renderer.LightTexture;

import java.util.Set;

public class TrackTiesBlockEntityRenderer implements BlockEntityRenderer<TrackTiesBlockEntity, TrackTiesBlockEntityRenderer.TrackTiesRenderState> {
    public static final int WHITE = 0xFFFFFFFF;
    public static final Vector3f WHITEF = new Vector3f(1, 1, 1);
    public static final Identifier TRACK_TEXTURE = Splinecart.id("textures/track.png");
    public static final Identifier TRACK_OVERLAY_TEXTURE = Splinecart.id("textures/track_overlay.png");
    public static final Identifier POSE_TEXTURE_DEBUG = Splinecart.id("textures/debug.png");
    public static final Identifier SLEEPER_TEXTURE = Identifier.withDefaultNamespace("textures/block/oak_planks.png");
    public static final Identifier CONNECTOR_TEXTURE = Identifier.withDefaultNamespace("textures/block/cauldron_side.png");
    public static final Identifier STEEL_RAIL_TEXTURE = Identifier.withDefaultNamespace("textures/block/iron_block.png");
    public static final Identifier STEEL_PILLAR_TEXTURE = Identifier.withDefaultNamespace("textures/block/white_concrete.png");

    public TrackTiesBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public TrackTiesRenderState createRenderState() {
        return new TrackTiesRenderState();
    }

    @Override
    public void extractRenderState(TrackTiesBlockEntity entity, TrackTiesRenderState state, float partialTick, net.minecraft.world.phys.Vec3 cameraPos, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(entity, state, crumbling);
        state.trackTies = entity;
        state.pose = entity.pose();
        entity.clientTime += partialTick;
        state.clientTime = entity.clientTime;
        state.estimatedTrackLength = entity.estimatedTrackLength();
        state.estimatedAltTrackLength = entity.estimatedAltTrackLength();
        state.nextEndpoint = entity.renderNextEndpoint();
        state.nextAltEndpoint = entity.renderNextAltEndpoint();
        state.nextBanking = entity.nextBanking();
        state.nextAltBanking = entity.nextAltBanking();
        state.nextSupports = entity.nextSupports();
        state.nextAltSupports = entity.nextAltSupports();
        state.steelPillarTs = entity.steelPillarTs();
        state.steelPillarAltTs = entity.steelPillarAltTs();
        state.prev = entity.prev();
        state.prevAlt = entity.prevAlt();
        state.power = entity.power();
    }

    @Override
    public void submit(TrackTiesRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (Minecraft.getInstance().getDebugOverlay().showDebugScreen()) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(POSE_TEXTURE_DEBUG), (entry, buffer) -> {
                renderDebug(state.pose, entry, buffer);
            });
            poseStack.popPose();
        }

        int trackResolution = SplinecartClient.CFG_TRACK_RESOLUTION.get();
        poseStack.pushPose();
        var pos = state.blockPos;
        poseStack.translate(-pos.getX(), -pos.getY(), -pos.getZ());

        // 1) Render primary branch if present
        if (state.nextEndpoint != null) {
            int segs = trackResolution * Math.max((int) state.estimatedTrackLength, 2);
            renderBranch(state, poseStack, nodeCollector, state.nextEndpoint, state.nextSupports,
                    state.nextBanking, state.steelPillarTs, segs);
        }

        // 2) Render alternate branch (switch) if present
        if (state.nextAltEndpoint != null) {
            int segsAlt = trackResolution * Math.max((int) state.estimatedAltTrackLength, 2);
            renderBranch(state, poseStack, nodeCollector, state.nextAltEndpoint, state.nextAltSupports,
                    state.nextAltBanking, state.steelPillarAltTs, segsAlt);
        }

        poseStack.popPose();
    }

    private void renderBranch(TrackTiesRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                              TrackTiesBlockEntity.RenderEndpoint endpoint, SupportMode supports,
                              boolean banking, double[] steelPillars, int segs) {
        var overlayColor = new Vector3f(WHITEF);
        float[] overlayVOffset = {0};

        var trackType = endpoint.type();
        if (trackType.overlay != null) {
            int power = Math.max(state.power, endpoint.power());
            trackType.overlay.calculateEffects(power, state.clientTime, overlayColor, overlayVOffset);
        }

        // Main track
        nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutNoCullZOffset(getTexture()), (entry, buffer) -> {
            var trackProvider = new TrackRenderer.DirectBufferProvider(buffer);
            TrackRenderer.renderTrack(entry, entry,
                    trackProvider, null,
                    OverlayTexture.NO_OVERLAY, state.lightCoords, segs,
                    0, overlayColor,
                    state.trackTies, state.prev, endpoint);
        });

        // Supports
        var sleeperStyle = TrackSupportRenderer.sleeperStyle(supports, state.pose, endpoint.pose(), banking);
        if (sleeperStyle == TrackSupportRenderer.SleeperStyle.WOOD) {
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(SLEEPER_TEXTURE), (entry, buffer) ->
                    TrackSupportRenderer.renderSleepers(entry, buffer, OverlayTexture.NO_OVERLAY, segs,
                            state.trackTies.getLevel(), state.pose, endpoint.pose(), banking));
        } else {
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(CONNECTOR_TEXTURE), (entry, buffer) ->
                    TrackSupportRenderer.renderConnectors(entry, buffer, OverlayTexture.NO_OVERLAY, segs,
                            state.trackTies.getLevel(), state.pose, endpoint.pose(), banking));
        }
        if (supports == SupportMode.STEEL) {
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(STEEL_RAIL_TEXTURE), (entry, buffer) ->
                    TrackSupportRenderer.renderThirdRail(entry, buffer, OverlayTexture.NO_OVERLAY, segs,
                            state.trackTies.getLevel(), state.pose, endpoint.pose(), banking));
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(STEEL_PILLAR_TEXTURE), (entry, buffer) ->
                    TrackSupportRenderer.renderSteelPillars(entry, buffer, OverlayTexture.NO_OVERLAY,
                            state.trackTies.getLevel(), state.pose, endpoint.pose(),
                            banking, steelPillars));
        }

        // Overlay
        if (trackType.overlay != null) {
            nodeCollector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(getTrackOverlayTexture()), (entry, buffer) -> {
                var overlayProvider = new TrackRenderer.DirectBufferProvider(buffer);
                TrackRenderer.renderTrack(entry, entry,
                        null, overlayProvider,
                        OverlayTexture.NO_OVERLAY, state.lightCoords, segs,
                        overlayVOffset[0], overlayColor,
                        state.trackTies, state.prev, endpoint);
            });
        }
    }

    protected Identifier getTexture() {
        return TRACK_TEXTURE;
    }

    protected Identifier getTrackOverlayTexture() {
        return TRACK_OVERLAY_TEXTURE;
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public boolean shouldRender(TrackTiesBlockEntity entity, Vec3 cameraPos) {
        var endpoint = entity.renderNextEndpoint();
        var altEndpoint = entity.renderNextAltEndpoint();
        if (endpoint == null && altEndpoint == null) {
            return BlockEntityRenderer.super.shouldRender(entity, cameraPos);
        }

        var start = entity.pose();
        var position = new Vector3d();
        var basis = new org.joml.Matrix3d();
        var derivative = new Vector3d();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        // Include the whole cubic path, not just the origin tie, in the
        // distance test used by BlockEntityRenderDispatcher.
        if (endpoint != null) {
            for (int i = 0; i <= 16; i++) {
                start.interpolate(endpoint.pose(), (double) i / 16.0, position, basis, derivative);
                minX = Math.min(minX, position.x());
                minY = Math.min(minY, position.y());
                minZ = Math.min(minZ, position.z());
                maxX = Math.max(maxX, position.x());
                maxY = Math.max(maxY, position.y());
                maxZ = Math.max(maxZ, position.z());
            }
        }
        if (altEndpoint != null) {
            for (int i = 0; i <= 16; i++) {
                start.interpolate(altEndpoint.pose(), (double) i / 16.0, position, basis, derivative);
                minX = Math.min(minX, position.x());
                minY = Math.min(minY, position.y());
                minZ = Math.min(minZ, position.z());
                maxX = Math.max(maxX, position.x());
                maxY = Math.max(maxY, position.y());
                maxZ = Math.max(maxZ, position.z());
            }
        }

        double margin = getViewDistance() + 2.0;
        return cameraPos.x >= minX - margin && cameraPos.x <= maxX + margin
                && cameraPos.y >= minY - margin && cameraPos.y <= maxY + margin
                && cameraPos.z >= minZ - margin && cameraPos.z <= maxZ + margin;
    }

    @Override
    public int getViewDistance() {
        return SplinecartClient.CFG_TRACK_RENDER_DISTANCE.get() * 16;
    }

    private static void renderDebug(Pose pose, PoseStack.Pose entry, VertexConsumer buffer) {
        var posMat = entry.pose();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                posMat.setRowColumn(x, y, (float) pose.basis().getRowColumn(x, y));
            }
        }

        buffer.addVertex(entry, 1, 0, 1).setColor(WHITE).setUv(0, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
        buffer.addVertex(entry, 0, 0, 1).setColor(WHITE).setUv(1, 0)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
        buffer.addVertex(entry, 0, 0, 0).setColor(WHITE).setUv(1, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
        buffer.addVertex(entry, 1, 0, 0).setColor(WHITE).setUv(0, 1)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(entry, 0, 1, 0);
    }

    public static void queueVboRebuildsForChunkUpdate(int sectionX, int sectionY, int sectionZ, Set<BlockEntity> blockEntities) {
        for (var be : blockEntities) if (be instanceof TrackTiesBlockEntity ties) {
            if (ties.geometry.isInChunk(sectionX, sectionY, sectionZ)) {
                ties.geometry.needsRebuild = true;
            }
        }
    }

    public static class TrackTiesRenderState extends BlockEntityRenderState {
        public TrackTiesBlockEntity trackTies;
        public Pose pose;
        public float clientTime;
        public double estimatedTrackLength;
        public double estimatedAltTrackLength;
        public TrackTiesBlockEntity.RenderEndpoint nextEndpoint;
        public TrackTiesBlockEntity.RenderEndpoint nextAltEndpoint;
        public boolean nextBanking;
        public boolean nextAltBanking;
        public SupportMode nextSupports = SupportMode.AUTO;
        public SupportMode nextAltSupports = SupportMode.AUTO;
        public double[] steelPillarTs = new double[0];
        public double[] steelPillarAltTs = new double[0];
        public TrackTiesBlockEntity prev;
        public TrackTiesBlockEntity prevAlt;
        public int power;
    }
}
