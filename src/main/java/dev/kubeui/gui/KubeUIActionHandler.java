package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/// Registered (typically from a `server_scripts` file, via `KubeUIActions.register`) to validate
/// and execute a server-authoritative action requested from a KubeUI screen. The server decides
/// whether the action is valid and what it does - never trust `data`'s contents for anything that
/// matters (e.g. a price): look up the real value server-side instead of trusting what the client
/// sent, the same way you would for any other player-triggered server action.
@FunctionalInterface
public interface KubeUIActionHandler {
	void handle(ServerPlayer player, CompoundTag data);
}
