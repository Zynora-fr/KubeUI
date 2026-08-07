package dev.kubeui.plugin;

import dev.kubeui.gui.KubeUIContext;
import dev.kubeui.gui.KubeUIEvents;
import dev.kubeui.gui.KubeUIRemoteScreens;
import dev.kubeui.gui.KubeUIScreenBuilder;
import dev.kubeui.gui.KubeUIScreenInjector;
import dev.kubeui.gui.KubeUISidebar;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;

/// Registers KubeUI's Java API with KubeJS. Loaded client-side only (see kubejs.plugins.txt).
public class KubeUIPlugin implements KubeJSPlugin {
	@Override
	public void registerClasses(ClassFilter filter) {
		if (filter.scriptType == ScriptType.CLIENT) {
			filter.allow(KubeUIScreenBuilder.class);
			filter.allow(KubeUIContext.class);
			filter.allow(KubeUISidebar.class);
			filter.allow(KubeUIScreenInjector.class);
			filter.allow(KubeUIRemoteScreens.class);
		}
	}

	@Override
	public void registerBindings(BindingRegistry bindings) {
		if (bindings.type() == ScriptType.CLIENT) {
			bindings.add("KubeUI", KubeUIScreenBuilder.class);
			bindings.add("KubeUISidebar", KubeUISidebar.class);
			bindings.add("KubeUIScreenInjector", KubeUIScreenInjector.class);
			bindings.add("KubeUIRemoteScreens", KubeUIRemoteScreens.class);
		}
	}

	@Override
	public void registerEvents(EventGroupRegistry registry) {
		registry.register(KubeUIEvents.GROUP);
	}
}
