package io.github.hurtrbxpng.giantess.registry;

import io.github.hurtrbxpng.giantess.Giantess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Block;

public final class ModTags {
	/** Environmental damage she shrugs off; edit data/giantess/tags/damage_type/giantess_ignores.json. */
	public static final TagKey<DamageType> GIANTESS_IGNORES = TagKey.create(Registries.DAMAGE_TYPE, Giantess.id("giantess_ignores"));
	/** Plants and trees she walks through without colliding; edit data/giantess/tags/block/walks_through.json. */
	public static final TagKey<Block> GIANTESS_WALKS_THROUGH = TagKey.create(Registries.BLOCK, Giantess.id("walks_through"));

	private ModTags() {
	}
}
