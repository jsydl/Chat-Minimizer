# Chat Minimizer

A small Fabric client mod for Minecraft **1.21.8** that lets you selectively hide chat:

- `/minimizechat true all` — hide **everything** until chat is opened.
- `/minimizechat true chat` — hide **only** player/game chat (command outputs still show).
- `/minimizechat true commands` — hide **only** command / command-block outputs.
- `/minimizechat false` — restore vanilla behavior.
- `/minimizechat backfill off|all|commands|chat` — control what buffered lines flush when you open chat.

Settings persist in `config/chatminimizer.json`.

**Source:** https://github.com/jsydl/Chat-Minimizer  
**Author:** jsydl  
**License:** MIT