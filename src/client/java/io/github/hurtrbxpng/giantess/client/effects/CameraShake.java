package io.github.hurtrbxpng.giantess.client.effects;

import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Footstep camera shake in the "trauma" style: each footfall adds trauma (0..1), which fades out
 * linearly while the visible shake grows with its square, so a near step thumps and a far one barely shows.
 */
public final class CameraShake {
	/** Trauma lost per client tick; a full-strength thump is gone after about a second. */
	private static final float DECAY_PER_TICK = 0.05F;
	/** Shape of the shake at full trauma: pitch swing in degrees, yaw swing in degrees, vertical lift in blocks. */
	private static final float MAX_PITCH = 2.2F;
	private static final float MAX_YAW = 0.8F;
	private static final float MAX_LIFT = 0.12F;
	private static final float PITCH_FREQUENCY = 1.9F;
	private static final float YAW_FREQUENCY = 1.3F;

	public record Offset(float pitchDegrees, float yawDegrees, float lift) {
	}

	private static float trauma;
	private static float age;

	private CameraShake() {
	}

	public static void addTrauma(float amount) {
		trauma = Mth.clamp(trauma + amount, 0.0F, 1.0F);
	}

	/** Strongest trauma seen since the last call; used by tests to check the shake fades with distance. */
	public static float trauma() {
		return trauma;
	}

	public static void tick() {
		age += 1.0F;
		trauma = Math.max(0.0F, trauma - DECAY_PER_TICK);
	}

	public static void reset() {
		trauma = 0.0F;
	}

	@Nullable
	public static Offset offset(float partialTicks) {
		if (trauma <= 0.0F) {
			return null;
		}
		float shake = trauma * trauma;
		float t = age + partialTicks;
		return new Offset(
			shake * MAX_PITCH * Mth.sin(t * PITCH_FREQUENCY),
			shake * MAX_YAW * Mth.sin(t * YAW_FREQUENCY + 1.0F),
			shake * MAX_LIFT * Mth.cos(t * PITCH_FREQUENCY)
		);
	}
}
