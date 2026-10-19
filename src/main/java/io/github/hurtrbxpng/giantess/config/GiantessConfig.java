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
