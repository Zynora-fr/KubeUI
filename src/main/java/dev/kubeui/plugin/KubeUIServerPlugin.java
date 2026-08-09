package dev.kubeui.plugin;

import dev.kubeui.gui.KubeUIActions;
import dev.kubeui.gui.KubeUIChatScriptEvents;
import dev.kubeui.gui.KubeUICombatEvents;
import dev.kubeui.gui.KubeUIEconomyEvents;
import dev.kubeui.gui.KubeUIGuildScheduledEvents;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;

/// Registers KubeUI's server-side API (`KubeUIActions`, for `screen.runServerAction(...)`, and the
/// `KubeUIEconomyEvents` group) with KubeJS. Loaded on both client and server (unlike
/// [KubeUIPlugin], which is client-only) since it must reach `server_scripts` - it only ever
/// references server-safe classes (no `net.minecraft.client.*`), so it's safe to load on a
/// dedicated server too.
public class KubeUIServerPlugin implements KubeJSPlugin {
	@Override
	public void registerClasses(ClassFilter filter) {
		if (filter.scriptType == ScriptType.SERVER) {
			filter.allow(KubeUIActions.class);
		}
	}

	@Override
	public void registerBindings(BindingRegistry bindings) {
		if (bindings.type() == ScriptType.SERVER) {
			bindings.add("KubeUIActions", KubeUIActions.class);
		}
	}

	@Override
	public void registerEvents(EventGroupRegistry registry) {
		registry.register(KubeUIEconomyEvents.GROUP);
		registry.register(KubeUICombatEvents.GROUP);
		registry.register(KubeUIGuildScheduledEvents.GROUP);
		registry.register(KubeUIChatScriptEvents.GROUP);
	}
}
