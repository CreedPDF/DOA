package com.creedpdf.doa.net;

import com.creedpdf.doa.DoaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** DOA's only packet: a toast the client writes in DOA's own lettering instead of Minecraft's action bar. */
public final class DoaNet {
    public record ToastPayload(String key, String arg, int color) implements CustomPayload {
        public static final Id<ToastPayload> ID = new Id<>(DoaMod.id("toast"));
        public static final PacketCodec<RegistryByteBuf, ToastPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, ToastPayload::key, PacketCodecs.STRING, ToastPayload::arg, PacketCodecs.INTEGER, ToastPayload::color,
                ToastPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private DoaNet() {
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(ToastPayload.ID, ToastPayload.CODEC);
    }

    /** key: a translation key, or a literal string prefixed with "=". */
    public static void toast(PlayerEntity player, String key, String arg, int color) {
        if (player instanceof ServerPlayerEntity sp && ServerPlayNetworking.canSend(sp, ToastPayload.ID))
            ServerPlayNetworking.send(sp, new ToastPayload(key, arg == null ? "" : arg, color));
    }
}
