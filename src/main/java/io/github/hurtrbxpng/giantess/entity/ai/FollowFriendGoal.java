package io.github.hurtrbxpng.giantess.entity.ai;

import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.disposition.Disposition;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/** Friendly: when the player she is focused on wanders off, she walks over and stops a little way from them. */
public class FollowFriendGoal extends Goal {
	/** Ticks between fresh checks of the way to her friend, who keeps moving. */
	private static final int RETARGET_TICKS = 20;

	private final GiantessEntity giantess;
	private @Nullable Player friend;
	private @Nullable Vec3 target;
	private int retargetIn;

	public FollowFriendGoal(GiantessEntity giantess) {
		this.giantess = giantess;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		Player focus = giantess.focus();
		if (giantess.isPassenger() || !giantess.onGround() && !giantess.isInWater() || !isFriend(focus)
			|| horizontalDistance(focus) <= GiantessConfig.get().disposition.followStartPerScale * giantess.getScale()) {
			return false;
		}
		friend = focus;
		target = findTarget(focus);
		return target != null;
	}

	@Override
	public boolean canContinueToUse() {
		return target != null && friend != null && friend == giantess.focus() && isFriend(friend) && !giantess.isPassenger()
			&& horizontalDistance(friend) > GiantessConfig.get().disposition.followStopPerScale * giantess.getScale();
	}

	@Override
	public void start() {
		retargetIn = RETARGET_TICKS;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (friend == null) {
			return;
		}
		giantess.getLookControl().setLookAt(friend);
		if (--retargetIn <= 0) {
			retargetIn = RETARGET_TICKS;
			target = findTarget(friend);
		}
		if (target != null) {
			giantess.getMoveControl().setWantedPosition(target.x, target.y, target.z, GiantessConfig.get().disposition.followSpeedModifier);
		}
	}

	@Override
	public void stop() {
		friend = null;
		target = null;
		giantess.getMoveControl().setWait();
		giantess.setZza(0.0F);
	}

	/** The point on the line between them, followStop away from her friend, if she can walk straight there. */
	private @Nullable Vec3 findTarget(Player friend) {
		Vec3 toHer = giantess.position().subtract(friend.position()).multiply(1.0, 0.0, 1.0);
		double stop = GiantessConfig.get().disposition.followStopPerScale * giantess.getScale();
		Vec3 spot = toHer.lengthSqr() < 1.0E-4 ? friend.position() : friend.position().add(toHer.normalize().scale(stop));
		return Footing.checkStraightWalk(giantess, spot);
	}

	private boolean isFriend(@Nullable Player player) {
		return player != null && player.isAlive() && Disposition.mood(player) == Disposition.Mood.FRIENDLY;
	}

	private double horizontalDistance(Player player) {
		return Math.hypot(player.getX() - giantess.getX(), player.getZ() - giantess.getZ());
	}
}
