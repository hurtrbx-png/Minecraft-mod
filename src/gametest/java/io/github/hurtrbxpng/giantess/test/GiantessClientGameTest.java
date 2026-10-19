package io.github.hurtrbxpng.giantess.test;

import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;

public class GiantessClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
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
}
