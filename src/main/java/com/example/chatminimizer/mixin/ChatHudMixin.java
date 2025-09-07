package com.example.chatminimizer.mixin;

import com.example.chatminimizer.ChatMinimizerClient;
import com.example.chatminimizer.ChatMinimizerState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"), cancellable = true, require = 0
    )
    private void cm$filterNew(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        cm$applyFilter(message, signature, indicator, ci);
    }

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;)V",
        at = @At("HEAD"), cancellable = true, require = 0
    )
    private void cm$filterOld(Text message, CallbackInfo ci) {
        cm$applyFilter(message, null, null, ci);
    }

    private void cm$applyFilter(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        var mode = ChatMinimizerState.getMode();
        if (mode == ChatMinimizerState.Mode.DISABLED) return;

        // Do NOT filter while chat is open.
        if (MinecraftClient.getInstance() != null &&
            MinecraftClient.getInstance().currentScreen instanceof ChatScreen) {
            return;
        }

        ChatMinimizerState.Kind kind = ChatMinimizerClient.classifyKind(message, indicator, signature);

        boolean cancel = switch (mode) {
            case ALL -> true;
            case COMMANDS -> kind == ChatMinimizerState.Kind.COMMANDS;
            case CHAT -> kind == ChatMinimizerState.Kind.CHAT;
            default -> false;
        };

        if (cancel) {
            ChatMinimizerState.bufferSuppressed(message, signature, indicator, kind);
            ci.cancel();
        }
    }
}