package io.github.hurtrbxpng.giantess.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.hurtrbxpng.giantess.disposition.Disposition;
import java.util.Collection;
import java.util.function.IntBinaryOperator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /giantess trust <players> [set|add <value>]}: read or change trust, for testing and server admins. */
public final class GiantessCommand {
	/** Twice the largest allowed maxTrust, so "add" can cross the whole range without overflowing. */
	private static final int MAX_VALUE = 20000;

	private GiantessCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("giantess")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("trust")
				.then(Commands.argument("targets", EntityArgument.players())
					.executes(c -> get(c.getSource(), EntityArgument.getPlayers(c, "targets")))
					.then(Commands.literal("set").then(Commands.argument("value", IntegerArgumentType.integer(-MAX_VALUE, MAX_VALUE))
						.executes(c -> change(c.getSource(), EntityArgument.getPlayers(c, "targets"),
							IntegerArgumentType.getInteger(c, "value"), (old, value) -> value))))
					.then(Commands.literal("add").then(Commands.argument("value", IntegerArgumentType.integer(-MAX_VALUE, MAX_VALUE))
						.executes(c -> change(c.getSource(), EntityArgument.getPlayers(c, "targets"),
							IntegerArgumentType.getInteger(c, "value"), Integer::sum)))))));
	}

	private static int get(CommandSourceStack source, Collection<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			source.sendSuccess(() -> Component.translatable("commands.giantess.trust.get",
				player.getDisplayName(), Disposition.trust(player), Disposition.mood(player).id()), false);
		}
		return players.size();
	}

	private static int change(CommandSourceStack source, Collection<ServerPlayer> players, int value, IntBinaryOperator op) {
		for (ServerPlayer player : players) {
			Disposition.setTrust(player, op.applyAsInt(Disposition.trust(player), value));
			source.sendSuccess(() -> Component.translatable("commands.giantess.trust.set",
				player.getDisplayName(), Disposition.trust(player), Disposition.mood(player).id()), true);
		}
		return players.size();
	}
}
