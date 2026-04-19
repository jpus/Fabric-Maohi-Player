package com.example.maohi.mixin;

import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 阻止 Minecraft 服务器在控制台打印 "Done (...)" 消息。
 * 不影响服务器的实际运行，玩家仍可正常连接。
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    /**
     * 拦截 finishServerStartup 方法中打印 "Done" 的日志调用。
     * 使用 @Redirect 将原始 Logger.info 调用替换为空操作。
     * 同时兼容 Yarn 和 Mojmap 映射（参数类型均为 String, Object, Object）。
     */
    @Redirect(
        method = "finishServerStartup",
        at = @At(
            value = "INVOKE",
            target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V",
            ordinal = 0
        ),
        require = 1,   // 若找不到目标则编译失败，确保生效
        remap = true
    )
    private void suppressDoneLog(Logger logger, String format, Object arg1, Object arg2) {
        // 可选：检查一下 format 是否真的是 "Done ({})! For help, type \"help\""
        // 但没必要，因为 ordinal=0 已经唯一指定了那个位置
        // 完全丢弃该日志，不做任何输出
    }
}