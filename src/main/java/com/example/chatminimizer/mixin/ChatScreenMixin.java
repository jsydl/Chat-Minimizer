package com.example.chatminimizer.mixin;

import com.example.chatminimizer.ChatMinimizerState;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "sendMessage(Ljava/lang/String;Z)V", at = @At("HEAD"), require = 0)
    private void cm$trackCommandNew(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (chatText != null && chatText.startsWith("/")) {
            ChatMinimizerState.markCommandSent();
        }
    }

    @Inject(method = "sendMessage(Ljava/lang/String;)V", at = @At("HEAD"), require = 0)
    private void cm$trackCommandOld(String chatText, CallbackInfo ci) {
        if (chatText != null && chatText.startsWith("/")) {
            ChatMinimizerState.markCommandSent();
        }
    }
}