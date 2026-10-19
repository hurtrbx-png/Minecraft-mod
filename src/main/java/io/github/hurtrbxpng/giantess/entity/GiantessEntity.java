package io.github.hurtrbxpng.giantess.entity;

import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.registry.ModTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The giant. Her size comes from the vanilla scale attribute, so the hitbox, eye height and
 * rendering all follow {@link GiantessConfig#size}. The overrides below stop a body this big
 * from being hurt by the terrain it walks through.
 */
public class GiantessEntity extends PathfinderMob {
	public GiantessEntity(EntityType<? extends GiantessEntity> type, Level level) {
		super(type, level);
		// The scale comes from the default attribute supplier, which does not trigger a dimension refresh by itself.
		refreshDimensions();
	}

	public static AttributeSupplier.Builder createAttributes() {
		GiantessConfig config = GiantessConfig.get();
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, config.maxHealth)
			.add(Attributes.MOVEMENT_SPEED, config.movementSpeed)
			.add(Attributes.SCALE, config.size.scale())
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	/** Her eyes sit far above the ground, so hills and tree canopies must not suffocate her. */
	@Override
	public boolean isInWall() {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	/** Other entities inside her huge hitbox are neither shoved by her nor cramming her. */
	@Override
	protected void pushEntities() {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(ModTags.GIANTESS_IGNORES) || super.isInvulnerableTo(level, source);
	}

	/** A landmark: she stays loaded-world persistent instead of despawning when players are far away. */
	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}
}
