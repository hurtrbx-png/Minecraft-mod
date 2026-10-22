package io.github.hurtrbxpng.giantess;

import io.github.hurtrbxpng.giantess.command.GiantessCommand;
import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.disposition.Disposition;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import io.github.hurtrbxpng.giantess.registry.ModRegistries;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
		// Her shoulders pass through tree trunks and overhangs she walks under; riders must not suffocate there.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GiantessEntity::allowRiderDamage);
		Disposition.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> GiantessCommand.register(dispatcher));
		LOGGER.info("Giantess initialised");
	}
}
