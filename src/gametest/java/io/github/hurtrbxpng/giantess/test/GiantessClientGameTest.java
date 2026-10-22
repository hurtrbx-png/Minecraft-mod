package io.github.hurtrbxpng.giantess.test;

import io.github.hurtrbxpng.giantess.client.effects.CameraShake;
import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import io.github.hurtrbxpng.giantess.entity.ai.Footing;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class GiantessClientGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("giantess-test");
	/** Fixed seed so the soak walks the same varied terrain every run. */
	private static final String SOAK_SEED = "giantess";
	private static final int SOAK_TICKS = 6000;
	private static final int SAMPLE_TICKS = 20;
	/** She pauses up to idleMaxTicks between strolls; standing still much longer than that means she is stuck. */
	private static final int MAX_STILL_TICKS = 1200;
	private static final double MIN_TRAVEL = 60.0;
	private static final int RIDE_TICKS = 1200;
	private static final double MIN_RIDE_TRAVEL = 8.0;
	private static final int[][] SOAK_OFFSETS = {{0, 0}, {48, 32}, {-40, -48}};

	@Override
	public void runTest(ClientGameTestContext context) {
		checkSummonAndLooks(context);
		checkFootstepShake(context);
		checkRiding(context);
		soakInGeneratedTerrain(context);
	}

	private static void checkSummonAndLooks(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();

			// Superflat surface is y=-60; the player looks south (+z) at a giantess at the origin who faces north.
			server.runCommand("tp @p 0 -60 -30 0 0");
			server.runCommand("summon giantess:giantess 0 -60 0 {Rotation:[180.0f,0.0f]}");
			context.waitTicks(60);
			context.takeScreenshot("m2-giantess-wide");

			server.runCommand("tp @p 0 -60 -12 0 -35");
			context.waitTicks(10);
			context.takeScreenshot("m2-giantess-close");

			server.runCommand("item replace entity @p weapon.mainhand with giantess:giantess_spawn_egg");
			context.waitTicks(10);
			context.takeScreenshot("m2-egg-in-hand");

			List<Float> healthFractions = server.computeOnServer(minecraftServer ->
				minecraftServer.overworld().getEntities(EntityTypeTest.forClass(GiantessEntity.class), giantess -> true)
					.stream().map(giantess -> giantess.getHealth() / giantess.getMaxHealth()).toList());
			if (healthFractions.size() != 1 || healthFractions.getFirst() != 1.0F) {
				throw new AssertionError("expected exactly one unharmed giantess after /summon, got " + healthFractions);
			}
		}
	}

	/** A giantess striding past the player shakes the camera; one walking at the edge of view distance does not. */
	private static void checkFootstepShake(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode spectator @p");
			server.runCommand("tp @p 0 -57 -14 facing 0 -60 0");

			// Near: she walks east along z=0, 14 blocks from the player.
			float near = walkAndMeasureShake(context, server, -20, 20, "m4-footstep");
			// Far: the same walk 100 blocks away, well outside the shake range of a medium giantess.
			float far = walkAndMeasureShake(context, server, 100, 140, null);
			LOGGER.info("Footstep shake: peak trauma near {} far {}", near, far);
			if (near < 0.05F) {
				throw new AssertionError("a giantess walking past the player should shake the camera, peak trauma was " + near);
			}
			if (far != 0.0F) {
				throw new AssertionError("a giantess 100 blocks away should not shake the camera, peak trauma was " + far);
			}
		}
	}

	private static float walkAndMeasureShake(ClientGameTestContext context, TestServerContext server, int fromX, int toX, String screenshot) {
		server.runCommand("kill @e[type=giantess:giantess]");
		server.runCommand("forceload add %d -16 %d 16".formatted(Math.min(fromX, toX) - 16, Math.max(fromX, toX) + 16));
		context.waitTicks(40);
		server.runCommand("summon giantess:giantess %d -60 0 {Rotation:[-90.0f,0.0f]}".formatted(fromX));
		context.waitTicks(20);
		server.computeOnServer(minecraftServer -> {
			for (GiantessEntity giantess : soakGiantesses(minecraftServer.overworld())) {
				giantess.getMoveControl().setWantedPosition(toX, -60, 0, 1.0);
			}
			return null;
		});
		CameraShake.reset();
		float peak = 0.0F;
		boolean shot = screenshot == null;
		for (int tick = 0; tick < 200; tick++) {
			context.waitTicks(1);
			float trauma = CameraShake.trauma();
			peak = Math.max(peak, trauma);
			if (!shot && trauma > 0.1F) {
				context.takeScreenshot(screenshot);
				shot = true;
			}
		}
		int steps = server.computeOnServer(minecraftServer -> soakGiantesses(minecraftServer.overworld()).stream()
			.mapToInt(GiantessEntity::footstepCount).sum());
		LOGGER.info("Walk {} -> {}: {} footfalls, peak trauma {}", fromX, toX, steps, peak);
		if (steps == 0) {
			throw new AssertionError("the giantess never took a step, so the shake check would prove nothing");
		}
		return peak;
	}

	/** M5 done-criterion in singleplayer: board her, ride a full minute while she wanders, sneak off unhurt. */
	private static void checkRiding(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("forceload add -64 -64 64 64");
			server.runCommand("execute at @p run summon giantess:giantess ~ ~ ~6");
			context.waitTicks(40);
			boolean boarded = server.computeOnServer(minecraftServer -> {
				ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
				GiantessEntity giantess = soakGiantesses(minecraftServer.overworld()).get(0);
				giantess.interact(player, InteractionHand.MAIN_HAND, giantess.position());
				return player.getVehicle() == giantess;
			});
			if (!boarded) {
				throw new AssertionError("right-clicking her with an empty hand should put the player on her shoulder");
			}

			Vec3 start = soakPositions(server).get(0);
			double travelled = 0.0;
			Vec3 last = start;
			for (int tick = 0; tick < RIDE_TICKS; tick += 20) {
				context.waitTicks(20);
				boolean riding = context.computeOnClient(client -> client.player.getVehicle() instanceof GiantessEntity);
				if (!riding) {
					throw new AssertionError("the player fell off after " + tick + " ticks");
				}
				Vec3 now = soakPositions(server).get(0);
				travelled += now.distanceTo(last);
				last = now;
				if (tick == 200) {
					context.takeScreenshot("m5-riding");
				}
			}
			LOGGER.info("Rode for {} ticks while she walked {} blocks", RIDE_TICKS, travelled);
			if (travelled < MIN_RIDE_TRAVEL) {
				throw new AssertionError("she should have walked with the rider aboard, moved only " + travelled);
			}

			context.getInput().holdKeyFor(options -> options.keyShift, 5);
			context.waitTicks(40);
			String state = context.computeOnClient(client -> {
				LocalPlayer player = client.player;
				Vec3 her = clientGiantesses(client).get(0).position();
				return player.isPassenger() + " " + player.onGround() + " " + player.getHealth() + "/" + player.getMaxHealth()
					+ " dy=" + (player.getY() - her.y);
			});
			LOGGER.info("After sneaking off: passenger, onGround, health, dy = {}", state);
			context.takeScreenshot("m5-dismounted");
			boolean safe = context.computeOnClient(client -> {
				LocalPlayer player = client.player;
				double dy = Math.abs(player.getY() - clientGiantesses(client).get(0).getY());
				return !player.isPassenger() && player.onGround() && player.getHealth() == player.getMaxHealth() && dy < 2.0;
			});
			if (!safe) {
				throw new AssertionError("sneaking should set the player down unhurt beside her: " + state);
			}
		}
	}

	private static List<GiantessEntity> clientGiantesses(Minecraft client) {
		List<GiantessEntity> found = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof GiantessEntity giantess) {
				found.add(giantess);
			}
		}
		return found;
	}

	/**
	 * Lets three giantesses wander a generated world for five minutes and fails if any of them stands still
	 * far longer than her longest pause, or barely moves at all.
	 */
	private static void soakInGeneratedTerrain(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.setUseConsistentSettings(false)
			.adjustSettings(settings -> settings.setSeed(SOAK_SEED))
			.create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			BlockPos center = server.computeOnServer(minecraftServer -> minecraftServer.overworld().getRespawnData().globalPos().pos());
			int cx = center.getX();
			int cz = center.getZ();
			server.runCommand("time set day");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @p");
			server.runCommand("forceload add %d %d %d %d".formatted(cx - 96, cz - 96, cx + 96, cz + 96));
			for (int[] offset : SOAK_OFFSETS) {
				int x = cx + offset[0];
				int z = cz + offset[1];
				int y = server.computeOnServer(minecraftServer -> minecraftServer.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
				server.runCommand("summon giantess:giantess %d %d %d".formatted(x, y, z));
			}
			server.runCommand("tp @p %d %d %d".formatted(cx, center.getY() + 60, cz - 60));

			int count = SOAK_OFFSETS.length;
			List<Vec3> anchors = new ArrayList<>();
			int[] anchorTick = new int[count];
			int[] longestStill = new int[count];
			double[] travelled = new double[count];
			List<Vec3> previous = soakPositions(server);
			anchors.addAll(previous);
			if (previous.size() != count) {
				throw new AssertionError("expected " + count + " giantesses in the soak world, got " + previous.size());
			}

			for (int tick = SAMPLE_TICKS; tick <= SOAK_TICKS; tick += SAMPLE_TICKS) {
				context.waitTicks(SAMPLE_TICKS);
				List<Vec3> now = soakPositions(server);
				if (now.size() != count) {
					throw new AssertionError("a giantess disappeared during the soak at tick " + tick + ": " + now.size() + " left");
				}
				for (int i = 0; i < count; i++) {
					travelled[i] += now.get(i).distanceTo(previous.get(i));
					if (now.get(i).distanceTo(anchors.get(i)) > 2.0) {
						anchors.set(i, now.get(i));
						anchorTick[i] = tick;
					}
					longestStill[i] = Math.max(longestStill[i], tick - anchorTick[i]);
				}
				previous = now;
				if (tick == SOAK_TICKS / 2 || tick == SOAK_TICKS) {
					Vec3 first = now.getFirst();
					server.runCommand("tp @p %.1f %.1f %.1f facing %.1f %.1f %.1f".formatted(
						first.x, first.y + 30, first.z - 50, first.x, first.y + 10, first.z));
					context.waitTicks(10);
					context.takeScreenshot("m3-soak-" + (tick == SOAK_TICKS ? "end" : "middle"));
				}
			}

			List<Integer> abandoned = server.computeOnServer(minecraftServer -> soakGiantesses(minecraftServer.overworld()).stream()
				.map(giantess -> giantess.wanderGoal().abandonedStrolls()).toList());
			double msPerTick = server.computeOnServer(minecraftServer -> minecraftServer.getAverageTickTimeNanos() / 1.0E6);
			for (int i = 0; i < count; i++) {
				LOGGER.info("Soak giantess {}: travelled {} blocks, longest still {} ticks, abandoned strolls {}, ends at {}",
					i, Math.round(travelled[i]), longestStill[i], abandoned.get(i), previous.get(i));
			}
			LOGGER.info("Soak server average tick time: {} ms", "%.1f".formatted(msPerTick));
			for (int i = 0; i < count; i++) {
				if (longestStill[i] > MAX_STILL_TICKS || travelled[i] < MIN_TRAVEL) {
					int index = i;
					LOGGER.info("Stuck giantess {}: {}", i, server.computeOnServer(minecraftServer -> describeSurroundings(soakGiantesses(minecraftServer.overworld()).get(index))));
					throw new AssertionError("giantess " + i + " got stuck: still for " + longestStill[i] + " ticks, travelled " + travelled[i]);
				}
			}
		}
	}

	private static List<GiantessEntity> soakGiantesses(ServerLevel level) {
		List<GiantessEntity> giantesses = new ArrayList<>(level.getEntities(EntityTypeTest.forClass(GiantessEntity.class), giantess -> true));
		giantesses.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
		return giantesses;
	}

	private static List<Vec3> soakPositions(TestServerContext server) {
		return server.computeOnServer(minecraftServer -> soakGiantesses(minecraftServer.overworld()).stream().map(GiantessEntity::position).toList());
	}

	/** Where she stands, how much water is over her feet, and how many of 64 directions the stroll check would accept. */
	private static String describeSurroundings(GiantessEntity giantess) {
		ServerLevel level = (ServerLevel) giantess.level();
		BlockPos feet = giantess.blockPosition();
		int water = 0;
		while (water < 64 && level.getFluidState(feet.above(water)).is(FluidTags.WATER)) {
			water++;
		}
		Footing footing = new Footing(giantess);
		double maxWater = GiantessConfig.get().movement.maxWadeDepth * giantess.getBbHeight();
		int accepted = 0;
		for (int k = 0; k < 64; k++) {
			double angle = k * Math.PI / 32.0;
			Vec3 candidate = giantess.position().add(Math.cos(angle) * 24.0, 0.0, Math.sin(angle) * 24.0);
			if (footing.checkStraightWalk(candidate, giantess.maxUpStep(), maxWater) != null) {
				accepted++;
			}
		}
		return "at %s on %s, water over feet %d (limit %.1f), %d of 64 directions accepted, onGround %s"
			.formatted(giantess.position(), level.getBlockState(feet.below()), water, maxWater, accepted, giantess.onGround());
	}
}
