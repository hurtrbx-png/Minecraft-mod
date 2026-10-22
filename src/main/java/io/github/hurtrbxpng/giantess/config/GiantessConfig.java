package io.github.hurtrbxpng.giantess.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.hurtrbxpng.giantess.Giantess;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All tunable values live here and are read from {@code config/giantess.json}.
 * Every milestone adds its own keys; nothing tunable is hardcoded elsewhere.
 */
public final class GiantessConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "giantess.json";

	private static GiantessConfig instance = new GiantessConfig();

	/** Bumped when a key is renamed or removed, so old files can be migrated. */
	public int configVersion = 1;
	/** Extra diagnostic logging for development. */
	public boolean debugLogging = false;
	/** Hitbox and model size relative to a normal humanoid. */
	public Size size = new Size();
	public double maxHealth = 500.0;
	public double movementSpeed = 0.3;
	/** How she walks; see {@link Movement}. */
	public Movement movement = new Movement();
	/** Footfall events and their client-side effects; see {@link Footsteps}. */
	public Footsteps footsteps = new Footsteps();

	public Climbing climbing = new Climbing();
	/** Server: how giantesses feel about each player (trust from -maxTrust to maxTrust, starting at 0). */
	public Disposition disposition = new Disposition();
	/** How far away, in chunks, players receive her; the server view distance still caps it. */
	public int trackingRangeChunks = 16;

	public enum SizeTier {
		SMALL,
		MEDIUM,
		COLOSSAL
	}

	public static class Size {
		/** Range of the vanilla scale attribute. */
		public static final float MIN_SCALE = 0.0625F;
		public static final float MAX_SCALE = 16.0F;

		public SizeTier tier = SizeTier.MEDIUM;
		public float small = 4.0F;
		public float medium = 8.0F;
		public float colossal = 16.0F;

		public float scale() {
			return switch (tier) {
				case SMALL -> small;
				case MEDIUM -> medium;
				case COLOSSAL -> colossal;
			};
		}
	}

	public static class Movement {
		/** Tallest ledge she walks up, per unit of scale (a normal mob steps 0.6); capped at 10 blocks. */
		public double stepHeightPerScale = 0.6;
		/** Multiplier on movementSpeed while wandering. */
		public double wanderSpeedModifier = 1.0;
		/** Distance range, in blocks, of each stroll. */
		public int wanderMinDistance = 16;
		public int wanderMaxDistance = 48;
		/** Pause between strolls, in ticks (20 ticks = 1 second). */
		public int idleMinTicks = 100;
		public int idleMaxTicks = 400;
		/** How fast she turns, in degrees per tick. */
		public float turnSpeedDegrees = 4.0F;
		/** Deepest water she wades into, as a fraction of her height. */
		public double maxWadeDepth = 0.4;
		/** A stroll is abandoned when she covers less than stuckMinProgress blocks in stuckCheckTicks ticks. */
		public int stuckCheckTicks = 60;
		public double stuckMinProgress = 1.0;
		/** How far away, in blocks, she notices and looks at players. */
		public float lookAtPlayerRange = 48.0F;
	}

	public static class Footsteps {
		/** Server: a footfall event every stride while she walks. Off silences all footstep effects. */
		public boolean enabled = true;
		/** Server: blocks covered per step, per unit of scale (a person's stride is about 0.8 of their height / 2). */
		public double strideLengthPerScale = 0.8;
		/** Client: shake the camera when she steps nearby. */
		public boolean cameraShake = true;
		/** Client, accessibility: a quarter of the normal shake. */
		public boolean reduceShake = false;
		/** Client: multiplier on the shake strength. */
		public double shakeStrength = 1.0;
		/** Client: the shake fades to nothing at this many blocks per unit of her scale. */
		public double shakeRangePerScale = 6.0;
		/** Client: volume of the step sound; hearing distance is 16 blocks times this. */
		public double soundVolume = 4.0;
		/** Client: pitch of the step sound (the sound engine clamps it to 0.5..2.0). */
		public double soundPitch = 0.5;
		/** Client: block particles kicked up by each step, before the particle setting thins them. */
		public int particleCount = 24;
		/** Client: no particles when the camera is farther than this many blocks per unit of her scale. */
		public double particleRangePerScale = 8.0;
	}

	public static class Climbing {
		/** Server: right-clicking her with an empty hand lifts the player onto a shoulder; sneak to get down. */
		public boolean enabled = true;
		/** Server: riders at once, one per shoulder (1 or 2). */
		public int seats = 2;
		/** Height of the shoulder seat above her feet, per unit of scale (a humanoid's shoulder is at 1.5). */
		public double seatHeightPerScale = 1.5;
		/** Distance of the seat from her centre line, per unit of scale (the middle of the arm is at 0.375). */
		public double seatSidePerScale = 0.375;
		/** Riders' view turns along with her body, as in a boat. */
		public boolean turnRidersWithHer = true;
		/** Client: multiplier on footstep camera shake while riding her. */
		public double riderShakeMultiplier = 0.5;
	}

	public static class Disposition {
		/** Largest trust a player can reach either way. */
		public int maxTrust = 100;
		/** At or above this trust she is friendly towards the player. */
		public int friendlyAt = 30;
		/** At or below this trust she is hostile towards the player (who then cannot climb her). */
		public int hostileAt = -30;
		/** Trust gained per gift (right-click her holding an item from the giantess:gifts item tag). */
		public int giftTrust = 10;
		/** Ticks after a gift before she accepts another from the same player. */
		public int giftCooldownTicks = 100;
		/** Trust gained for killing a monster near a villager while a giantess is near enough to see it. */
		public int protectTrust = 5;
		/** A villager this many blocks from the monster counts as protected. */
		public double protectVillagerRadius = 16.0;
		/** A giantess this many blocks from the monster sees it happen. */
		public double witnessRadius = 64.0;
		/** Trust lost each time the player hurts her. */
		public int attackTrust = 15;
		/** Her mood is her mood towards the nearest player within this many blocks per unit of scale (riders aside). */
		public double moodRangePerScale = 6.0;
		/** Friendly: she walks over when that player is farther than this per unit of scale... */
		public double followStartPerScale = 3.0;
		/** ...and stops this close, per unit of scale. */
		public double followStopPerScale = 1.5;
		/** Friendly: walking speed multiplier while she comes over. */
		public double followSpeedModifier = 1.0;
		/** Hostile: she backs away from that player when they are closer than this per unit of scale... */
		public double keepDistancePerScale = 2.0;
		/** ...warns them when they come closer than this per unit of scale... */
		public double warnDistancePerScale = 1.25;
		/** ...and stomps (a shove, never breaking blocks) if they are still that close this many ticks after the warning. */
		public int warnTicks = 60;
		/** Ticks after a stomp before she warns again. */
		public int stompCooldownTicks = 100;
		/** Damage of a stomp (0 for a harmless shove). */
		public double stompDamage = 2.0;
		/** Strength of the stomp's shove (a sword's knockback is 0.4 per level). */
		public double stompKnockback = 1.5;
		/** Client: volume of her warning grumble. */
		public double warnSoundVolume = 2.0;
		/** Client: chance per tick of a mood particle above her head (angry or happy) while she is hostile or friendly. */
		public double moodParticleChance = 0.04;
	}

	public static GiantessConfig get() {
		return instance;
	}

	/**
	 * Reads the config file, falling back to defaults if it is missing or unreadable.
	 * Out-of-range values are clamped, then the file is rewritten so newly added keys
	 * appear with their defaults. A file that cannot be parsed is left untouched so the
	 * user can fix it.
	 */
	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		GiantessConfig loaded = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, GiantessConfig.class);
			} catch (IOException | JsonParseException e) {
				Giantess.LOGGER.error("Could not read {}; using defaults and leaving the file untouched", path, e);
				return;
			}
		}

		instance = loaded != null ? loaded : new GiantessConfig();
		instance.validate();
		save(path);
	}

	private void validate() {
		if (size == null) {
			size = new Size();
		}
		if (size.tier == null) {
			Giantess.LOGGER.warn("Unknown size.tier in config; using {}", SizeTier.MEDIUM);
			size.tier = SizeTier.MEDIUM;
		}
		size.small = clamp("size.small", size.small, Size.MIN_SCALE, Size.MAX_SCALE);
		size.medium = clamp("size.medium", size.medium, Size.MIN_SCALE, Size.MAX_SCALE);
		size.colossal = clamp("size.colossal", size.colossal, Size.MIN_SCALE, Size.MAX_SCALE);
		maxHealth = clamp("maxHealth", maxHealth, 1.0, 1024.0);
		movementSpeed = clamp("movementSpeed", movementSpeed, 0.01, 2.0);
		trackingRangeChunks = (int) clamp("trackingRangeChunks", trackingRangeChunks, 2, 64);
		if (movement == null) {
			movement = new Movement();
		}
		Movement m = movement;
		m.stepHeightPerScale = clamp("movement.stepHeightPerScale", m.stepHeightPerScale, 0.0, 2.0);
		m.wanderSpeedModifier = clamp("movement.wanderSpeedModifier", m.wanderSpeedModifier, 0.1, 4.0);
		m.wanderMinDistance = (int) clamp("movement.wanderMinDistance", m.wanderMinDistance, 4, 256);
		m.wanderMaxDistance = (int) clamp("movement.wanderMaxDistance", m.wanderMaxDistance, m.wanderMinDistance, 256);
		m.idleMinTicks = (int) clamp("movement.idleMinTicks", m.idleMinTicks, 0, 24000);
		m.idleMaxTicks = (int) clamp("movement.idleMaxTicks", m.idleMaxTicks, m.idleMinTicks, 24000);
		m.turnSpeedDegrees = clamp("movement.turnSpeedDegrees", m.turnSpeedDegrees, 0.5F, 90.0F);
		m.maxWadeDepth = clamp("movement.maxWadeDepth", m.maxWadeDepth, 0.0, 1.0);
		m.stuckCheckTicks = (int) clamp("movement.stuckCheckTicks", m.stuckCheckTicks, 10, 1200);
		m.stuckMinProgress = clamp("movement.stuckMinProgress", m.stuckMinProgress, 0.0, 16.0);
		m.lookAtPlayerRange = clamp("movement.lookAtPlayerRange", m.lookAtPlayerRange, 0.0F, 128.0F);
		if (footsteps == null) {
			footsteps = new Footsteps();
		}
		Footsteps f = footsteps;
		f.strideLengthPerScale = clamp("footsteps.strideLengthPerScale", f.strideLengthPerScale, 0.1, 4.0);
		f.shakeStrength = clamp("footsteps.shakeStrength", f.shakeStrength, 0.0, 4.0);
		f.shakeRangePerScale = clamp("footsteps.shakeRangePerScale", f.shakeRangePerScale, 0.0, 32.0);
		f.soundVolume = clamp("footsteps.soundVolume", f.soundVolume, 0.0, 16.0);
		f.soundPitch = clamp("footsteps.soundPitch", f.soundPitch, 0.5, 2.0);
		f.particleCount = (int) clamp("footsteps.particleCount", f.particleCount, 0, 200);
		f.particleRangePerScale = clamp("footsteps.particleRangePerScale", f.particleRangePerScale, 0.0, 32.0);
		if (climbing == null) {
			climbing = new Climbing();
		}
		Climbing c = climbing;
		c.seats = (int) clamp("climbing.seats", c.seats, 1, 2);
		c.seatHeightPerScale = clamp("climbing.seatHeightPerScale", c.seatHeightPerScale, 0.0, 2.5);
		c.seatSidePerScale = clamp("climbing.seatSidePerScale", c.seatSidePerScale, 0.0, 1.0);
		c.riderShakeMultiplier = clamp("climbing.riderShakeMultiplier", c.riderShakeMultiplier, 0.0, 1.0);
		if (disposition == null) {
			disposition = new Disposition();
		}
		Disposition d = disposition;
		d.maxTrust = (int) clamp("disposition.maxTrust", d.maxTrust, 1, 10000);
		d.friendlyAt = (int) clamp("disposition.friendlyAt", d.friendlyAt, 1, d.maxTrust);
		d.hostileAt = (int) clamp("disposition.hostileAt", d.hostileAt, -d.maxTrust, -1);
		d.giftTrust = (int) clamp("disposition.giftTrust", d.giftTrust, 0, d.maxTrust);
		d.giftCooldownTicks = (int) clamp("disposition.giftCooldownTicks", d.giftCooldownTicks, 0, 24000);
		d.protectTrust = (int) clamp("disposition.protectTrust", d.protectTrust, 0, d.maxTrust);
		d.protectVillagerRadius = clamp("disposition.protectVillagerRadius", d.protectVillagerRadius, 0.0, 64.0);
		d.witnessRadius = clamp("disposition.witnessRadius", d.witnessRadius, 0.0, 256.0);
		d.attackTrust = (int) clamp("disposition.attackTrust", d.attackTrust, 0, d.maxTrust);
		d.moodRangePerScale = clamp("disposition.moodRangePerScale", d.moodRangePerScale, 0.0, 16.0);
		d.followStartPerScale = clamp("disposition.followStartPerScale", d.followStartPerScale, 0.5, 16.0);
		d.followStopPerScale = clamp("disposition.followStopPerScale", d.followStopPerScale, 0.5, d.followStartPerScale);
		d.followSpeedModifier = clamp("disposition.followSpeedModifier", d.followSpeedModifier, 0.1, 4.0);
		d.keepDistancePerScale = clamp("disposition.keepDistancePerScale", d.keepDistancePerScale, 0.5, 16.0);
		d.warnDistancePerScale = clamp("disposition.warnDistancePerScale", d.warnDistancePerScale, 0.5, d.keepDistancePerScale);
		d.warnTicks = (int) clamp("disposition.warnTicks", d.warnTicks, 0, 1200);
		d.stompCooldownTicks = (int) clamp("disposition.stompCooldownTicks", d.stompCooldownTicks, 20, 24000);
		d.stompDamage = clamp("disposition.stompDamage", d.stompDamage, 0.0, 20.0);
		d.stompKnockback = clamp("disposition.stompKnockback", d.stompKnockback, 0.0, 4.0);
		d.warnSoundVolume = clamp("disposition.warnSoundVolume", d.warnSoundVolume, 0.0, 16.0);
		d.moodParticleChance = clamp("disposition.moodParticleChance", d.moodParticleChance, 0.0, 1.0);
	}

	private static float clamp(String key, float value, float min, float max) {
		return (float) clamp(key, (double) value, min, max);
	}

	private static double clamp(String key, double value, double min, double max) {
		double clamped = Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
		if (clamped != value) {
			Giantess.LOGGER.warn("Config value {}={} is outside [{}, {}]; using {}", key, value, min, max, clamped);
		}
		return clamped;
	}

	private static void save(Path path) {
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			Giantess.LOGGER.error("Could not write {}", path, e);
		}
	}
}
