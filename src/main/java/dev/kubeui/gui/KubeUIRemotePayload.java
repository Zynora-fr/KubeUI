package dev.kubeui.gui;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/// Server -> client packet behind `KubeUIActions.openRemote(player, screenId, data)` /
/// `.broadcastUpdate(screenId, data)` - see [KubeUIActions]. `screenId` is whatever a
/// `client_scripts` file registered via [KubeUIRemoteScreens#register] to build/open that screen;
/// KubeUI itself never interprets it beyond a lookup key.
public record KubeUIRemotePayload(int version, String screenId, CompoundTag data) implements CustomPacketPayload {
	public static final int CURRENT_VERSION = 1;

	public static final Type<KubeUIRemotePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("kubeui", "remote"));

	public static final StreamCodec<ByteBuf, KubeUIRemotePayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, KubeUIRemotePayload::version,
		ByteBufCodecs.STRING_UTF8, KubeUIRemotePayload::screenId,
		ByteBufCodecs.COMPOUND_TAG, KubeUIRemotePayload::data,
		KubeUIRemotePayload::new
	);

	public KubeUIRemotePayload(String screenId, CompoundTag data) {
		this(CURRENT_VERSION, screenId, data);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
