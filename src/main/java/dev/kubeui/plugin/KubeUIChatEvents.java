package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIChatDecorateEvent;
import dev.kubeui.gui.KubeUIChatScriptEvents;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

/// Posts [KubeUIChatDecorateEvent] for every real chat message - real, decompiled-and-confirmed
/// integration point (`ServerGamePacketListenerImpl#handleChat` runs the decorated content through
/// `CommonHooks.getServerChatSubmittedDecorator()`, which posts this exact NeoForge event and uses
/// whatever `#setMessage` leaves behind as the actual broadcast content; cancelling drops the
/// message entirely - not a cosmetic client-side overlay, every other player's client renders
/// this). Deliberately doesn't decide *what* to do with a message itself (no hardcoded guild tag
/// or anything else here) - see [KubeUIChatDecorateEvent]'s own doc for why that's entirely a
/// script's call ("vraiment tout en JS" - real ask), reacting to
/// `KubeUIChatScriptEvents.decorate(event => {...})`.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIChatEvents {
	private KubeUIChatEvents() {
	}

	@SubscribeEvent
	static void onServerChat(ServerChatEvent event) {
		var chatEvent = new KubeUIChatDecorateEvent(event.getPlayer(), event.getMessage().getString());
		var result = KubeUIChatScriptEvents.DECORATE.post(ScriptType.SERVER, chatEvent);
		if (result.applyCancel(event)) {
			return;
		}
		event.setMessage(Component.literal(chatEvent.getMessage()));
	}
}
