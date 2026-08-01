package dev.kubeui.gui;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Client -> server packet behind `screen.runServerAction(id, data)` - see [KubeUIActions].
public record KubeUIActionPayload(String action, CompoundTag data) implements CustomPacketPayload {
	public static final Type<KubeUIActionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("kubeui", "action"));

	public static final StreamCodec<ByteBuf, KubeUIActionPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, KubeUIActionPayload::action,
		ByteBufCodecs.COMPOUND_TAG, KubeUIActionPayload::data,
		KubeUIActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
