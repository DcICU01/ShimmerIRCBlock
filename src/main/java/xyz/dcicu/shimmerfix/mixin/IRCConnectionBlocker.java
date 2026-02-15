package xyz.dcicu.shimmerfix.mixin;


import dev.greencat.shimmer.util.irc.IRC;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.net.Socket;

@Mixin(IRC.class)
public class IRCConnectionBlocker {

    @Redirect(
            method = "<init>",
            at = @At(value = "NEW", args = "class=java/net/Socket", remap = false)
    )
    private Socket redirectSocketCreation(String host, int port) throws IOException {
        if ("frp-rib.com".equals(host) && port == 12783) {
            System.out.println("[ShimmerFix] Blocked connection to " + host + ":" + port);
            throw new IOException("Connection blocked by ShimmerFix");
        }
        return new Socket(host, port);
    }
}