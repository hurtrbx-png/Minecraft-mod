package io.github.hurtrbxpng.giantess.mixin;

import io.github.hurtrbxpng.giantess.client.effects.CameraShake;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the footstep shake after the camera has been placed on its entity, before the view matrices are built. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	public abstract Vec3 position();

	@Shadow
	public abstract float xRot();

	@Shadow
	public abstract float yRot();

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void giantess$shake(float partialTicks, CallbackInfo ci) {
		CameraShake.Offset offset = CameraShake.offset(partialTicks);
		if (offset != null) {
			setRotation(yRot() + offset.yawDegrees(), xRot() + offset.pitchDegrees());
			setPosition(position().add(0.0, offset.lift(), 0.0));
		}
	}
}
