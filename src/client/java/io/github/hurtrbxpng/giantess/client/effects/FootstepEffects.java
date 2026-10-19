package io.github.hurtrbxpng.giantess.client.effects;

import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import io.github.hurtrbxpng.giantess.registry.ModTags;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/** What a footfall looks, sounds and feels like for the local player; all values come from the config. */
public final class FootstepEffects {
	/** Half the distance between her feet, per unit of scale (the humanoid legs sit 2 of 16 pixels off-centre). */
	private static final double FOOT_OFFSET_PER_SCALE = 0.125;
	/** Footfall strength reaches its maximum at this scale. */
	private static final float FULL_STRENGTH_SCALE = 12.0F;

	private FootstepEffects() {
	}

	public static void onFootstep(GiantessEntity giantess, boolean rightFoot) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity viewer = minecraft.getCameraEntity();
		if (viewer == null) {
			return;
		}
		GiantessConfig.Footsteps config = GiantessConfig.get().footsteps;
		Level level = giantess.level();
		float scale = giantess.getScale();

		double yaw = Math.toRadians(giantess.yBodyRot);
		double side = (rightFoot ? 1 : -1) * FOOT_OFFSET_PER_SCALE * scale;
		double footX = giantess.getX() - Math.cos(yaw) * side;
		double footZ = giantess.getZ() - Math.sin(yaw) * side;
		double footY = giantess.getY();
		double distance = viewer.distanceTo(giantess);

		BlockState state = footingState(level, footX, footY, footZ);

		if (config.cameraShake) {
			double range = config.shakeRangePerScale * scale;
			if (range > 0.0 && distance < range) {
				float falloff = (float) (1.0 - distance / range);
				float strength = (float) config.shakeStrength * Math.min(1.0F, scale / FULL_STRENGTH_SCALE) * falloff * falloff;
				CameraShake.addTrauma(config.reduceShake ? strength * 0.25F : strength);
			}
		}

		if (config.soundVolume > 0.0) {
			SoundType sound = state.isAir() ? SoundType.STONE : state.getSoundType();
			float volume = (float) config.soundVolume;
			float pitch = (float) config.soundPitch;
			level.playLocalSound(footX, footY, footZ, sound.getStepSound(), SoundSource.NEUTRAL, volume, pitch * sound.getPitch(), false);
			level.playLocalSound(footX, footY, footZ, SoundEvents.IRON_GOLEM_STEP, SoundSource.NEUTRAL, volume * 0.6F, pitch, false);
		}

		if (config.particleCount > 0 && !state.isAir() && distance < config.particleRangePerScale * scale) {
			kickUp(level, state, footX, footY, footZ, scale, config.particleCount);
		}
	}

	/** The block her foot lands in (leaves, undergrowth she walks through) or else the one it lands on. */
	private static BlockState footingState(Level level, double x, double y, double z) {
		BlockState inside = level.getBlockState(BlockPos.containing(x, y + 0.1, z));
		if (inside.is(ModTags.GIANTESS_WALKS_THROUGH)) {
			return inside;
		}
		return level.getBlockState(BlockPos.containing(x, y - 0.1, z));
	}

	private static void kickUp(Level level, BlockState state, double x, double y, double z, float scale, int count) {
		RandomSource random = level.getRandom();
		BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, state);
		double radius = 0.12 * scale;
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double outward = 0.05 + random.nextDouble() * 0.12;
			double dist = Math.sqrt(random.nextDouble()) * radius;
			level.addParticle(
				debris,
				x + Math.cos(angle) * dist, y + 0.1, z + Math.sin(angle) * dist,
				Math.cos(angle) * outward, 0.1 + random.nextDouble() * 0.2, Math.sin(angle) * outward
			);
		}
		for (int i = 0; i < Math.max(1, count / 6); i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			level.addParticle(ParticleTypes.POOF, x + Math.cos(angle) * radius, y + 0.2, z + Math.sin(angle) * radius, Math.cos(angle) * 0.05, 0.02, Math.sin(angle) * 0.05);
		}
	}
}
