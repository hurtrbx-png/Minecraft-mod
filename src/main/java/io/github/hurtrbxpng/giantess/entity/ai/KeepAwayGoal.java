package io.github.hurtrbxpng.giantess.entity.ai;

import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.disposition.Disposition;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Hostile: she backs away from the player she is focused on and keeps an eye on them. One who comes too close
 * is warned first; if they are still too close when the warning runs out, she stomps beside them: a shove and
 * a little damage, never breaking blocks.
 */
public class KeepAwayGoal extends Goal {
	/** Ticks between fresh retreat directions. */
	private static final int RETARGET_TICKS = 20;
	/** She keeps backing off until the player is this much past keepDistance, so she does not stop and start. */
	private static final double LEAVE_MARGIN = 1.25;
	/** Retreat directions tried, in degrees off straight away from the player. */
	private static final float[] RETREAT_ANGLES = {0.0F, 45.0F, -45.0F, 90.0F, -90.0F};

	private final GiantessEntity giantess;
	private @Nullable Player enemy;
	private @Nullable Vec3 target;
	private int retargetIn;
	private int warningLeft;
	private int cooldown;
	private int warnings;
	private int stomps;

	public KeepAwayGoal(GiantessEntity giantess) {
		this.giantess = giantess;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	/** Warnings given since she spawned; read by tests. */
	public int warnings() {
		return warnings;
	}

	/** Stomps since she spawned; read by tests. */
	public int stomps() {
		return stomps;
	}

	@Override
	public boolean canUse() {
		if (cooldown > 0) {
			cooldown--;
		}
		Player focus = giantess.focus();
		if (giantess.isPassenger() || !isEnemy(focus) || horizontalDistance(focus) >= keepDistance()) {
			return false;
		}
		enemy = focus;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return enemy != null && enemy == giantess.focus() && isEnemy(enemy) && !giantess.isPassenger()
			&& horizontalDistance(enemy) < keepDistance() * LEAVE_MARGIN;
	}

	@Override
	public void start() {
		retargetIn = 0;
		warningLeft = 0;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (enemy == null) {
			return;
		}
		GiantessConfig.Disposition config = GiantessConfig.get().disposition;
		giantess.getLookControl().setLookAt(enemy);
		double warnDistance = config.warnDistancePerScale * giantess.getScale();
		if (cooldown > 0) {
			cooldown--;
		}
		if (warningLeft > 0) {
			if (--warningLeft == 0) {
				if (horizontalDistance(enemy) < warnDistance) {
					stomp(enemy);
				}
				cooldown = config.stompCooldownTicks;
			}
		} else if (cooldown == 0 && horizontalDistance(enemy) < warnDistance) {
			warnings++;
			giantess.level().broadcastEntityEvent(giantess, GiantessEntity.EVENT_WARN);
			warningLeft = Math.max(1, config.warnTicks);
		}

		if (--retargetIn <= 0) {
			retargetIn = RETARGET_TICKS;
			target = findRetreat(enemy);
		}
		if (target != null) {
			giantess.getMoveControl().setWantedPosition(target.x, target.y, target.z, GiantessConfig.get().movement.wanderSpeedModifier);
		} else {
			giantess.getMoveControl().setWait();
		}
	}

	@Override
	public void stop() {
		enemy = null;
		target = null;
		warningLeft = 0;
		giantess.getMoveControl().setWait();
		giantess.setZza(0.0F);
	}

	private void stomp(Player enemy) {
		stomps++;
		GiantessConfig.Disposition config = GiantessConfig.get().disposition;
		ServerLevel level = (ServerLevel) giantess.level();
		level.broadcastEntityEvent(giantess, GiantessEntity.EVENT_STOMP);
		DamageSource source = level.damageSources().mobAttack(giantess);
		if (config.stompDamage > 0.0) {
			enemy.hurtServer(level, source, (float) config.stompDamage);
		}
		enemy.knockback(config.stompKnockback, giantess.getX() - enemy.getX(), giantess.getZ() - enemy.getZ(), source, 0.0F);
	}

	/** A walkable spot keepDistance farther from the player, straight away or as close to that as the ground allows. */
	private @Nullable Vec3 findRetreat(Player enemy) {
		Vec3 away = giantess.position().subtract(enemy.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			away = Vec3.directionFromRotation(0.0F, giantess.yBodyRot);
		}
		away = away.normalize().scale(keepDistance());
		for (float angle : RETREAT_ANGLES) {
			Vec3 spot = Footing.checkStraightWalk(giantess, giantess.position().add(away.yRot(angle * Mth.DEG_TO_RAD)));
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	private double keepDistance() {
		return GiantessConfig.get().disposition.keepDistancePerScale * giantess.getScale();
	}

	private boolean isEnemy(@Nullable Player player) {
		return player != null && player.isAlive() && Disposition.mood(player) == Disposition.Mood.HOSTILE;
	}

	private double horizontalDistance(Player player) {
		return Math.hypot(player.getX() - giantess.getX(), player.getZ() - giantess.getZ());
	}
}
