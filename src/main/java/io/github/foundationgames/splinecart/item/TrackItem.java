package io.github.foundationgames.splinecart.item;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.BankingPreferences;
import io.github.foundationgames.splinecart.SupportPreferences;
import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.component.OriginComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
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
            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            var origin = stack.get(Splinecart.ORIGIN_POS.get());
            if (origin != null) {
                var oPos = origin.pos();
                if (!pos.equals(oPos) && world.getBlockEntity(oPos) instanceof TrackTiesBlockEntity oTies && oTies.next() == null && ties.prev() == null) {
                    oTies.setNext(pos, this.track, SupportPreferences.get(context.getPlayer()),
                            BankingPreferences.get(context.getPlayer()));

                    world.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 1.5f, 0.7f);
                }

                stack.remove(Splinecart.ORIGIN_POS.get());
            } else {
                stack.set(Splinecart.ORIGIN_POS.get(), new OriginComponent(pos));
            }
        } else {
            var origin = stack.get(Splinecart.ORIGIN_POS.get());
            if (origin != null) {
                if (world.isClientSide()) {
                    return InteractionResult.CONSUME;
                }

                stack.remove(Splinecart.ORIGIN_POS.get());
            }
        }

        return super.useOn(context);
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

        var origin = stack.get(Splinecart.ORIGIN_POS.get());
        if (origin != null) {
            origin.addToTooltip(context, tooltip, type, stack);
        }
    }
}
