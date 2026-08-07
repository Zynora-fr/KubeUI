package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContextKey;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/// Real integration with NeoForge's own [PermissionAPI] - the "neutral hook for LuckPerms or
/// equivalent" `.requirePermission(gate)` needs: a permission plugin registers its own
/// [net.neoforged.neoforge.server.permission.handler.IPermissionHandler] via
/// [PermissionGatherEvent.Handler] independently of KubeUI, and once it does, every check below
/// goes through it instead of [#DEFAULT_RESOLVER]. Without one installed, the default is "server
/// operators only" (permission level 2, the same tier vanilla uses for `/gamemode`).
///
/// One node (`kubeui.widget`) is registered, parameterized by a [PermissionDynamicContextKey]
/// carrying the gate name a script chose (`"shop.buy"`, etc.) - not one node per gate string.
/// [PermissionNode]s can only be registered during [PermissionGatherEvent.Nodes], which fires once
/// during server startup, long before any `client_scripts`-declared gate name is known - a dynamic
/// context is NeoForge's own intended mechanism for exactly this "arbitrary sub-permission string
/// decided at runtime" case (see its own doc comment: "a dimension context key could be used
/// inside a building permission check").
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIPermissions {
	static final PermissionDynamicContextKey<String> GATE = new PermissionDynamicContextKey<>(String.class, "gate", s -> s);

	static final PermissionNode<Boolean> WIDGET_GATE = new PermissionNode<>(
		"kubeui", "widget", PermissionTypes.BOOLEAN,
		(player, uuid, context) -> player != null && player.hasPermissions(2),
		GATE
	);

	private KubeUIPermissions() {
	}

	@SubscribeEvent
	static void onGatherNodes(PermissionGatherEvent.Nodes event) {
		event.addNodes(WIDGET_GATE);
	}

	/// Whether `player` may use a `.requirePermission(gate)` widget - backs both the automatic
	/// client-side check (screen open, batched over every distinct gate on that screen) and
	/// anything a script's own action handler wants to check server-side directly.
	static boolean check(ServerPlayer player, String gate) {
		return PermissionAPI.getPermission(player, WIDGET_GATE, GATE.createContext(gate));
	}
}
