package io.github.hurtrbxpng.giantess.test;

import io.github.hurtrbxpng.giantess.Giantess;
import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;

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
}
