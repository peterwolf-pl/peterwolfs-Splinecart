package io.github.foundationgames.splinecart;

import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.item.TrackItem;
import io.github.foundationgames.splinecart.item.TrackTiesItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.BlockHitResult;

public class SplinecartHud {
    public static final Component CANCEL = Component.translatable("hud.splinecart.cancel").withStyle(ChatFormatting.RED);
    public static final Component CREATE = Component.translatable("hud.splinecart.create_track").withStyle(ChatFormatting.GREEN);
    public static final String RIGHT_CLICK_HINT = "hud.splinecart.right_click";

    public void extractRenderState(GuiGraphicsExtractor guiGraphics, DeltaTracker tickCounter) {
        var client = Minecraft.getInstance();
        var world = client.level;

        if (world != null && client.player != null) {
            var mainItem = client.player.getMainHandItem();
            var offItem = client.player.getOffhandItem();
            var origin = mainItem.get(Splinecart.ORIGIN_POS.get());

            if (origin == null) {
                origin = offItem.get(Splinecart.ORIGIN_POS.get());
            }

            int w = guiGraphics.guiWidth();
            int h = guiGraphics.guiHeight();

            if (client.hitResult instanceof BlockHitResult hit) {
                var pos = hit.getBlockPos();
                if (!world.getBlockState(pos).isAir() && world.getBlockEntity(pos) instanceof TrackTiesBlockEntity ties) {
                    boolean holdingBuildingItem = mainItem.getItem() instanceof TrackItem
                            || offItem.getItem() instanceof TrackItem
                            || mainItem.getItem() instanceof TrackTiesItem
                            || offItem.getItem() instanceof TrackTiesItem
                            || mainItem.isEmpty();

                    if (origin != null) {
                        var hint = CANCEL;
                        if (!pos.equals(origin.pos())) {
                            var originTie = world.getBlockEntity(origin.pos()) instanceof TrackTiesBlockEntity o ? o : null;
                            if (originTie != null) {
                                if ((originTie.canAcceptOutgoing() && ties.canAcceptIncoming()) ||
                                        (ties.canAcceptOutgoing() && originTie.canAcceptIncoming())) {
                                    hint = CREATE;
                                }
                            }
                        }

                        var text = Component.translatable(RIGHT_CLICK_HINT, client.options.keyUse.getTranslatedKeyMessage(), hint);
                        guiGraphics.centeredText(client.font, text, w / 2, (h / 2) + 20, 0xFFFFFFFF);

                        Component smoothState = ties.isSmoothing()
                                ? Component.translatable("hud.splinecart.smoothing_on").withStyle(ChatFormatting.GREEN)
                                : Component.translatable("hud.splinecart.smoothing_off").withStyle(ChatFormatting.RED);
                        var smoothText = Component.translatable("hud.splinecart.smoothing_status", smoothState);
                        guiGraphics.centeredText(client.font, smoothText, w / 2, (h / 2) + 32, 0xFFCCCCCC);
                    } else if (holdingBuildingItem) {
                        Component smoothState = ties.isSmoothing()
                                ? Component.translatable("hud.splinecart.smoothing_on").withStyle(ChatFormatting.GREEN)
                                : Component.translatable("hud.splinecart.smoothing_off").withStyle(ChatFormatting.RED);
                        var smoothText = Component.translatable("hud.splinecart.smoothing_status", smoothState);
                        var hintText = Component.translatable("hud.splinecart.toggle_smoothing_hint",
                                client.options.keyShift.getTranslatedKeyMessage(),
                                client.options.keyUse.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY);

                        guiGraphics.centeredText(client.font, smoothText, w / 2, (h / 2) + 20, 0xFFFFFFFF);
                        guiGraphics.centeredText(client.font, hintText, w / 2, (h / 2) + 32, 0xFFAAAAAA);
                    }
                } else if (origin != null && !world.getBlockState(pos).isAir()) {
                    var text = Component.translatable(RIGHT_CLICK_HINT, client.options.keyUse.getTranslatedKeyMessage(), CANCEL);
                    guiGraphics.centeredText(client.font, text, w / 2, (h / 2) + 20, 0xFFFFFFFF);
                }
            }
        }
    }
}
