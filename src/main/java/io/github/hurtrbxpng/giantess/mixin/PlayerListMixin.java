package io.github.hurtrbxpng.giantess.mixin;

import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player leaving while on her shoulder is set down beside her before being saved. Vanilla would instead save
 * her inside a lone rider's player data, so she would vanish from the world until that player came back.
 */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
	@Inject(method = "remove(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
	private void giantess$setRiderDown(ServerPlayer player, CallbackInfo ci) {
		if (player.getVehicle() instanceof GiantessEntity) {
			player.stopRiding();
		}
	}
}
