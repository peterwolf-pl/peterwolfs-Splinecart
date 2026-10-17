package io.github.foundationgames.splinecart.item;

import io.github.foundationgames.splinecart.SmoothingPreferences;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Places track ties against blocks or one block from the player in air. */
public class TrackTiesItem extends BlockItem {
    public TrackTiesItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.mayBuild()) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                boolean enabled = SmoothingPreferences.toggle(serverPlayer);
                player.sendOverlayMessage(Component.translatable(enabled ? "msg.splinecart.smoothing_preference_enabled" : "msg.splinecart.smoothing_preference_disabled"));
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_DISPENSE, SoundSource.PLAYERS, 0.8f, enabled ? 1.5f : 0.8f);
            }
            return InteractionResult.SUCCESS;
        }

        float pitch = player.getXRot();
        Direction facing = player.getDirection();
        BlockPos placePos;
        Direction clickedFace;

        if (pitch > 45.0f) {
            placePos = player.blockPosition().below();
            clickedFace = Direction.UP;
        } else if (pitch < -45.0f) {
            placePos = player.blockPosition().above(2);
            clickedFace = Direction.DOWN;
        } else {
            placePos = player.blockPosition().relative(facing);
            clickedFace = facing.getOpposite();
        }

        if (!level.getBlockState(placePos).canBeReplaced()) {
            return InteractionResult.PASS;
        }

        var hit = new BlockHitResult(Vec3.atCenterOf(placePos), clickedFace, placePos, true);
        return this.place(new BlockPlaceContext(player, hand, player.getItemInHand(hand), hit));
    }
}
