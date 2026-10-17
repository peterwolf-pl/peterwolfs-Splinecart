package io.github.foundationgames.splinecart.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.renderer.SectionOcclusionGraph;

/**
 * 1.21.11 has no CameraRenderState.smartCull and a different SectionOcclusionGraph.update signature.
 * Occlusion-culling bypass while riding a banked track is not ported.
 */
@Mixin(SectionOcclusionGraph.class)
public class WorldRendererMixin {
}
