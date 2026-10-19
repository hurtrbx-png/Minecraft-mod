package io.github.hurtrbxpng.giantess.registry;

import io.github.hurtrbxpng.giantess.Giantess;
import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> GIANTESS_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Giantess.id("giantess"));

	public static EntityType<GiantessEntity> GIANTESS;

	private ModEntities() {
	}

	/** Must run after the config is loaded: the tracking range and attributes come from it. */
	public static void register() {
		GIANTESS = Registry.register(BuiltInRegistries.ENTITY_TYPE, GIANTESS_KEY,
			// Base humanoid size; the scale attribute multiplies the dimensions and eye height.
			EntityType.Builder.of(GiantessEntity::new, MobCategory.CREATURE)
				.sized(0.6F, 1.95F)
				.eyeHeight(1.74F)
				.clientTrackingRange(GiantessConfig.get().trackingRangeChunks)
				.fireImmune()
				.noLootTable()
				.build(GIANTESS_KEY));
		FabricDefaultAttributeRegistry.register(GIANTESS, GiantessEntity.createAttributes());
	}
}
