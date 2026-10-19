package io.github.hurtrbxpng.giantess.registry;

/**
 * Single entry point for registration so the order is deterministic:
 * entities first, then the items that refer to them.
 */
public final class ModRegistries {
	private ModRegistries() {
	}

	public static void init() {
		ModEntities.register();
		ModItems.register();
	}
}
