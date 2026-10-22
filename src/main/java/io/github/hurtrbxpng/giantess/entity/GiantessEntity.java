package io.github.hurtrbxpng.giantess.entity;

import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.disposition.Disposition;
import io.github.hurtrbxpng.giantess.entity.ai.GiantessMoveControl;
import io.github.hurtrbxpng.giantess.entity.ai.GiantessNavigation;
import io.github.hurtrbxpng.giantess.entity.ai.FollowFriendGoal;
import io.github.hurtrbxpng.giantess.entity.ai.GiantessWanderGoal;
import io.github.hurtrbxpng.giantess.entity.ai.KeepAwayGoal;
import io.github.hurtrbxpng.giantess.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The giant. Her size comes from the vanilla scale attribute, so the hitbox, eye height and
 * rendering all follow {@link GiantessConfig#size}. The overrides below stop a body this big
 * from being hurt by the terrain it walks through.
 */
public class GiantessEntity extends PathfinderMob {
	/** Vanilla caps the step height attribute at 10 blocks; the same cap applies here. */
	private static final float MAX_STEP_HEIGHT = 10.0F;

	/** How far below her feet a dismounting rider may be set down, for a slope falling away beside her. */
	private static final int DISMOUNT_SEARCH_DEPTH = 8;

	/**
	 * Network id of the rider on her left shoulder; every other rider sits on the right. Synced so that a rider
	 * keeps their shoulder when the other one climbs down, instead of following the passenger list order.
	 */
	private static final EntityDataAccessor<Integer> DATA_LEFT_RIDER = SynchedEntityData.defineId(GiantessEntity.class, EntityDataSerializers.INT);
	private static final int NO_RIDER = -1;
	/** Her mood towards her focus player, as a {@link Disposition.Mood} ordinal; clients show it. */
	private static final EntityDataAccessor<Byte> DATA_MOOD = SynchedEntityData.defineId(GiantessEntity.class, EntityDataSerializers.BYTE);
	private static final int FOCUS_INTERVAL_TICKS = 10;

	/** Entity events carrying a footfall to the clients: the foot that came down is the left or the right one. */
	public static final byte EVENT_STEP_LEFT = 100;
	public static final byte EVENT_STEP_RIGHT = 101;
	public static final byte EVENT_GIFT_ACCEPTED = 102;
	public static final byte EVENT_WARN = 103;
	public static final byte EVENT_STOMP = 104;
	private static final int WARN_PARTICLES = 12;
	private static final float WARN_PITCH = 0.5F;
	private static final int GIFT_HEARTS = 7;

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
	private KeepAwayGoal keepAwayGoal;
	private @Nullable Player focus;
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

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LEFT_RIDER, NO_RIDER);
		builder.define(DATA_MOOD, (byte) Disposition.Mood.NEUTRAL.ordinal());
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
		keepAwayGoal = new KeepAwayGoal(this);
		wanderGoal = new GiantessWanderGoal(this);
		goalSelector.addGoal(0, keepAwayGoal);
		goalSelector.addGoal(1, new FollowFriendGoal(this));
		goalSelector.addGoal(2, wanderGoal);
		goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, GiantessConfig.get().movement.lookAtPlayerRange));
		goalSelector.addGoal(4, new RandomLookAroundGoal(this));
	}

	public GiantessWanderGoal wanderGoal() {
		return wanderGoal;
	}

	public KeepAwayGoal keepAwayGoal() {
		return keepAwayGoal;
	}

	/** Server: the nearest player in mood range who is not riding her, or null; refreshed every half second. */
	public @Nullable Player focus() {
		return focus;
	}

	/** Her mood towards her focus player (neutral without one); synced, so valid on both sides. */
	public Disposition.Mood mood() {
		Disposition.Mood[] moods = Disposition.Mood.values();
		int ordinal = entityData.get(DATA_MOOD);
		return ordinal >= 0 && ordinal < moods.length ? moods[ordinal] : Disposition.Mood.NEUTRAL;
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
		if (level().isClientSide()) {
			showMood();
			return;
		}
		if (GiantessConfig.get().footsteps.enabled) {
			trackFootsteps();
		}
		if (tickCount % FOCUS_INTERVAL_TICKS == 0) {
			updateFocus();
		}
	}

	/** Picks her focus player, publishes her mood towards them, and sets down any rider she has turned hostile to. */
	private void updateFocus() {
		double range = GiantessConfig.get().disposition.moodRangePerScale * getScale();
		focus = level().getNearestPlayer(getX(), getY(), getZ(), range,
			entity -> entity.isAlive() && !entity.isSpectator() && entity.getVehicle() != this);
		Disposition.Mood mood = focus == null ? Disposition.Mood.NEUTRAL : Disposition.mood(focus);
		entityData.set(DATA_MOOD, (byte) mood.ordinal());
		for (Entity rider : List.copyOf(getPassengers())) {
			if (rider instanceof Player player && Disposition.mood(player) == Disposition.Mood.HOSTILE) {
				player.stopRiding();
			}
		}
	}

	/** Client: now and then an angry or happy particle over her head while she is hostile or friendly. */
	private void showMood() {
		Disposition.Mood mood = mood();
		if (mood == Disposition.Mood.NEUTRAL || random.nextDouble() >= GiantessConfig.get().disposition.moodParticleChance) {
			return;
		}
		level().addParticle(mood == Disposition.Mood.HOSTILE ? ParticleTypes.ANGRY_VILLAGER : ParticleTypes.HAPPY_VILLAGER,
			getRandomX(0.5), getEyeY() + random.nextDouble() * 0.2 * getBbHeight(), getRandomZ(0.5), 0.0, 0.0, 0.0);
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
		if (id == EVENT_STEP_LEFT || id == EVENT_STEP_RIGHT || id == EVENT_STOMP) {
			footstepListener.onFootstep(this, id != EVENT_STEP_LEFT);
		} else if (id == EVENT_WARN) {
			for (int i = 0; i < WARN_PARTICLES; i++) {
				level().addParticle(ParticleTypes.ANGRY_VILLAGER, getRandomX(0.6), getEyeY() + random.nextDouble(), getRandomZ(0.6), 0.0, 0.0, 0.0);
			}
			level().playLocalSound(getX(), getEyeY(), getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL,
				(float) GiantessConfig.get().disposition.warnSoundVolume, WARN_PITCH, false);
		} else if (id == EVENT_GIFT_ACCEPTED) {
			for (int i = 0; i < GIFT_HEARTS; i++) {
				level().addParticle(ParticleTypes.HEART, getRandomX(0.5), getEyeY() + random.nextDouble(), getRandomZ(0.5),
					random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
			}
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

	/**
	 * A gift (giantess:gifts item tag) raises the giver's trust. An empty hand lifts the player onto a free
	 * shoulder unless she is hostile towards them; sneaking (vanilla) gets them down again.
	 */
	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || player.isSecondaryUseActive()) {
			return super.mobInteract(player, hand);
		}
		ItemStack held = player.getMainHandItem();
		if (held.is(ModTags.GIANTESS_GIFTS)) {
			if (player instanceof ServerPlayer serverPlayer && Disposition.tryGift(serverPlayer)) {
				held.consume(1, player);
				level().broadcastEntityEvent(this, EVENT_GIFT_ACCEPTED);
			}
			return InteractionResult.SUCCESS;
		}
		if (GiantessConfig.get().climbing.enabled && held.isEmpty() && player.getVehicle() != this && canAddPassenger(player)) {
			if (player instanceof ServerPlayer serverPlayer) {
				if (Disposition.mood(serverPlayer) == Disposition.Mood.HOSTILE) {
					serverPlayer.sendOverlayMessage(Component.translatable("giantess.climb.refused"));
				} else {
					serverPlayer.startRiding(this);
				}
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < GiantessConfig.get().climbing.seats;
	}

	/** A new rider takes the left shoulder when it is free, else the right. */
	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		int left = entityData.get(DATA_LEFT_RIDER);
		if (!level().isClientSide() && getPassengers().stream().noneMatch(rider -> rider.getId() == left)) {
			entityData.set(DATA_LEFT_RIDER, passenger.getId());
		}
	}

	public boolean isOnLeftShoulder(Entity passenger) {
		return passenger.getId() == entityData.get(DATA_LEFT_RIDER);
	}

	/** The seats follow her body as she turns. */
	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		GiantessConfig.Climbing config = GiantessConfig.get().climbing;
		double side = (isOnLeftShoulder(passenger) ? 1 : -1) * config.seatSidePerScale * scale;
		return new Vec3(side, config.seatHeightPerScale * scale, 0.0).yRot(-yBodyRot * Mth.DEG_TO_RAD);
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (GiantessConfig.get().climbing.turnRidersWithHer) {
			float turn = Mth.wrapDegrees(yBodyRot - yBodyRotO);
			passenger.setYRot(passenger.getYRot() + turn);
			passenger.setYHeadRot(passenger.getYHeadRot() + turn);
		}
	}

	/**
	 * Riders are set down on the ground beside her, on the side they sat on first, never dropped from her shoulder.
	 * Her own footprint, where she stands, is the last resort.
	 */
	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		float left = yBodyRot - 90.0F;
		double leftX = -Mth.sin(left * Mth.DEG_TO_RAD);
		double leftZ = Mth.cos(left * Mth.DEG_TO_RAD);
		boolean onLeft = (passenger.getX() - getX()) * leftX + (passenger.getZ() - getZ()) * leftZ >= 0.0;
		float first = onLeft ? left : yBodyRot + 90.0F;
		for (float turn : new float[] {0.0F, 180.0F, 90.0F, -90.0F}) {
			Vec3 escape = getCollisionHorizontalEscapeVector(getBbWidth(), passenger.getBbWidth(), first + turn);
			Vec3 spot = findDismountSpot(getX() + escape.x, getZ() + escape.z, passenger);
			if (spot != null) {
				return spot;
			}
		}
		return position();
	}

	/** Highest safe floor in the column, from her step height above her feet down to a short drop below them. */
	private @Nullable Vec3 findDismountSpot(double x, double z, LivingEntity passenger) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = Mth.floor(getY() + maxUpStep()); y >= Mth.floor(getY()) - DISMOUNT_SEARCH_DEPTH; y--) {
			pos.set(x, y, z);
			Vec3 spot = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), pos, true);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		passenger.resetFallDistance();
		if (!level().isClientSide() && isOnLeftShoulder(passenger)) {
			entityData.set(DATA_LEFT_RIDER, NO_RIDER);
		}
	}

	/** Riders climb down safely before she dies or is removed, instead of falling from her shoulders. */
	@Override
	public void die(DamageSource source) {
		ejectPassengers();
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (reason.shouldDestroy()) {
			ejectPassengers();
		}
		super.remove(reason);
	}

	/** Her health would replace the rider's food bar; a rider is not steering her, so keep the food bar. */
	@Override
	public boolean showVehicleHealth() {
		return false;
	}

	/** Server damage hook: riders are not hurt by the blocks her shoulders pass through. */
	public static boolean allowRiderDamage(LivingEntity entity, DamageSource source, float amount) {
		return !(source.is(DamageTypes.IN_WALL) && entity.getVehicle() instanceof GiantessEntity);
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

	/** Every hit from a player, or from their arrow, costs that player some of her trust. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof ServerPlayer player) {
			Disposition.changeTrust(player, -GiantessConfig.get().disposition.attackTrust);
		}
		return hurt;
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
