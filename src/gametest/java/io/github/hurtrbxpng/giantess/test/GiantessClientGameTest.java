package io.github.hurtrbxpng.giantess.test;

import io.github.hurtrbxpng.giantess.client.effects.CameraShake;
import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.disposition.Disposition;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import io.github.hurtrbxpng.giantess.entity.ai.Footing;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

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
	private static final int TRUST_ADDED = 30;
	private static final int MOOD_TICKS = 200;
	/** How far from her the hostile check puts the player: inside her warning distance at the default size. */
	private static final double HOSTILE_START_DISTANCE = 8.0;
	private static final int RIDE_TOGETHER_TICKS = 600;
	private static final int[][] SOAK_OFFSETS = {{0, 0}, {48, 32}, {-40, -48}};

	@Override
	public void runTest(ClientGameTestContext context) {
		checkSummonAndLooks(context);
		checkFootstepShake(context);
		checkRiding(context);
		checkRidingTogether(context);
		checkTrustSurvivesReload(context);
		checkMoods(context);
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

	/**
	 * M5 done-criterion in singleplayer: board her, ride a full minute while she wanders, sneak off unhurt.
	 * Then quit while riding: on reload the player stands beside her, and she is still in the world.
	 */
	private static void checkRiding(ClientGameTestContext context) {
		TestSingleplayerContext singleplayer = context.worldBuilder().create();
		TestWorldSave save = singleplayer.getWorldSave();
		try {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("forceload add -64 -64 64 64");
			server.runCommand("execute at @p run summon giantess:giantess ~ ~ ~6");
			context.waitTicks(40);
			if (!board(server, player -> true)) {
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
			context.takeScreenshot("m5-dismounted");
			assertStandingBesideHer(context, "sneaking off");

			// Boarding has a 3-second cooldown after getting down.
			context.waitTicks(70);
			if (!board(server, player -> true)) {
				throw new AssertionError("the player should be able to board again");
			}
			context.waitTicks(20);
		} finally {
			singleplayer.close();
		}
		try (TestSingleplayerContext reopened = save.open()) {
			reopened.getConnection().waitForChunksRender();
			context.waitTicks(40);
			assertStandingBesideHer(context, "reloading a world quit while riding");
		}
	}

	/**
	 * M6 done-criterion: trust changes through play (a gift, the debug command, a hit), reaches the player's own
	 * client, and is still there after quitting and reloading the world.
	 */
	private static void checkTrustSurvivesReload(ClientGameTestContext context) {
		GiantessConfig.Disposition config = GiantessConfig.get().disposition;
		int expected = config.giftTrust + TRUST_ADDED - config.attackTrust;
		TestSingleplayerContext singleplayer = context.worldBuilder().create();
		TestWorldSave save = singleplayer.getWorldSave();
		try {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("execute at @p run summon giantess:giantess ~ ~ ~6");
			server.runCommand("item replace entity @p weapon.mainhand with minecraft:poppy");
			context.waitTicks(20);
			server.runOnServer(minecraftServer -> {
				ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
				GiantessEntity giantess = soakGiantesses(minecraftServer.overworld()).get(0);
				giantess.interact(player, InteractionHand.MAIN_HAND, giantess.position());
			});
			server.runCommand("giantess trust @p add " + TRUST_ADDED);
			server.runOnServer(minecraftServer -> {
				ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
				GiantessEntity giantess = soakGiantesses(minecraftServer.overworld()).get(0);
				giantess.hurtServer(minecraftServer.overworld(), minecraftServer.overworld().damageSources().playerAttack(player), 1.0F);
			});
			context.waitTicks(20);
			assertTrust(context, server, expected, "after a gift, the command and a hit");
		} finally {
			singleplayer.close();
		}
		try (TestSingleplayerContext reopened = save.open()) {
			reopened.getConnection().waitForChunksRender();
			context.waitTicks(20);
			assertTrust(context, reopened.getServer(), expected, "after reloading the world");
		}
	}

	/**
	 * M6b in a real world: she walks over to a friend standing far off, then backs away from the same player once
	 * hostile and warns them. This client sees each mood. Screenshots of both.
	 */
	private static void checkMoods(ClientGameTestContext context) {
		GiantessConfig.Disposition config = GiantessConfig.get().disposition;
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @p");
			server.runCommand("time set day");
			server.runCommand("forceload add -64 -64 64 64");
			server.runCommand("execute at @p run summon giantess:giantess ~ ~ ~36");
			server.runCommand("giantess trust @p set " + config.friendlyAt);
			context.waitTicks(MOOD_TICKS);
			double scale = server.computeOnServer(minecraftServer -> soakGiantesses(minecraftServer.overworld()).get(0).getScale());
			double friendDistance = distanceToHer(server);
			LOGGER.info("Friendly: she is {} blocks from the player, client sees {}", friendDistance, clientMood(context));
			if (clientMood(context) != Disposition.Mood.FRIENDLY || friendDistance > config.followStartPerScale * scale) {
				throw new AssertionError("a friend 36 blocks off should see a friendly giantess walk over, she is " + friendDistance + " away");
			}
			server.runCommand("tp @p ~ ~ ~ facing entity @e[type=giantess:giantess,limit=1] eyes");
			context.waitTicks(10);
			context.takeScreenshot("m6-friendly");

			server.runCommand("giantess trust @p set " + config.hostileAt);
			Vec3 her = soakPositions(server).get(0);
			server.runCommand("tp @p %.2f %.2f %.2f".formatted(her.x, her.y, her.z - HOSTILE_START_DISTANCE));
			server.runCommand("tp @p ~ ~ ~ facing entity @e[type=giantess:giantess,limit=1] eyes");
			context.waitTicks(20);
			context.takeScreenshot("m6-hostile-warning");
			int warnings = server.computeOnServer(minecraftServer -> soakGiantesses(minecraftServer.overworld()).get(0).keepAwayGoal().warnings());
			context.waitTicks(MOOD_TICKS - 20);
			double enemyDistance = distanceToHer(server);
			LOGGER.info("Hostile: warnings {}, she is {} blocks from the player, client sees {}", warnings, enemyDistance, clientMood(context));
			if (clientMood(context) != Disposition.Mood.HOSTILE || warnings < 1 || enemyDistance < config.keepDistancePerScale * scale) {
				throw new AssertionError("a hostile giantess should warn a player who comes close and back off to "
					+ config.keepDistancePerScale * scale + ": warnings " + warnings + ", distance " + enemyDistance);
			}
		}
	}

	private static double distanceToHer(TestServerContext server) {
		return server.computeOnServer(minecraftServer -> {
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
			GiantessEntity giantess = soakGiantesses(minecraftServer.overworld()).get(0);
			return Math.hypot(player.getX() - giantess.getX(), player.getZ() - giantess.getZ());
		});
	}

	private static Disposition.Mood clientMood(ClientGameTestContext context) {
		return context.computeOnClient(client -> clientGiantesses(client).get(0).mood());
	}

	private static void assertTrust(ClientGameTestContext context, TestServerContext server, int expected, String when) {
		int onServer = server.computeOnServer(minecraftServer -> Disposition.trust(minecraftServer.getPlayerList().getPlayers().get(0)));
		int onClient = context.computeOnClient(client -> Disposition.trust(client.player));
		LOGGER.info("Trust {}: server {}, client {}", when, onServer, onClient);
		if (onServer != expected || onClient != expected) {
			throw new AssertionError("trust " + when + " should be " + expected + ", server has " + onServer + ", client has " + onClient);
		}
	}

	/**
	 * Two players ride her together on a dedicated server: this client and a server-side stand-in with its own
	 * connection. Checks the seats as this client sees them, stable seats when one gets down, and that a rider
	 * logging out leaves her in the world and comes back on the ground beside her.
	 */
	private static void checkRidingTogether(ClientGameTestContext context) {
		Properties properties = new Properties();
		properties.setProperty("level-type", "minecraft:flat");
		// The test server allows one player by default; the stand-in takes a slot.
		properties.setProperty("max-players", "4");
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				server.runCommand("forceload add -64 -64 64 64");
				server.runCommand("execute at @a[limit=1] run summon giantess:giantess ~ ~ ~6");
				context.waitTicks(40);
				server.runOnServer(minecraftServer -> addStandIn(minecraftServer));
				if (!board(server, player -> player.getPlainTextName().equals(STAND_IN)) || !board(server, player -> !player.getPlainTextName().equals(STAND_IN))) {
					throw new AssertionError("both players should board");
				}
				for (int tick = 0; tick < RIDE_TOGETHER_TICKS; tick += 20) {
					context.waitTicks(20);
					double[] sides = context.computeOnClient(GiantessClientGameTest::riderSidesSeenByClient);
					double seat = GiantessConfig.get().climbing.seatSidePerScale * GiantessConfig.get().size.scale();
					if (sides.length != 2 || Math.abs(sides[0] - seat) > 0.5 || Math.abs(sides[1] + seat) > 0.5) {
						throw new AssertionError("expected the stand-in on her left (+" + seat + ") and this player on her right, got "
							+ Arrays.toString(sides) + " after " + tick + " ticks");
					}
					if (tick == 200) {
						context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
						context.waitTicks(2);
						context.takeScreenshot("m5-two-riders");
						context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
					}
				}

				server.runOnServer(minecraftServer -> standIn(minecraftServer).stopRiding());
				context.waitTicks(20);
				double[] sides = context.computeOnClient(GiantessClientGameTest::riderSidesSeenByClient);
				if (sides.length != 1 || sides[0] > 0.0) {
					throw new AssertionError("this player should keep the right shoulder when the other gets down, got " + Arrays.toString(sides));
				}
				String standIn = server.computeOnServer(minecraftServer -> {
					ServerPlayer player = standIn(minecraftServer);
					return player.onGround() + " dy=" + (player.getY() - soakGiantesses(minecraftServer.overworld()).get(0).getY());
				});
				LOGGER.info("Stand-in after getting down: onGround {}", standIn);
			}
			// The connection is closed while this player still rides her.
			context.waitTicks(20);
			int left = server.computeOnServer(minecraftServer -> soakGiantesses(minecraftServer.overworld()).size());
			if (left != 1) {
				throw new AssertionError("she should stay in the world when a rider logs out, found " + left);
			}
			try (TestDedicatedServerConnection connection = server.connect()) {
				connection.waitForChunksRender();
				context.waitTicks(40);
				assertStandingBesideHer(context, "logging back in after leaving while riding");
			}
			server.runOnServer(minecraftServer -> minecraftServer.getPlayerList().remove(standIn(minecraftServer)));
		}
	}

	private static final String STAND_IN = "stand-in";

	/** A second player with its own (in-memory) connection, so the server treats it as a real client. */
	private static void addStandIn(MinecraftServer server) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), STAND_IN), false);
		ServerPlayer player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
	}

	private static ServerPlayer standIn(MinecraftServer server) {
		return server.getPlayerList().getPlayers().stream().filter(player -> player.getPlainTextName().equals(STAND_IN)).findFirst().orElseThrow();
	}

	/** Boards the first matching player onto the first giantess the way a right-click does; true if they are aboard. */
	private static boolean board(TestServerContext server, Predicate<ServerPlayer> which) {
		return server.computeOnServer(minecraftServer -> {
			ServerPlayer player = minecraftServer.getPlayerList().getPlayers().stream().filter(which).findFirst().orElseThrow();
			GiantessEntity giantess = soakGiantesses(minecraftServer.overworld()).get(0);
			giantess.interact(player, InteractionHand.MAIN_HAND, giantess.position());
			return player.getVehicle() == giantess;
		});
	}

	/** Each rider's offset to her left as this client renders it; the other player first, this player last. */
	private static double[] riderSidesSeenByClient(Minecraft client) {
		GiantessEntity giantess = clientGiantesses(client).get(0);
		float left = (giantess.yBodyRot - 90.0F) * Mth.DEG_TO_RAD;
		return giantess.getPassengers().stream()
			.sorted(Comparator.comparing(rider -> rider == client.player))
			.mapToDouble(rider -> (rider.getX() - giantess.getX()) * -Mth.sin(left) + (rider.getZ() - giantess.getZ()) * Mth.cos(left))
			.toArray();
	}

	private static void assertStandingBesideHer(ClientGameTestContext context, String after) {
		String state = context.computeOnClient(client -> {
			LocalPlayer player = client.player;
			List<GiantessEntity> giantesses = clientGiantesses(client);
			return "passenger=" + player.isPassenger() + " onGround=" + player.onGround() + " health=" + player.getHealth() + "/" + player.getMaxHealth()
				+ " giantesses=" + giantesses.size() + (giantesses.isEmpty() ? "" : " dy=" + (player.getY() - giantesses.get(0).getY()));
		});
		LOGGER.info("After {}: {}", after, state);
		boolean safe = context.computeOnClient(client -> {
			LocalPlayer player = client.player;
			List<GiantessEntity> giantesses = clientGiantesses(client);
			return !player.isPassenger() && player.onGround() && player.getHealth() == player.getMaxHealth()
				&& giantesses.size() == 1 && Math.abs(player.getY() - giantesses.get(0).getY()) < 2.0;
		});
		if (!safe) {
			throw new AssertionError(after + " should leave the player standing unhurt beside her: " + state);
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

			List<Integer> abandoned = List.of();
			for (int tick = SAMPLE_TICKS; tick <= SOAK_TICKS; tick += SAMPLE_TICKS) {
				context.waitTicks(SAMPLE_TICKS);
				List<Vec3> now = soakPositions(server);
				if (now.size() != count) {
					throw new AssertionError("a giantess disappeared during the soak at tick " + tick + ": " + now.size() + " left");
				}
				// Read before the camera teleports away: a giantess that wandered off unloads with her chunk.
				abandoned = server.computeOnServer(minecraftServer -> soakGiantesses(minecraftServer.overworld()).stream()
					.map(giantess -> giantess.wanderGoal().abandonedStrolls()).toList());
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

			double msPerTick = server.computeOnServer(minecraftServer -> minecraftServer.getAverageTickTimeNanos() / 1.0E6);
			for (int i = 0; i < count; i++) {
				LOGGER.info("Soak giantess {}: travelled {} blocks, longest still {} ticks, abandoned strolls {}, ends at {}",
					i, Math.round(travelled[i]), longestStill[i], abandoned.get(i), previous.get(i));
			}
			LOGGER.info("Soak server average tick time: {} ms", "%.1f".formatted(msPerTick));
			for (int i = 0; i < count; i++) {
				if (longestStill[i] > MAX_STILL_TICKS || travelled[i] < MIN_TRAVEL) {
					int index = i;
					LOGGER.info("Stuck giantess {}: {}", i, server.computeOnServer(minecraftServer -> soakGiantesses(minecraftServer.overworld()).stream()
						.skip(index).findFirst().map(GiantessClientGameTest::describeSurroundings).orElse("unloaded")));
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
