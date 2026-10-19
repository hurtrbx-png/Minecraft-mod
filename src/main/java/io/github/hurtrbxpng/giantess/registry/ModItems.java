package io.github.hurtrbxpng.giantess.registry;

import io.github.hurtrbxpng.giantess.Giantess;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class ModItems {
	private static final ResourceKey<Item> GIANTESS_SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM, Giantess.id("giantess_spawn_egg"));
	private static final ResourceKey<CreativeModeTab> SPAWN_EGGS_TAB =
		ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));

	public static Item GIANTESS_SPAWN_EGG;

	private ModItems() {
	}

	/** Must run after {@link ModEntities#register()}: the egg refers to the entity type. */
	public static void register() {
		GIANTESS_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, GIANTESS_SPAWN_EGG_KEY,
			new SpawnEggItem(new Item.Properties().setId(GIANTESS_SPAWN_EGG_KEY).spawnEgg(ModEntities.GIANTESS)));

		CreativeModeTabEvents.modifyOutputEvent(SPAWN_EGGS_TAB).register(output -> output.accept(GIANTESS_SPAWN_EGG));
	}
}
