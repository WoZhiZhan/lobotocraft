package com.wzz.lobotocraft.mixin;

import com.wzz.lobotocraft.logger.ModLogger;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserves the packet decoder stack trace in normal production logs. */
@Mixin(Connection.class)
public abstract class MixinConnection {
    @Inject(method = "exceptionCaught", at = @At("HEAD"))
    private void lobotocraft$logNetworkFailure(ChannelHandlerContext context, Throwable failure,
                                                CallbackInfo callback) {
        Connection connection = (Connection) (Object) this;
        ModLogger.LOGGER.error("Network failure while receiving {} packets",
                connection.getDirection(), failure);
    }
}
