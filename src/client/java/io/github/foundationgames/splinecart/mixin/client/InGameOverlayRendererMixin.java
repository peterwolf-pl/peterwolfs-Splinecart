package io.github.foundationgames.splinecart.mixin.client;

import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public class InGameOverlayRendererMixin {
    @Inject(method = "getViewBlockingState",
            at = @At("HEAD"), cancellable = true)
    private static void splinecart$modifySuffocatingBlock(LocalPlayer player, Frustum frustum, CallbackInfoReturnable<BlockState> info) {
        var vehicle = player.getVehicle();
        while (vehicle != null) {
            if (vehicle instanceof TrackFollowerEntity) {
                var world = player.level();
                var pos = BlockPos.containing(player.getEyePosition());
                var state = world.getBlockState(pos);
                var aabb = frustum.getNearPlaneBounds().move(player.getEyePosition());

                if (state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(world, pos, aabb)) {
                    info.setReturnValue(state);
                } else {
                    info.setReturnValue(null);
                }
                return;
            }

            vehicle = vehicle.getVehicle();
        }
    }
}
