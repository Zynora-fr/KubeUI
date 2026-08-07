package dev.kubeui.gui;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/// Client -> server packet behind `screen.runServerAction(id, data)` - see [KubeUIActions].
/// `version` lets a server evolve its own payload conventions without breaking clients still
/// running an older KubeUI build. `requestId` is 0 for a fire-and-forget call (the vast majority);
/// a non-zero value asks the server to reply with a [KubeUIActionAckPayload] carrying the same id
/// once the action has actually run, for `screen.runServerAction(id, data, onAck)`.
public record KubeUIActionPayload(int version, String action, CompoundTag data, int requestId) implements CustomPacketPayload {
	public static final int CURRENT_VERSION = 1;

	public static final Type<KubeUIActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kubeui", "action"));

	public static final StreamCodec<ByteBuf, KubeUIActionPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, KubeUIActionPayload::version,
		ByteBufCodecs.STRING_UTF8, KubeUIActionPayload::action,
		ByteBufCodecs.COMPOUND_TAG, KubeUIActionPayload::data,
		ByteBufCodecs.VAR_INT, KubeUIActionPayload::requestId,
		KubeUIActionPayload::new
	);

	public KubeUIActionPayload(String action, CompoundTag data) {
		this(CURRENT_VERSION, action, data, 0);
	}

	public KubeUIActionPayload(String action, CompoundTag data, int requestId) {
		this(CURRENT_VERSION, action, data, requestId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
