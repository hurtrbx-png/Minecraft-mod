package io.github.hurtrbxpng.giantess.entity.ai;

import io.github.hurtrbxpng.giantess.Giantess;
import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Slow strolls between pauses. Each stroll is a straight line that {@link Footing} has checked for
 * ledges, cliffs, deep water and lava. A stroll that stops making progress is abandoned and the next
 * one picks a new direction, so terrain the check missed never traps her.
 */
public class GiantessWanderGoal extends Goal {
	/** Random directions tried per attempt before she waits and tries again. */
	private static final int TARGET_ATTEMPTS = 12;

	private final GiantessEntity giantess;
	private int cooldown;
	private @Nullable Vec3 target;
	private Vec3 lastProgressPos = Vec3.ZERO;
	private int ticksSinceProgressCheck;
	private boolean stuck;
	private int abandonedStrolls;

	public GiantessWanderGoal(GiantessEntity giantess) {
		this.giantess = giantess;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	/** Strolls given up because she stopped making progress; read by the soak test. */
	public int abandonedStrolls() {
		return abandonedStrolls;
	}

	@Override
	public boolean canUse() {
		if (cooldown > 0) {
			cooldown--;
			return false;
		}
		if (giantess.isPassenger() || !giantess.onGround() && !giantess.isInWater()) {
			return false;
		}
		target = findTarget();
		if (target == null) {
			cooldown = GiantessConfig.get().movement.idleMinTicks;
			return false;
		}
		return true;
	}

	private @Nullable Vec3 findTarget() {
		GiantessConfig.Movement config = GiantessConfig.get().movement;
		RandomSource random = giantess.getRandom();
		Footing footing = new Footing(giantess);
		double maxStep = giantess.maxUpStep();
		double maxWaterDepth = config.maxWadeDepth * giantess.getBbHeight();
		for (int i = 0; i < TARGET_ATTEMPTS; i++) {
			float angle = random.nextFloat() * Mth.TWO_PI;
			double distance = Mth.nextDouble(random, config.wanderMinDistance, config.wanderMaxDistance);
			Vec3 candidate = giantess.position().add(Mth.cos(angle) * distance, 0.0, Mth.sin(angle) * distance);
			Vec3 checked = footing.checkStraightWalk(candidate, maxStep, maxWaterDepth);
			if (checked != null) {
				return checked;
			}
		}
		return null;
	}

	@Override
	public void start() {
		stuck = false;
		lastProgressPos = giantess.position();
		ticksSinceProgressCheck = 0;
	}

	@Override
	public boolean canContinueToUse() {
		if (stuck || target == null || giantess.isPassenger()) {
			return false;
		}
		double arrive = Math.max(1.0, giantess.getBbWidth() / 2.0);
		return horizontalDistanceSqr(giantess.position(), target) > arrive * arrive;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (target == null) {
			return;
		}
		GiantessConfig.Movement config = GiantessConfig.get().movement;
		giantess.getMoveControl().setWantedPosition(target.x, target.y, target.z, config.wanderSpeedModifier);

		if (++ticksSinceProgressCheck >= config.stuckCheckTicks) {
			Vec3 position = giantess.position();
			if (horizontalDistanceSqr(position, lastProgressPos) < config.stuckMinProgress * config.stuckMinProgress) {
				stuck = true;
				abandonedStrolls++;
				if (GiantessConfig.get().debugLogging) {
					Giantess.LOGGER.info("Giantess {} gave up a stroll at {} toward {}", giantess.getId(), position, target);
				}
			}
			lastProgressPos = position;
			ticksSinceProgressCheck = 0;
		}
	}

	private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	@Override
	public void stop() {
		target = null;
		giantess.getMoveControl().setWait();
		giantess.setZza(0.0F);
		GiantessConfig.Movement config = GiantessConfig.get().movement;
		cooldown = Mth.nextInt(giantess.getRandom(), config.idleMinTicks, config.idleMaxTicks);
	}
}
