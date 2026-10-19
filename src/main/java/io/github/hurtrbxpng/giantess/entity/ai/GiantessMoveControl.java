package io.github.hurtrbxpng.giantess.entity.ai;

import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Walks her toward the wanted position at a heavy, limited turn rate. She slows down while turning and
 * turns on the spot when the target is behind her, so she never circles it. She never jumps: ledges up
 * to her step height are climbed by the regular step-up collision.
 */
public class GiantessMoveControl extends MoveControl<GiantessEntity> {
	public GiantessMoveControl(GiantessEntity giantess) {
		super(giantess);
	}

	@Override
	public void tick() {
		if (operation != Operation.MOVE_TO) {
			operation = Operation.WAIT;
			mob.setZza(0.0F);
			return;
		}
		// Like vanilla, the wanted position must be set again every tick to keep walking.
		operation = Operation.WAIT;
		double dx = wantedX - mob.getX();
		double dz = wantedZ - mob.getZ();
		if (dx * dx + dz * dz < MIN_SPEED_SQR) {
			mob.setZza(0.0F);
			return;
		}

		float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		mob.setYRot(rotlerp(mob.getYRot(), targetYaw, GiantessConfig.get().movement.turnSpeedDegrees));
		float offAngle = Math.abs(Mth.wrapDegrees(targetYaw - mob.getYRot()));
		float forward = offAngle >= 90.0F ? 0.0F : Mth.cos(offAngle * Mth.DEG_TO_RAD);
		mob.setSpeed((float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)) * forward);
	}
}
