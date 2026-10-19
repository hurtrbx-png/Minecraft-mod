package io.github.hurtrbxpng.giantess.mixin;

import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import io.github.hurtrbxpng.giantess.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trees and other plants in {@link ModTags#GIANTESS_WALKS_THROUGH} have no collision for her, so she wades
 * through forests instead of getting caught on trunks and canopies. Nothing is broken or changed.
 */
@Mixin(EntityCollisionContext.class)
abstract class EntityCollisionContextMixin {
	@Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
	private void giantess$walkThroughPlants(BlockState state, CollisionGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
		if (((EntityCollisionContext) (Object) this).getEntity() instanceof GiantessEntity && state.is(ModTags.GIANTESS_WALKS_THROUGH)) {
			cir.setReturnValue(Shapes.empty());
		}
	}
}
