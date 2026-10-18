package io.github.hurtrbxpng.giantess.test;

import io.github.hurtrbxpng.giantess.Giantess;
import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
