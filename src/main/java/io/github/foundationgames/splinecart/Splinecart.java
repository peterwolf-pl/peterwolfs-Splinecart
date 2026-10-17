package io.github.foundationgames.splinecart;

import io.github.foundationgames.splinecart.block.TrackTiesBlock;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.command.SplinecartCommands;
import io.github.foundationgames.splinecart.component.OriginComponent;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import io.github.foundationgames.splinecart.item.TrackItem;
import io.github.foundationgames.splinecart.item.TrackTiesItem;
import io.github.foundationgames.splinecart.util.TrackProgress;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Splinecart.MOD_ID)
public class Splinecart {
	public static final String MOD_ID = "splinecart";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
	public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(MOD_ID);
	public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
	public static final DeferredRegister<EntityDataSerializer<?>> ENTITY_DATA_SERIALIZERS = DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, MOD_ID);

	public static final DeferredBlock<TrackTiesBlock> TRACK_TIES = BLOCKS.registerBlock(
			"track_ties",
			TrackTiesBlock::new,
			() -> BlockBehaviour.Properties.ofLegacyCopy(Blocks.RAIL)
	);

	public static final ResourceKey<Item> TRACK_TIES_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("track_ties"));
	public static final DeferredItem<TrackTiesItem> TRACK_TIES_ITEM = ITEMS.register(
			"track_ties",
			() -> new TrackTiesItem(TRACK_TIES.get(), new Item.Properties()
					.component(DataComponents.LORE, lore(Component.translatable("item.splinecart.track_ties.desc").withStyle(ChatFormatting.GRAY)))
					.useBlockDescriptionPrefix()
					.setId(TRACK_TIES_ITEM_KEY))
	);

	public static final ResourceKey<Item> TRACK_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("track"));
	public static final DeferredItem<TrackItem> TRACK = ITEMS.register(
			"track",
			() -> new TrackItem(TrackType.DEFAULT, new Item.Properties()
					.component(DataComponents.LORE, lore(Component.translatable("item.splinecart.track.desc").withStyle(ChatFormatting.GRAY)))
					.setId(TRACK_ITEM_KEY))
	);

	public static final ResourceKey<Item> CHAIN_DRIVE_TRACK_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("chain_drive_track"));
	public static final DeferredItem<TrackItem> CHAIN_DRIVE_TRACK = ITEMS.register(
			"chain_drive_track",
			() -> new TrackItem(TrackType.CHAIN_DRIVE, new Item.Properties()
					.component(DataComponents.LORE, lore(Component.translatable("item.splinecart.chain_drive_track.desc").withStyle(ChatFormatting.GRAY)))
					.setId(CHAIN_DRIVE_TRACK_ITEM_KEY))
	);

	public static final ResourceKey<Item> MAGNETIC_TRACK_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("magnetic_track"));
	public static final DeferredItem<TrackItem> MAGNETIC_TRACK = ITEMS.register(
			"magnetic_track",
			() -> new TrackItem(TrackType.MAGNETIC, new Item.Properties()
					.component(DataComponents.LORE, lore(Component.translatable("item.splinecart.magnetic_track.desc").withStyle(ChatFormatting.GRAY)))
					.setId(MAGNETIC_TRACK_ITEM_KEY))
	);

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrackTiesBlockEntity>> TRACK_TIES_BE = BLOCK_ENTITY_TYPES.register(
			"track_ties",
			() -> new BlockEntityType<>(TrackTiesBlockEntity::new, Set.of(TRACK_TIES.get()))
	);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<OriginComponent>> ORIGIN_POS = DATA_COMPONENTS.register(
			"origin",
			() -> DataComponentType.<OriginComponent>builder().persistent(OriginComponent.CODEC).build()
	);

	public static final ResourceKey<EntityType<?>> TRACK_FOLLOWER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("track_follower"));
	public static final DeferredHolder<EntityType<?>, EntityType<TrackFollowerEntity>> TRACK_FOLLOWER = ENTITY_TYPES.register(
			"track_follower",
			() -> EntityType.Builder.<TrackFollowerEntity>of(TrackFollowerEntity::new, MobCategory.MISC)
					.updateInterval(2)
					.sized(0.25f, 0.25f)
					.build(TRACK_FOLLOWER_KEY)
	);

	public static final TagKey<EntityType<?>> CARTS = TagKey.create(Registries.ENTITY_TYPE, id("carts"));

	public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<TrackProgress>> TRACK_PROGRESS_SERIALIZER = ENTITY_DATA_SERIALIZERS.register(
			"track_progress",
			() -> TrackProgress.DATA_HANDLER
	);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SPLINECART_GROUP = CREATIVE_TABS.register(
			"splinecart",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.splinecart"))
					.displayItems((params, output) -> {
						output.accept(TRACK_TIES_ITEM.get().getDefaultInstance());
						output.accept(TRACK.get().getDefaultInstance());
						output.accept(CHAIN_DRIVE_TRACK.get().getDefaultInstance());
						output.accept(MAGNETIC_TRACK.get().getDefaultInstance());
					})
					.icon(() -> CHAIN_DRIVE_TRACK.get().getDefaultInstance())
					.build()
	);

	public Splinecart(IEventBus modBus) {
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
		BLOCK_ENTITY_TYPES.register(modBus);
		ENTITY_TYPES.register(modBus);
		DATA_COMPONENTS.register(modBus);
		CREATIVE_TABS.register(modBus);
		ENTITY_DATA_SERIALIZERS.register(modBus);
		modBus.addListener(Splinecart::addCreative);
		NeoForge.EVENT_BUS.register(GameEvents.class);
		LOGGER.info("Registered Splinecart");
	}

	private static void addCreative(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
			event.accept(TRACK_TIES_ITEM.get());
			event.accept(TRACK.get());
			event.accept(CHAIN_DRIVE_TRACK.get());
			event.accept(MAGNETIC_TRACK.get());
		}
	}

	public static ItemLore lore(Component lore) {
		return new ItemLore(List.of(lore));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static final class GameEvents {
		private GameEvents() {
		}

		@SubscribeEvent
		public static void onRegisterCommands(RegisterCommandsEvent event) {
			SplinecartCommands.register(event.getDispatcher());
		}
	}
}
