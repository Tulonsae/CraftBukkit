package net.minecraft.server;

import net.minecraft.util.io.netty.util.concurrent.GenericFutureListener;

public class HandshakeListener implements PacketHandshakingInListener {

    private final MinecraftServer a;
    private final NetworkManager b;

    public HandshakeListener(MinecraftServer minecraftserver, NetworkManager networkmanager) {
        this.a = minecraftserver;
        this.b = networkmanager;
    }

    public void a(PacketHandshakingInSetProtocol packethandshakinginsetprotocol) {
        switch (ProtocolOrdinalWrapper.a[packethandshakinginsetprotocol.c().ordinal()]) {
        case 1:
            this.b.a(EnumProtocol.LOGIN);
            ChatComponentText chatcomponenttext;

            if (packethandshakinginsetprotocol.d() > 4) {
                chatcomponenttext = new ChatComponentText( org.spigotmc.SpigotConfig.outdatedServerMessage ); // Spigot
                this.b.handle(new PacketLoginOutDisconnect(chatcomponenttext), new GenericFutureListener[0]);
                this.b.a((IChatBaseComponent) chatcomponenttext);
            } else if (packethandshakinginsetprotocol.d() < 4) {
                chatcomponenttext = new ChatComponentText( org.spigotmc.SpigotConfig.outdatedClientMessage ); // Spigot
                this.b.handle(new PacketLoginOutDisconnect(chatcomponenttext), new GenericFutureListener[0]);
                this.b.a((IChatBaseComponent) chatcomponenttext);
            } else {
                this.b.a((PacketListener) (new LoginListener(this.a, this.b)));
                // Spigot Start
                if (org.spigotmc.SpigotConfig.bungee) {
                    String[] split = packethandshakinginsetprotocol.b.split("\00");
                    if (split.length == 2) {
                        packethandshakinginsetprotocol.b = split[0];
                        b.l = new java.net.InetSocketAddress(split[1], ((java.net.InetSocketAddress) b.getSocketAddress()).getPort());
                    }
                }
                // Spigot End
                ((LoginListener) this.b.getPacketListener()).hostname = packethandshakinginsetprotocol.b + ":" + packethandshakinginsetprotocol.c; // CraftBukkit - set hostname
            }
            break;

        case 2:
            this.b.a(EnumProtocol.STATUS);
            this.b.a((PacketListener) (new PacketStatusListener(this.a, this.b)));
            break;

        default:
            throw new UnsupportedOperationException("Invalid intention " + packethandshakinginsetprotocol.c());
        }
    }

    public void a(IChatBaseComponent ichatbasecomponent) {}

    public void a(EnumProtocol enumprotocol, EnumProtocol enumprotocol1) {
        if (enumprotocol1 != EnumProtocol.LOGIN && enumprotocol1 != EnumProtocol.STATUS) {
            throw new UnsupportedOperationException("Invalid state " + enumprotocol1);
        }
    }

    public void a() {}
}
