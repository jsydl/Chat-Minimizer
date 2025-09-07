package com.example.chatminimizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ChatMinimizerState {
    public enum Mode { DISABLED, ALL, COMMANDS, CHAT }
    public enum BackfillMode { OFF, ALL, COMMANDS, CHAT }
    public enum Kind { COMMANDS, CHAT } // message category

    private static final int MAX_BUFFER = 400;

    private static Mode mode = Mode.DISABLED;
    private static BackfillMode backfillMode = BackfillMode.ALL;

    // config
    private static Path configPath = null;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void initConfig(Path path) {
        configPath = path;
        load();
        save(); // ensure file exists
    }

    private static void load() {
        try {
            if (configPath != null && Files.exists(configPath)) {
                String json = Files.readString(configPath);
                JsonObject o = GSON.fromJson(json, JsonObject.class);
                if (o != null) {
                    if (o.has("mode")) {
                        try { mode = Mode.valueOf(o.get("mode").getAsString()); } catch (Throwable ignored) {}
                    }
                    if (o.has("backfill")) {
                        try { backfillMode = BackfillMode.valueOf(o.get("backfill").getAsString()); } catch (Throwable ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void save() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("mode", mode.name());
            o.addProperty("backfill", backfillMode.name());
            Files.writeString(configPath, GSON.toJson(o));
        } catch (IOException ignored) {}
    }

    // ==== Buffering ====
    public record Buffered(Text message, MessageSignatureData signature, MessageIndicator indicator, Kind kind) {}
    private static final List<Buffered> buffer = new ArrayList<>();

    public static synchronized void bufferSuppressed(Text msg, MessageSignatureData sig, MessageIndicator ind, Kind kind) {
        if (buffer.size() >= MAX_BUFFER) {
            for (int i = 0; i < 50 && !buffer.isEmpty(); i++) buffer.remove(0);
        }
        buffer.add(new Buffered(msg, sig, ind, kind));
    }

    /** Return only the messages we should flush for the given backfill setting; keep the rest buffered. */
    public static synchronized List<Buffered> drainBufferedFor(BackfillMode backfill) {
        List<Buffered> out = new ArrayList<>();
        if (backfill == BackfillMode.OFF) return out;

        Iterator<Buffered> it = buffer.iterator();
        while (it.hasNext()) {
            Buffered b = it.next();
            boolean allow = switch (backfill) {
                case ALL -> true;
                case COMMANDS -> b.kind == Kind.COMMANDS;
                case CHAT -> b.kind == Kind.CHAT;
                case OFF -> false;
            };
            if (allow) {
                out.add(b);
                it.remove();
            }
        }
        return out;
    }

    // ==== Settings (persist on change) ====
    public static Mode getMode() { return mode; }
    public static void setMode(Mode m) { mode = m; save(); }

    public static BackfillMode getBackfillMode() { return backfillMode; }
    public static void setBackfillMode(BackfillMode b) { backfillMode = b; save(); }

    // timing for command-output heuristic
    private static long lastCommandAtMs = 0L;
    public static void markCommandSent() { lastCommandAtMs = System.currentTimeMillis(); }
    public static long getLastCommandMs() { return lastCommandAtMs; }
}