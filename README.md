# Chat Minimizer

A small Fabric client mod for Minecraft **1.21.8+** that lets you selectively hide chat:

- `/minimizechat true all` — hide **everything** until chat is opened.
- `/minimizechat true chat` — hide **only** player chat / system messages.
- `/minimizechat true commands` — hide **only** command / command-block outputs.
- `/minimizechat false` — restore vanilla behavior.
- `/minimizechat backfill off|all|commands|chat` — control what buffered lines flush when you open chat.

Settings save in `config/chatminimizer.json`.

**Links:** https://modrinth.com/mod/chat-minimizer  
**Author:** jsydl  
**License:** MIT
