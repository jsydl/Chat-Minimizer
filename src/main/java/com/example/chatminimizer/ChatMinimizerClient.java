package com.example.chatminimizer;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ChatMinimizerClient implements ClientModInitializer {

    private static boolean wasChatOpen = false;

    @Override
    public void onInitializeClient() {
        // Load config (persists mode + backfill)
        Path cfg = FabricLoader.getInstance().getConfigDir().resolve("chatminimizer.json");
        ChatMinimizerState.initConfig(cfg);

        // When chat opens, flush ONLY the messages allowed by the current backfill setting.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean isOpen = client.currentScreen instanceof ChatScreen;
            if (isOpen && !wasChatOpen) {
                flushBuffered(client);
            }
            wasChatOpen = isOpen;
        });

        // /minimizechat commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("minimizechat")
                    .executes(ctx -> { sendStatus(ctx.getSource()); return 1; })

                    // false
                    .then(ClientCommandManager.literal("false").executes(ctx -> {
                        ChatMinimizerState.setMode(ChatMinimizerState.Mode.DISABLED); // persists
                        ctx.getSource().sendFeedback(Text.literal("Chat Minimizer: Disabled"));
                        return 1;
                    }))

                    // true [all|chat|commands]
                    .then(ClientCommandManager.literal("true")
                        .executes(ctx -> setAndNotify(ctx.getSource(), ChatMinimizerState.Mode.ALL))
                        .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                            .suggests(ChatMinimizerClient::suggestMinimizeModes)
                            .executes(ctx -> {
                                String raw = StringArgumentType.getString(ctx, "mode");
                                ChatMinimizerState.Mode m = parseMinimizeMode(raw);
                                if (m == null) {
                                    ctx.getSource().sendFeedback(Text.literal("Unknown mode: " + raw + " (use all, chat, commands)"));
                                    return 0;
                                }
                                return setAndNotify(ctx.getSource(), m);
                            })
                        )
                    )

                    // backfill [off|all|commands|chat]
                    .then(ClientCommandManager.literal("backfill")
                        .executes(ctx -> {
                            var cur = ChatMinimizerState.getBackfillMode();
                            ctx.getSource().sendFeedback(Text.literal("Backfill: " + pretty(cur)));
                            return 1;
                        })
                        .then(ClientCommandManager.argument("target", StringArgumentType.word())
                            .suggests(ChatMinimizerClient::suggestBackfillTargets)
                            .executes(ctx -> {
                                String raw = StringArgumentType.getString(ctx, "target");
                                ChatMinimizerState.BackfillMode b = parseBackfill(raw);
                                if (b == null) {
                                    ctx.getSource().sendFeedback(Text.literal("Unknown backfill target: " + raw + " (use off, all, commands, chat)"));
                                    return 0;
                                }
                                ChatMinimizerState.setBackfillMode(b); // persists
                                ctx.getSource().sendFeedback(Text.literal("Backfill: " + pretty(b)));
                                return 1;
                            })
                        )
                    )
            );
        });
    }

    private static void flushBuffered(MinecraftClient client) {
        ChatHud hud = client.inGameHud.getChatHud();
        var toFlush = ChatMinimizerState.drainBufferedFor(ChatMinimizerState.getBackfillMode());
        for (var b : toFlush) {
            if (b.signature() == null && b.indicator() == null) {
                hud.addMessage(b.message());
            } else {
                hud.addMessage(b.message(), b.signature(), b.indicator());
            }
        }
    }

    // ----- Suggestions -----
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMinimizeModes(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder b) {
        b.suggest("all").suggest("chat").suggest("commands");
        return b.buildFuture();
    }
    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBackfillTargets(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder b) {
        b.suggest("off").suggest("all").suggest("commands").suggest("chat");
        return b.buildFuture();
    }

    // ----- Parsers -----
    private static ChatMinimizerState.Mode parseMinimizeMode(String raw) {
        if (raw == null) return null;
        raw = raw.toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "all" -> ChatMinimizerState.Mode.ALL;
            case "commands" -> ChatMinimizerState.Mode.COMMANDS;
            case "chat", "game" -> ChatMinimizerState.Mode.CHAT;
            default -> null;
        };
    }
    private static ChatMinimizerState.BackfillMode parseBackfill(String raw) {
        if (raw == null) return null;
        raw = raw.toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "off" -> ChatMinimizerState.BackfillMode.OFF;
            case "all" -> ChatMinimizerState.BackfillMode.ALL;
            case "commands" -> ChatMinimizerState.BackfillMode.COMMANDS;
            case "chat", "game" -> ChatMinimizerState.BackfillMode.CHAT;
            default -> null;
        };
    }
    private static String pretty(ChatMinimizerState.BackfillMode m) {
        return switch (m) {
            case OFF -> "Off";
            case ALL -> "All";
            case COMMANDS -> "Commands";
            case CHAT -> "Chat";
        };
    }
    private static String pretty(ChatMinimizerState.Mode m) {
        return switch (m) {
            case ALL -> "All";
            case COMMANDS -> "Commands";
            case CHAT -> "Chat";
            case DISABLED -> "Disabled";
        };
    }

    // ----- Feedback -----
    private static int setAndNotify(FabricClientCommandSource src, ChatMinimizerState.Mode mode) {
        ChatMinimizerState.setMode(mode); // persists
        src.sendFeedback(Text.literal("Chat Minimizer: Enabled (" + pretty(mode) + ")"));
        return 1;
    }
    private static void sendStatus(FabricClientCommandSource src) {
        var m = ChatMinimizerState.getMode();
        if (m == ChatMinimizerState.Mode.DISABLED) {
            src.sendFeedback(Text.literal("Chat Minimizer: Disabled"));
        } else {
            src.sendFeedback(Text.literal("Chat Minimizer: Enabled (" + pretty(m) + ")"));
        }
    }

    // ===== Single-source classification (stable) =====
    public static ChatMinimizerState.Kind classifyKind(Text message, MessageIndicator indicator, MessageSignatureData signature) {
        return isCommandOutput(message, indicator, signature)
                ? ChatMinimizerState.Kind.COMMANDS
                : ChatMinimizerState.Kind.CHAT;
    }

    public static boolean isCommandOutput(Text message, MessageIndicator indicator, MessageSignatureData signature) {
        // A) Signed lines are *always* player chat.
        if (signature != null) return false;

        // B) True system-indicated lines are command/system.
        if (indicator == MessageIndicator.system()) return true;

        String s = message.getString();
        if (s == null) return false;
        String sl = s.toLowerCase(Locale.ROOT).trim();

        // C) Tags like [Server], [Console], [Command Block], [@], etc. -> commands
        if (looksLikeCommandTag(sl)) return true;

        // D) Known game system broadcasts -> not commands
        if (isGameMessage(message)) return false;

        // E) Window after *your* slash command -> treat unsigned replies as command output
        long dt = System.currentTimeMillis() - ChatMinimizerState.getLastCommandMs();
        if (dt >= 0 && dt <= 4000) return true;

        // F) Player chat formats:
        //    <Name> Hello
        if (sl.startsWith("<")) {
            int gt = sl.indexOf('>');
            if (gt >= 3 && gt <= 20) return false;
        }
        //    Name: Hello  (only if looks like a valid MC name; avoids "Server: ..." etc.)
        int colon = sl.indexOf(':');
        if (colon >= 3 && colon <= 20) {
            String name = sl.substring(0, colon).trim();
            if (name.matches("^[a-z0-9_]{3,16}$") &&
                !(name.equals("server") || name.equals("console") || name.equals("rcon")
                  || name.equals("system") || name.equals("admin") || name.equals("cb"))) {
                return false;
            }
        }

        // G) Common command-output phrasing (catch-all for servers without tags/indicators)
        if (sl.startsWith("set ") || sl.startsWith("gave ") || sl.startsWith("summoned ") ||
            sl.startsWith("teleported ") || sl.startsWith("filled ") || sl.startsWith("cleared ") ||
            sl.startsWith("replaced ") || sl.startsWith("toggled ") || sl.startsWith("saved the game") ||
            sl.startsWith("set the time") || sl.startsWith("set time ")) {
            return true;
        }

        // H) Default: unsigned, non-game, not chat-pattern -> treat as command
        return true;
    }

    public static boolean isGameMessage(Text message) {
        String s = message.getString();
        if (s == null) return false;
        String sl = s.toLowerCase(Locale.ROOT);
        if (sl.contains("joined the game") || sl.contains("left the game")) return true;
        if (sl.contains("advancement") || sl.contains("made the advancement") || sl.contains("has completed the challenge")) return true;
        if (sl.contains("was slain by") || sl.contains("was shot by") || sl.contains("fell from a high place") ||
            sl.contains("tried to swim in lava") || sl.contains("drowned") || sl.contains("was pricked to death") ||
            sl.contains("burned to death") || sl.contains("blew up") || sl.contains("was blown up")) return true;
        return false;
    }

    private static boolean looksLikeCommandTag(String sl) {
        if (sl.contains("[@]") || sl.startsWith("@")) return true;
        if (!sl.startsWith("[")) return false;
        int end = sl.indexOf(']');
        if (end <= 1 || end > 24) return false;
        String tag = sl.substring(1, end).trim();
        String t = tag.toLowerCase(Locale.ROOT);
        return t.equals("@") || t.equals("server") || t.equals("console") || t.equals("rcon")
                || t.equals("command") || t.equals("command block") || t.equals("commandblock")
                || t.equals("cb") || t.equals("system") || t.equals("ops") || t.equals("admin");
    }
}