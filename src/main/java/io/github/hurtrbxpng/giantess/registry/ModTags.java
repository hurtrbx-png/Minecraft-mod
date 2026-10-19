package io.github.hurtrbxpng.giantess.registry;

import io.github.hurtrbxpng.giantess.Giantess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

public final class ModTags {
	/** Environmental damage she shrugs off; edit data/giantess/tags/damage_type/giantess_ignores.json. */
	public static final TagKey<DamageType> GIANTESS_IGNORES = TagKey.create(Registries.DAMAGE_TYPE, Giantess.id("giantess_ignores"));

	private ModTags() {
	}
}
