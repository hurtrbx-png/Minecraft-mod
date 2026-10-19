package io.github.hurtrbxpng.giantess.entity;

import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.entity.ai.GiantessMoveControl;
import io.github.hurtrbxpng.giantess.entity.ai.GiantessNavigation;
import io.github.hurtrbxpng.giantess.entity.ai.GiantessWanderGoal;
import io.github.hurtrbxpng.giantess.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The giant. Her size comes from the vanilla scale attribute, so the hitbox, eye height and
 * rendering all follow {@link GiantessConfig#size}. The overrides below stop a body this big
 * from being hurt by the terrain it walks through.
 */
public class GiantessEntity extends PathfinderMob {
	/** Vanilla caps the step height attribute at 10 blocks; the same cap applies here. */
	private static final float MAX_STEP_HEIGHT = 10.0F;

	/** Entity events carrying a footfall to the clients: the foot that came down is the left or the right one. */
	public static final byte EVENT_STEP_LEFT = 100;
	public static final byte EVENT_STEP_RIGHT = 101;

	/** Client-side hook for footstep effects; the client sets it, a dedicated server never does. */
	@FunctionalInterface
	public interface FootstepListener {
		void onFootstep(GiantessEntity giantess, boolean rightFoot);
	}

	private static FootstepListener footstepListener = (giantess, rightFoot) -> {
	};

	public static void setFootstepListener(FootstepListener listener) {
		footstepListener = listener;
	}

	private GiantessWanderGoal wanderGoal;
	private double strideProgress;
	private double lastStrideX = Double.NaN;
	private double lastStrideZ;
	private boolean nextFootRight;
	private int footstepCount;

	public GiantessEntity(EntityType<? extends GiantessEntity> type, Level level) {
		super(type, level);
		moveControl = new GiantessMoveControl(this);
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

	@Override
	protected void registerGoals() {
		wanderGoal = new GiantessWanderGoal(this);
		goalSelector.addGoal(1, wanderGoal);
		goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, GiantessConfig.get().movement.lookAtPlayerRange));
		goalSelector.addGoal(3, new RandomLookAroundGoal(this));
	}

	public GiantessWanderGoal wanderGoal() {
		return wanderGoal;
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new GiantessNavigation(this, level);
	}

	/** Ledges scale with her: a normal mob steps 0.6 blocks, she steps that times her scale. */
	@Override
	public float maxUpStep() {
		return Math.min(MAX_STEP_HEIGHT, (float) GiantessConfig.get().movement.stepHeightPerScale * getScale());
	}

	/** She wades along the bottom at walking pace instead of swimming or drifting with currents. */
	@Override
	public boolean isAffectedByFluids() {
		return false;
	}

	/**
	 * Strides follow her size: a body s times larger swings its legs like a normal one walking 1/sqrt(s)
	 * as fast, at 1/sqrt(s) the cadence (equal Froude number), so her steps cover ground without skating.
	 */
	@Override
	protected void updateWalkAnimation(float distance) {
		float sqrtScale = Mth.sqrt(Math.max(getScale(), 0.01F));
		walkAnimation.update(Math.min(distance * 4.0F / sqrtScale, 1.0F), 0.4F, 1.0F / sqrtScale);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && GiantessConfig.get().footsteps.enabled) {
			trackFootsteps();
		}
	}

	/** One footfall per stride of ground covered on foot, alternating feet; the clients do the rest. */
	private void trackFootsteps() {
		double moved = Double.isNaN(lastStrideX) ? 0.0 : Math.hypot(getX() - lastStrideX, getZ() - lastStrideZ);
		lastStrideX = getX();
		lastStrideZ = getZ();
		if (!onGround()) {
			return;
		}
		strideProgress += moved;
		double stride = Math.max(0.5, GiantessConfig.get().footsteps.strideLengthPerScale * getScale());
		if (strideProgress >= stride) {
			strideProgress -= stride;
			footstepCount++;
			level().broadcastEntityEvent(this, nextFootRight ? EVENT_STEP_RIGHT : EVENT_STEP_LEFT);
			nextFootRight = !nextFootRight;
		}
	}

	/** Footfalls announced since she spawned (server side); used by tests. */
	public int footstepCount() {
		return footstepCount;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_STEP_LEFT || id == EVENT_STEP_RIGHT) {
			footstepListener.onFootstep(this, id == EVENT_STEP_RIGHT);
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** The vanilla footstep is a mob-sized tap; the footfall event replaces it. */
	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}

	/**
	 * Footsteps never break anything. Vanilla stepping carefully (sneaking) spares turtle eggs and stops magma
	 * and redstone ore reacting to her; and she never lands on anything, so a drop cannot trample farmland.
	 */
	@Override
	public boolean isSteppingCarefully() {
		return true;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
		super.checkFallDamage(ya, onGround, Blocks.AIR.defaultBlockState(), pos);
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
