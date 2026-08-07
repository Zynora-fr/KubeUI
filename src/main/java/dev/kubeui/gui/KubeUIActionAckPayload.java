package dev.kubeui.gui;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/// Server -> client reply to a [KubeUIActionPayload] whose `requestId` was non-zero - see
/// `screen.runServerAction(id, data, onAck)`. `success` is false if the action wasn't registered,
/// failed schema validation, was throttled, or the handler itself threw - never a crash either way.
public record KubeUIActionAckPayload(int requestId, boolean success) implements CustomPacketPayload {
	public static final Type<KubeUIActionAckPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kubeui", "action_ack"));

	public static final StreamCodec<ByteBuf, KubeUIActionAckPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, KubeUIActionAckPayload::requestId,
		ByteBufCodecs.BOOL, KubeUIActionAckPayload::success,
		KubeUIActionAckPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
