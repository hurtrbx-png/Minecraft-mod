package io.github.hurtrbxpng.giantess.client;

import io.github.hurtrbxpng.giantess.Giantess;
import net.fabricmc.api.ClientModInitializer;

public class GiantessClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Giantess.LOGGER.info("Giantess client initialised");
	}
}
