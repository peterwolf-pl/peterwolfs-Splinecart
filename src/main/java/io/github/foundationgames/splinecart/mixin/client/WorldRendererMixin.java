package io.github.foundationgames.splinecart.mixin.client;

import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ChunkLoadingRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionOcclusionGraph.class)
public class WorldRendererMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private void splinecart$disableOcclusionCullingWhileOnTrack(CameraRenderState camera, int viewDistance, ChunkLoadingRenderState chunkLoading, CallbackInfo info) {
        if (SplinecartClient.CFG_ROTATE_CAMERA.get()) {
            var entity = Minecraft.getInstance().getCameraEntity();
            while (entity != null) {
                entity = entity.getVehicle();
                if (entity instanceof TrackFollowerEntity) {
                    camera.smartCull = false;
                    return;
                }
            }
        }
    }
}
