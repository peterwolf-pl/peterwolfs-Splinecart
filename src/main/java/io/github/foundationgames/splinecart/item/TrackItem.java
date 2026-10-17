package io.github.foundationgames.splinecart.item;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.BankingPreferences;
import io.github.foundationgames.splinecart.SmoothingPreferences;
import io.github.foundationgames.splinecart.SupportPreferences;
import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.component.OriginComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TrackItem extends Item {
    public static final Map<TrackType, Item> ITEMS_BY_TYPE = new HashMap<>();

    public final TrackType track;

    public TrackItem(TrackType track, Properties settings) {
        super(settings);

        this.track = track;
        ITEMS_BY_TYPE.put(track, this);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && !context.getPlayer().mayBuild()) {
            return super.useOn(context);
        }

        var world = context.getLevel();
        var pos = context.getClickedPos();
        var stack = context.getItemInHand();

        if (world.getBlockEntity(pos) instanceof TrackTiesBlockEntity ties) {
            if (context.isSecondaryUseActive()) {
                if (world.isClientSide()) {
                    return InteractionResult.SUCCESS;
                }
                boolean newSmoothing = !ties.isSmoothing();
                ties.setSmoothing(newSmoothing);
                if (context.getPlayer() != null) {
                    context.getPlayer().sendOverlayMessage(Component.translatable(newSmoothing ? "msg.splinecart.tie_smoothing_enabled" : "msg.splinecart.tie_smoothing_disabled"));
                }
                world.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 0.8f, newSmoothing ? 1.5f : 0.8f);
                return InteractionResult.CONSUME;
            }

            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            var origin = stack.get(Splinecart.ORIGIN_POS);
            if (origin != null) {
                var oPos = origin.pos();
                if (!pos.equals(oPos)
                        && world.getBlockEntity(oPos) instanceof TrackTiesBlockEntity oTies) {
                    boolean connected = false;
                    boolean banking = BankingPreferences.get(context.getPlayer());

                    if (oTies.canAcceptOutgoing() && ties.canAcceptIncoming()
                            && !oTies.hasConnectionWith(pos)) {
                        var supportMode = oTies.getTieSupportMode();
                        connected = oTies.addOutgoing(pos, this.track, supportMode, banking);
                    } else if (ties.canAcceptOutgoing() && oTies.canAcceptIncoming()
                            && !ties.hasConnectionWith(oPos)) {
                        var supportMode = ties.getTieSupportMode();
                        connected = ties.addOutgoing(oPos, this.track, supportMode, banking);
                    }

                    if (connected) {
                        world.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 1.5f, 0.7f);
                    }
                }

                stack.remove(Splinecart.ORIGIN_POS);
            } else {
                stack.set(Splinecart.ORIGIN_POS, new OriginComponent(pos));
            }
        } else {
            var origin = stack.get(Splinecart.ORIGIN_POS);
            if (origin != null) {
                if (world.isClientSide()) {
                    return InteractionResult.CONSUME;
                }

                stack.remove(Splinecart.ORIGIN_POS);
            }
        }

        return super.useOn(context);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.mayBuild() && player.isShiftKeyDown()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                boolean enabled = SmoothingPreferences.toggle(serverPlayer);
                player.sendOverlayMessage(Component.translatable(enabled ? "msg.splinecart.smoothing_preference_enabled" : "msg.splinecart.smoothing_preference_disabled"));
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_DISPENSE, SoundSource.PLAYERS, 0.8f, enabled ? 1.5f : 0.8f);
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay tooltipDisplay,
            Consumer<Component> tooltip,
            TooltipFlag type
    ) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, type);

        var origin = stack.get(Splinecart.ORIGIN_POS);
        if (origin != null) {
            origin.addToTooltip(context, tooltip, type, stack);
        }
    }
}
