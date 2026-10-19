package io.github.hurtrbxpng.giantess.test;

import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
	private static final int[][] SOAK_OFFSETS = {{0, 0}, {48, 32}, {-40, -48}};

	@Override
	public void runTest(ClientGameTestContext context) {
		checkSummonAndLooks(context);
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
}
