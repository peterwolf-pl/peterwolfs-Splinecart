package io.github.foundationgames.splinecart;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.foundationgames.splinecart.block.TrackGeometry;
import io.github.foundationgames.splinecart.block.entity.ClientTrackGeometry;
import io.github.foundationgames.splinecart.block.entity.TrackTiesBlockEntityRenderer;
import io.github.foundationgames.splinecart.config.Config;
import io.github.foundationgames.splinecart.config.ConfigOption;
import io.github.foundationgames.splinecart.util.SUtil;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Splinecart.MOD_ID, dist = Dist.CLIENT)
public class SplinecartClient {
	public static final Config CONFIG = new Config("splinecart_client",
			() -> FMLPaths.CONFIGDIR.get()
					.resolve("splinecart").resolve("splinecart_client.properties"));

	public static final ConfigOption.BooleanOption CFG_ROTATE_CAMERA = CONFIG.optBool("rotate_camera", true);
	public static final ConfigOption.BooleanOption CFG_VBOS = CONFIG.optBool("vbos", false);
	public static final ConfigOption.IntOption CFG_TRACK_RESOLUTION = CONFIG.optInt("track_resolution", 3, 1, 16);
	public static final ConfigOption.IntOption CFG_TRACK_RENDER_DISTANCE = CONFIG.optInt("track_render_distance", 8, 4, 32);

	public SplinecartClient(IEventBus modBus) {
		SUtil.TICK_DELTA = () -> Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

		try {
			CONFIG.load();
		} catch (IOException e) {
			Splinecart.LOGGER.error("Error loading client config on mod init", e);
		}

		modBus.addListener(SplinecartClient::registerRenderers);
		modBus.addListener(SplinecartClient::registerGuiLayers);
		NeoForge.EVENT_BUS.addListener(SplinecartClient::registerClientCommands);
		TrackGeometry.CONSTRUCTOR = ClientTrackGeometry::new;
	}

	private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerBlockEntityRenderer(Splinecart.TRACK_TIES_BE.get(), TrackTiesBlockEntityRenderer::new);
		event.registerEntityRenderer(Splinecart.TRACK_FOLLOWER.get(), NoopRenderer::new);
	}

	private static void registerGuiLayers(RegisterGuiLayersEvent event) {
		event.registerAboveAll(Splinecart.id("hud"), SplinecartHud.INSTANCE::extractRenderState);
	}

	private static void registerClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(
				LiteralArgumentBuilder.<CommandSourceStack>literal("splinecartc")
						.then(CONFIG.command(LiteralArgumentBuilder.<CommandSourceStack>literal("config"),
								(source, message) -> source.sendSuccess(() -> message, false)))
		);
	}
}
