package io.github.hurtrbxpng.giantess.test;

import io.github.hurtrbxpng.giantess.Giantess;
import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import io.github.hurtrbxpng.giantess.entity.ai.Footing;
import io.github.hurtrbxpng.giantess.registry.ModEntities;
import io.github.hurtrbxpng.giantess.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class GiantessGameTest {
	@GameTest
	public void modLoadedWithConfig(GameTestHelper context) {
		context.assertTrue(FabricLoader.getInstance().isModLoaded(Giantess.MOD_ID), "giantess is not loaded");
		context.assertTrue(GiantessConfig.get() != null, "config was not loaded");
		context.assertTrue(Giantess.id("test").toString().equals("giantess:test"), "id helper builds the wrong id");
		context.succeed();
	}

	@GameTest
	public void corruptConfigKeepsDefaultsAndFile(GameTestHelper context) throws IOException {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("giantess.json");
		String original = Files.isRegularFile(path) ? Files.readString(path) : null;
		String broken = "{ this is not json";
		try {
			Files.writeString(path, broken);
			GiantessConfig.load();
			context.assertTrue(GiantessConfig.get().configVersion == 1, "defaults were not kept");
			context.assertTrue(Files.readString(path).equals(broken), "the broken file was overwritten");
		} finally {
			if (original != null) {
				Files.writeString(path, original);
			} else {
				Files.deleteIfExists(path);
			}
			GiantessConfig.load();
		}
		context.succeed();
	}

	@GameTest
	public void registeredWithScaledHitbox(GameTestHelper context) {
		GiantessConfig config = GiantessConfig.get();
		float scale = config.size.scale();
		GiantessEntity giantess = context.spawn(ModEntities.GIANTESS, 2, 2, 2);

		context.assertTrue(BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.GIANTESS).toString().equals("giantess:giantess"),
			"entity is not registered as giantess:giantess");
		context.assertTrue(SpawnEggItem.byId(ModEntities.GIANTESS).isPresent(), "there is no spawn egg for the giantess");
		context.assertTrue(SpawnEggItem.getType(new ItemStack(ModItems.GIANTESS_SPAWN_EGG)) == ModEntities.GIANTESS,
			"the spawn egg does not spawn the giantess");
		context.assertTrue(Math.abs(giantess.getBbHeight() - 1.95F * scale) < 0.01F, "hitbox height does not follow the configured scale");
		context.assertTrue(Math.abs(giantess.getBbWidth() - 0.6F * scale) < 0.01F, "hitbox width does not follow the configured scale");
		context.assertTrue(giantess.getMaxHealth() == (float) config.maxHealth, "max health does not come from the config");
		context.assertTrue(!giantess.removeWhenFarAway(1.0E9), "she would despawn when players are far away");

		RangedAttribute scaleRange = (RangedAttribute) Attributes.SCALE.value();
		context.assertTrue(GiantessConfig.Size.MIN_SCALE == scaleRange.getMinValue() && GiantessConfig.Size.MAX_SCALE == scaleRange.getMaxValue(),
			"the config scale limits drifted from the vanilla scale attribute range");
		context.succeed();
	}

	@GameTest(maxTicks = 100)
	public void headBuriedInTerrainTakesNoDamage(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		GiantessEntity giantess = context.spawn(ModEntities.GIANTESS, 2, 2, 2);
		BlockPos eye = BlockPos.containing(giantess.getEyePosition());
		for (BlockPos pos : BlockPos.betweenClosed(eye.offset(-3, -2, -3), eye.offset(3, 2, 3))) {
			level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		}
		float before = giantess.getHealth();

		context.runAfterDelay(60, () -> {
			context.assertTrue(giantess.isAlive() && giantess.getHealth() == before, "she was hurt while buried in terrain");
			context.assertTrue(giantess.onGround(), "she is not standing on the ground");
			context.succeed();
		});
	}

	@GameTest
	public void ignoresEnvironmentalDamageButNotAttacks(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		GiantessEntity giantess = context.spawn(ModEntities.GIANTESS, 2, 2, 2);
		DamageSources sources = level.damageSources();
		for (DamageSource source : List.of(sources.inWall(), sources.cramming(), sources.fall(), sources.drown(),
			sources.cactus(), sources.lava(), sources.onFire())) {
			context.assertTrue(giantess.isInvulnerableTo(level, source), source.getMsgId() + " should not hurt her");
		}

		float before = giantess.getHealth();
		giantess.hurtServer(level, sources.generic(), 10.0F);
		context.assertTrue(giantess.getHealth() == before - 10.0F, "ordinary damage should still hurt her");
		context.succeed();
	}

	@GameTest
	public void walksThroughTreesButNotWalls(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		GiantessEntity giantess = context.spawn(ModEntities.GIANTESS, 2, 2, 2);
		Zombie zombie = context.spawn(EntityTypes.ZOMBIE, 5, 2, 5);
		BlockPos pos = context.absolutePos(new BlockPos(6, 2, 6));
		for (BlockState plant : List.of(Blocks.OAK_LOG.defaultBlockState(), Blocks.OAK_LEAVES.defaultBlockState())) {
			context.assertTrue(CollisionContext.of(giantess).getCollisionShape(plant, level, pos).isEmpty(), plant + " should not block her");
			context.assertTrue(!CollisionContext.of(zombie).getCollisionShape(plant, level, pos).isEmpty(), plant + " should still block other mobs");
		}
		context.assertTrue(!CollisionContext.of(giantess).getCollisionShape(Blocks.STONE.defaultBlockState(), level, pos).isEmpty(),
			"stone should block her");
		context.assertTrue(giantess.getNavigation().createPath(pos, 0) == null, "vanilla A* should be off for her");
		float expectedStep = Math.min(10.0F, (float) GiantessConfig.get().movement.stepHeightPerScale * giantess.getScale());
		context.assertTrue(giantess.maxUpStep() == expectedStep, "step height should follow her scale");
		context.succeed();
	}

	/** Shrinks her to normal size so a straight walk fits the 8x8 test area, then checks what the terrain probe accepts. */
	@GameTest
	public void strollCheckRejectsLedgesWaterAndLava(GameTestHelper context) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				context.setBlock(x, 1, z, Blocks.STONE);
			}
		}
		GiantessEntity giantess = context.spawn(ModEntities.GIANTESS, new Vec3(1.5, 2.0, 3.5));
		giantess.setNoAi(true);
		giantess.getAttribute(Attributes.SCALE).setBaseValue(1.0);
		giantess.refreshDimensions();
		Vec3 target = context.absoluteVec(new Vec3(6.5, 2.0, 3.5));
		BlockPos middle = new BlockPos(4, 2, 3);

		context.assertTrue(new Footing(giantess).checkStraightWalk(target, 0.6, 0) != null, "flat ground should be walkable");

		context.setBlock(middle, Blocks.STONE);
		context.assertTrue(new Footing(giantess).checkStraightWalk(target, 0.6, 0) == null, "a 1-block ledge is above a 0.6 step");
		context.assertTrue(new Footing(giantess).checkStraightWalk(target, 1.2, 0) != null, "a 1-block ledge is within a 1.2 step");

		for (int y = 2; y < 6; y++) {
			context.setBlock(middle.atY(y), Blocks.OAK_LOG);
		}
		context.setBlock(middle.atY(6), Blocks.OAK_LEAVES);
		context.assertTrue(new Footing(giantess).checkStraightWalk(target, 0.6, 0) != null, "she should walk through a tree");

		for (int y = 2; y < 7; y++) {
			context.setBlock(middle.atY(y), Blocks.AIR);
		}
		context.setBlock(middle.below(), Blocks.WATER);
		context.setBlock(middle.below(2), Blocks.STONE);
		context.assertTrue(new Footing(giantess).checkStraightWalk(target, 1.2, 0) == null, "water deeper than allowed should be refused");
		context.assertTrue(new Footing(giantess).checkStraightWalk(target, 1.2, 1) != null, "shallow water should be wadeable");

		Vec3 fromWater = context.absoluteVec(new Vec3(4.5, 1.0, 3.5));
		Vec3 shore = context.absoluteVec(new Vec3(1.5, 2.0, 3.5));
		giantess.setPos(fromWater);
		context.assertTrue(new Footing(giantess).checkStraightWalk(shore, 1.2, 0) != null, "she should wade out of water deeper than allowed");
		giantess.setPos(context.absoluteVec(new Vec3(1.5, 2.0, 3.5)));

		context.setBlock(middle.below(), Blocks.LAVA);
		context.assertTrue(new Footing(giantess).checkStraightWalk(target, 10, 10) == null, "lava should always be refused");
		context.succeed();
	}

	/** Walks a normal-sized giantess across farmland and turtle eggs and counts her footfalls; nothing may break. */
	@GameTest(maxTicks = 100)
	public void footstepsFollowStrideAndBreakNothing(GameTestHelper context) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				context.setBlock(x, 1, z, Blocks.STONE);
			}
		}
		BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7);
		context.setBlock(2, 1, 3, farmland);
		context.setBlock(3, 1, 3, farmland);
		for (int x = 4; x <= 5; x++) {
			context.setBlock(x, 2, 3, Blocks.TURTLE_EGG);
		}
		GiantessEntity giantess = context.spawn(ModEntities.GIANTESS, new Vec3(0.5, 2.0, 3.5));
		giantess.setNoAi(true);
		giantess.getAttribute(Attributes.SCALE).setBaseValue(1.0);
		giantess.refreshDimensions();
		Vec3 start = giantess.position();
		for (int tick = 1; tick <= 30; tick++) {
			context.runAtTickTime(tick, () -> giantess.move(MoverType.SELF, new Vec3(0.2, -0.05, 0.0)));
		}
		context.runAtTickTime(32, () -> {
			double walked = giantess.position().subtract(start).horizontalDistance();
			context.assertTrue(walked > 5.0, "she should have crossed the room, walked " + walked);
			double stride = GiantessConfig.get().footsteps.strideLengthPerScale * giantess.getScale();
			int expected = (int) (walked / stride);
			context.assertTrue(Math.abs(giantess.footstepCount() - expected) <= 1,
				"footfalls " + giantess.footstepCount() + " should match walked " + walked + " / stride " + stride);
			context.assertBlockPresent(Blocks.FARMLAND, 2, 1, 3);
			context.assertBlockPresent(Blocks.FARMLAND, 3, 1, 3);
			context.assertBlockPresent(Blocks.TURTLE_EGG, 4, 2, 3);
			context.assertBlockPresent(Blocks.TURTLE_EGG, 5, 2, 3);
			context.succeed();
		});
	}

	/** A drop that would trample farmland under any other mob of her size leaves it alone. */
	@GameTest(maxTicks = 100)
	public void landingDoesNotTrampleFarmland(GameTestHelper context) {
		BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7);
		context.setBlock(3, 1, 3, farmland);
		GiantessEntity giantess = context.spawn(ModEntities.GIANTESS, new Vec3(3.5, 8.0, 3.5));
		giantess.getAttribute(Attributes.SCALE).setBaseValue(1.0);
		giantess.refreshDimensions();
		context.runAtTickTime(60, () -> {
			context.assertTrue(giantess.onGround(), "she should have landed");
			context.assertBlockPresent(Blocks.FARMLAND, 3, 1, 3);
			context.succeed();
		});
	}

	/** Spawns a scale-2 giantess, lets her land, turns her to face south (her left is +x), then runs the check. */
	private static void withLandedGiantess(GameTestHelper context, java.util.function.Consumer<GiantessEntity> check) {
		GiantessEntity giantess = context.spawn(ModEntities.GIANTESS, new Vec3(4.5, 2.0, 4.5));
		giantess.getAttribute(Attributes.SCALE).setBaseValue(2.0);
		giantess.refreshDimensions();
		context.runAtTickTime(20, () -> {
			context.assertTrue(giantess.onGround(), "she should have landed");
			// No AI (also no gravity) from here, so she holds still while the test moves her riders.
			giantess.setNoAi(true);
			giantess.setYBodyRot(0.0F);
			giantess.yBodyRotO = 0.0F;
			check.accept(giantess);
		});
	}

	private static boolean board(ServerPlayer player, GiantessEntity giantess) {
		return giantess.interact(player, InteractionHand.MAIN_HAND, giantess.position()) == InteractionResult.SUCCESS
			&& player.getVehicle() == giantess;
	}

	@GameTest(maxTicks = 100)
	public void ridersSitOnHerShouldersAndTurnWithHer(GameTestHelper context) {
		withLandedGiantess(context, giantess -> {
			GiantessConfig.Climbing config = GiantessConfig.get().climbing;
			double side = config.seatSidePerScale * 2.0;
			ServerPlayer first = context.makeMockServerPlayerInLevel();
			ServerPlayer second = context.makeMockServerPlayerInLevel();
			ServerPlayer third = context.makeMockServerPlayerInLevel();
			context.assertTrue(board(first, giantess), "an empty hand should lift the player onto her");
			context.assertTrue(board(second, giantess), "a second player should get the other shoulder");
			context.assertFalse(board(third, giantess), "only two shoulders");
			giantess.positionRider(first);
			giantess.positionRider(second);
			context.assertTrue(Math.abs(first.getX() - giantess.getX() - side) < 0.01, "first rider on her left (+x), got dx=" + (first.getX() - giantess.getX()));
			context.assertTrue(Math.abs(second.getX() - giantess.getX() + side) < 0.01, "second rider on her right (-x)");
			context.assertTrue(first.getY() > giantess.getY() + 2.0, "rider should sit high on her, got dy=" + (first.getY() - giantess.getY()));

			first.setYRot(0.0F);
			giantess.setYBodyRot(90.0F);
			giantess.positionRider(first);
			context.assertTrue(Math.abs(first.getZ() - giantess.getZ() - side) < 0.01, "the seat should turn with her body");
			context.assertTrue(Math.abs(first.getYRot() - 90.0F) < 0.01, "the rider's view should turn with her, got " + first.getYRot());

			ServerLevel level = context.getLevel();
			// Mock players are creative and immune anyway, so check the damage hook itself.
			DamageSource inWall = level.damageSources().inWall();
			context.assertFalse(GiantessEntity.allowRiderDamage(first, inWall, 4.0F), "riders must not suffocate in what she walks through");
			context.assertTrue(GiantessEntity.allowRiderDamage(third, inWall, 4.0F), "players on foot still can");
			context.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void ridersClimbDownBesideHerUnhurt(GameTestHelper context) {
		withLandedGiantess(context, giantess -> {
			ServerPlayer rider = context.makeMockServerPlayerInLevel();
			context.assertTrue(board(rider, giantess), "player should board");
			giantess.positionRider(rider);
			rider.stopRiding();
			assertSetDownBeside(context, giantess, rider);

			ServerPlayer survivor = context.makeMockServerPlayerInLevel();
			context.assertTrue(board(survivor, giantess), "player should board again");
			giantess.positionRider(survivor);
			giantess.kill(context.getLevel());
			assertSetDownBeside(context, giantess, survivor);
			context.runAfterDelay(20, () -> {
				context.assertTrue(rider.getHealth() == rider.getMaxHealth() && survivor.getHealth() == survivor.getMaxHealth(), "nobody should be hurt");
				context.succeed();
			});
		});
	}

	private static void assertSetDownBeside(GameTestHelper context, GiantessEntity giantess, ServerPlayer rider) {
		context.assertFalse(rider.isPassenger(), "rider should be off");
		context.assertTrue(Math.abs(rider.getY() - giantess.getY()) < 0.01, "rider should be on her ground level, got dy=" + (rider.getY() - giantess.getY()));
		double horizontal = Math.hypot(rider.getX() - giantess.getX(), rider.getZ() - giantess.getZ());
		context.assertTrue(horizontal >= giantess.getBbWidth() / 2.0, "rider should be beside her, got " + horizontal);
		context.assertTrue(rider.fallDistance == 0.0, "no fall pending");
	}
}
