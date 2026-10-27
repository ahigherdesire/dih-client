# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
Minecraft players running DIH Client. On the website they download the client, browse modules and addons, and look up how to type a chat command while playing.

## Product Purpose
DIH Client is a Fabric client mod for Minecraft 26.2 focused on duping/debugging: control over the packets the server receives, a macro system, addon support, and an integrated Baritone pathfinder with an AI mode. The website gets players to download it and use it.

## Capabilities and Constraints
- Two chat command systems: client commands with the `.` prefix (changeable; `%` when Meteor is installed) and Baritone/AI commands with the `#` prefix.
- The website is static HTML/CSS/JS with no build step, served by a Cloudflare Worker (`website/worker`). The CSP in `website/_headers` allows scripts only from `'self'`, so pages cannot use inline scripts.
- Command reference source of truth: `COMMANDS.md`, written from `src/main/java/dihclient/commands/impl/` and `src/main/java/baritone/command/defaults/`.

## Brand Commitments
- Name: DIH Client. Existing site voice is blunt and direct (README: "not pretending that users are too stupid to touch advanced features").
- Existing assets: pixel "D" icon (`website/assets/icon.svg`), Discord and GitHub links in the site nav.

## Evidence on Hand
- `COMMANDS.md`: every command, from the source.
- `website/addons.json`: real addon registry.
- No testimonials or user numbers beyond the live download counter; do not invent any.
