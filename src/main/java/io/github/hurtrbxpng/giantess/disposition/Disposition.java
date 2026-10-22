package io.github.hurtrbxpng.giantess.disposition;

import com.mojang.serialization.Codec;
import io.github.hurtrbxpng.giantess.Giantess;
import io.github.hurtrbxpng.giantess.config.GiantessConfig;
import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * How giantesses feel about each player: one trust value per player, shared by every giantess,
 * stored on the player so it follows them across dimensions, deaths and relogs.
 * Changes happen on the server only; the value is synced to its own player.
 */
public final class Disposition {
	public enum Mood {
		HOSTILE,
		NEUTRAL,
		FRIENDLY;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final AttachmentType<Integer> TRUST = AttachmentRegistry.create(Giantess.id("trust"), builder -> builder
		.persistent(Codec.INT)
		.initializer(() -> 0)
		.copyOnDeath()
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** Game time of the player's last accepted gift; not saved, a relog simply ends the wait. */
	private static final AttachmentType<Long> LAST_GIFT = AttachmentRegistry.create(Giantess.id("last_gift"));

	private Disposition() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(Disposition::onDeath);
	}

	public static int trust(Player player) {
		return player.getAttachedOrElse(TRUST, 0);
	}

	public static Mood mood(Player player) {
		return moodFor(trust(player));
	}

	public static Mood moodFor(int trust) {
		GiantessConfig.Disposition config = GiantessConfig.get().disposition;
		if (trust <= config.hostileAt) {
			return Mood.HOSTILE;
		}
		return trust >= config.friendlyAt ? Mood.FRIENDLY : Mood.NEUTRAL;
	}

	/** Sets trust (clamped to the configured range) and tells the player when her mood towards them changes. */
	public static void setTrust(ServerPlayer player, int value) {
		int max = GiantessConfig.get().disposition.maxTrust;
		int trust = Mth.clamp(value, -max, max);
		Mood before = mood(player);
		player.setAttached(TRUST, trust);
		Mood after = moodFor(trust);
		if (after != before) {
			player.sendOverlayMessage(Component.translatable("giantess.mood." + after.id()));
		}
		if (GiantessConfig.get().debugLogging) {
			Giantess.LOGGER.info("Trust of {} is now {} ({})", player.getScoreboardName(), trust, after.id());
		}
	}

	public static void changeTrust(ServerPlayer player, int delta) {
		setTrust(player, trust(player) + delta);
	}

	/** Accepts a gift unless the player gave one too recently; the caller consumes the item. */
	public static boolean tryGift(ServerPlayer player) {
		GiantessConfig.Disposition config = GiantessConfig.get().disposition;
		long now = player.level().getGameTime();
		Long last = player.getAttached(LAST_GIFT);
		if (last != null && now - last < config.giftCooldownTicks) {
			player.sendOverlayMessage(Component.translatable("giantess.gift.too_soon"));
			return false;
		}
		player.setAttached(LAST_GIFT, now);
		changeTrust(player, config.giftTrust);
		return true;
	}

	/** Defending villagers: a player kills a monster near a villager while a giantess is close enough to see it. */
	private static void onDeath(LivingEntity dead, DamageSource source) {
		if (!(dead instanceof Enemy) || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		GiantessConfig.Disposition config = GiantessConfig.get().disposition;
		boolean villagerNearby = !dead.level().getEntitiesOfClass(AbstractVillager.class,
			dead.getBoundingBox().inflate(config.protectVillagerRadius), LivingEntity::isAlive).isEmpty();
		if (villagerNearby && !dead.level().getEntitiesOfClass(GiantessEntity.class,
			dead.getBoundingBox().inflate(config.witnessRadius), LivingEntity::isAlive).isEmpty()) {
			changeTrust(player, config.protectTrust);
		}
	}
}
