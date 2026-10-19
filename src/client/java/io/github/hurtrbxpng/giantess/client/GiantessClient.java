package io.github.hurtrbxpng.giantess.client;

import io.github.hurtrbxpng.giantess.Giantess;
import io.github.hurtrbxpng.giantess.client.render.GiantessRenderer;
import io.github.hurtrbxpng.giantess.client.effects.CameraShake;
import io.github.hurtrbxpng.giantess.client.effects.FootstepEffects;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import io.github.hurtrbxpng.giantess.registry.ModEntities;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class GiantessClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.GIANTESS, GiantessRenderer::new);
		GiantessEntity.setFootstepListener(FootstepEffects::onFootstep);
		ClientTickEvents.END_CLIENT_TICK.register(client -> CameraShake.tick());
		Giantess.LOGGER.info("Giantess client initialised");
	}
}
