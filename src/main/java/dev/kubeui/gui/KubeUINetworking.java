package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// Registers [KubeUIActionPayload] and dispatches received ones to [KubeUIActions].
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUINetworking {
	private KubeUINetworking() {
	}

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		event.registrar("1").playToServer(KubeUIActionPayload.TYPE, KubeUIActionPayload.STREAM_CODEC, KubeUINetworking::handle);
	}

	private static void handle(KubeUIActionPayload payload, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (ctx.player() instanceof ServerPlayer serverPlayer) {
				var handler = KubeUIActions.get(payload.action());

				if (handler != null) {
					handler.handle(serverPlayer, payload.data());
				} else {
					KubeUI.LOGGER.warn("Received unknown KubeUI action '{}' from {}", payload.action(), serverPlayer.getName().getString());
				}
			}
		});
	}

	static void sendAction(String action, CompoundTag data) {
		ClientPacketDistributor.sendToServer(new KubeUIActionPayload(action, data == null ? new CompoundTag() : data));
	}
}
