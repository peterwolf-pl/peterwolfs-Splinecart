package io.github.foundationgames.splinecart.mixin;

import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.util.VanillaRailHandoff;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public class AbstractMinecartMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void splinecart$tickRailHandoff(CallbackInfo info) {
        VanillaRailHandoff.tick((AbstractMinecart) (Object) this);
    }

    @Inject(method = "comeOffTrack", at = @At("HEAD"), cancellable = true)
    private void splinecart$handoffToSpline(ServerLevel level, CallbackInfo info) {
        var self = (AbstractMinecart) (Object) this;
        if (VanillaRailHandoff.isLeaving(self)) {
            self.move(MoverType.SELF, self.getDeltaMovement());
            info.cancel();
            return;
        }
        if (self.getVehicle() instanceof TrackFollowerEntity || VanillaRailHandoff.transferToSpline(self)) {
            info.cancel();
        }
    }
}
