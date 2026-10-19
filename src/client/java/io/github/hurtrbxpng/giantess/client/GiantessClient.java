package io.github.hurtrbxpng.giantess.client;

import io.github.hurtrbxpng.giantess.Giantess;
import io.github.hurtrbxpng.giantess.client.render.GiantessRenderer;
import io.github.hurtrbxpng.giantess.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class GiantessClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.GIANTESS, GiantessRenderer::new);
		Giantess.LOGGER.info("Giantess client initialised");
	}
}
