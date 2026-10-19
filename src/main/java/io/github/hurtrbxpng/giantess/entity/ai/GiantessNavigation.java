package io.github.hurtrbxpng.giantess.entity.ai;

import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;

/**
 * Turns off vanilla A* for her. It tests every block of the hitbox at every node, which at her size
 * costs thousands of block reads per node; {@link GiantessWanderGoal} plans with {@link Footing} instead.
 */
public class GiantessNavigation extends GroundPathNavigation {
	public GiantessNavigation(GiantessEntity giantess, Level level) {
		super(giantess, level);
	}

	@Override
	protected boolean canUpdatePath() {
		return false;
	}
}
