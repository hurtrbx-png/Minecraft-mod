package io.github.hurtrbxpng.giantess;

import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.registry.ModRegistries;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Giantess implements ModInitializer {
	public static final String MOD_ID = "giantess";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// Config first: registration reads tunable values from it.
		GiantessConfig.load();
		ModRegistries.init();
		LOGGER.info("Giantess initialised");
	}
}
