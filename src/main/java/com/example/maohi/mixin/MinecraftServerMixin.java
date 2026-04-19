package com.example.maohi.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Shadow private volatile boolean running;

    @Inject(method = "finishServerStartup", at = @At("HEAD"), cancellable = true)
    private void suppressDone(CallbackInfo ci) {
        // 取消原方法，避免打印 "Done (...)"
        ci.cancel();
        // 手动将服务器标记为运行中（原方法的核心操作）
        this.running = true;
    }
}
