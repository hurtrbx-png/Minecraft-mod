package io.github.hurtrbxpng.giantess.client.render;

import io.github.hurtrbxpng.giantess.Giantess;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Placeholder: the vanilla humanoid model with our own skin. The scale attribute sizes it. */
public class GiantessRenderer extends HumanoidMobRenderer<GiantessEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = Giantess.id("textures/entity/giantess.png");

	public GiantessRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}
