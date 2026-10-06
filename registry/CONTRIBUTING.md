# Uploading an addon

Listed addons appear on the website's Addons page and in the client under **Addons → Browse Online**, where players
install them in two clicks. You add **one JSON file** by pull request; a maintainer reviews it, and once it is merged it
shows up in-game on the next Refresh (no client update needed).

## 1. Build your addon

```sh
python addon-templates/addon-toolkit.py setup --template minimal --output ../MyAddon
cd ../MyAddon && ./gradlew build
```

`minimal` is the one to start with; `advanced` shows every extension point. The jar lands in `build/libs/`.
Your `fabric.mod.json` must have a `dih` entrypoint and depend on `dih` (the templates already do).

Supporting several Minecraft versions? Build one jar per version (for example 26.2 and 26.3), each with the same
addon version.

> Ported from Autism Client? Rename `autismclient.*` imports to `dihclient.*`, `AutismAddon`/`AutismAddons` to
> `DihAddon`/`DihAddons`, and the `autism` entrypoint and dependency to `dih`. An `autism` jar will not load on DIH.

## 2. Publish the jars

Push your source to a **public GitHub repo** and attach the jar(s) to a **GitHub release**. The registry only accepts
jars that are release assets of the repo you list as `source`, so reviewers can read exactly what was built.

## 3. Create the entry

Run this from a checkout of `ahigherdesire/dih-client`, once per jar:

```sh
python registry/registry.py add \
  --jar ../MyAddon/build/libs/my-addon-1.0.0.jar \
  --source https://github.com/you/my-addon \
  --jar-url https://github.com/you/my-addon/releases/download/v1.0.0/my-addon-1.0.0.jar \
  --summary "One short line players see under the name" \
  --category hud
```

It reads the id, name, version, author, licence, description and Minecraft version from the jar, computes the size and
SHA-256, and writes `registry/addons/<id>.json`. Running it again for your 26.3 jar (same version) adds a second file
to the same listing. Then:

```sh
python registry/registry.py build      # refreshes website/addons.json
```

Open a pull request with `registry/addons/<id>.json` and `website/addons.json`. CI re-runs the same checks.

- **image** (optional, recommended): the picture on your card. Commit a **16:9 PNG or JPG** (1280×720 is ideal,
  max 2 MB and 2048 px a side) to your repo and pass `--image https://raw.githubusercontent.com/you/my-addon/main/card.png`
  (a release asset of the same repo works too). Without one, the card shows your initials on a coloured banner.
- **summary**: up to 70 characters, shown under the name in the list.
- **description**: up to 300 characters, shown in the details panel (taken from `fabric.mod.json`).
- **categories**: `basehunting hud utility automation combat movement render world macro library seed fun`.

## Updating an addon

Publish a new release, re-run `add` for each jar (a new version replaces the old files), run `build`, and open a
pull request. Players see **Update** in the browser.

## What the client checks

It installs a listing only if the jar's size and SHA-256 match the entry, its `fabric.mod.json` has the same `id` and a
`dih` entrypoint, there is a file for the player's Minecraft version, and the `apiVersion` is supported. Ids starting
with `dih-` are reserved for addons built into the client.

## What reviewers check

- The source repo is public and matches the jars (they are release assets of that repo).
- No obfuscation, no network calls the description does not mention, no reading of account tokens, files outside the
  game or config folders, or other mods' data.
- No bundled third-party code without a compatible licence.
- It builds against the current `ApiVersion` and loads next to the DIH jar.
- Nothing that steals data or hides itself from the user.

Listings can be removed at any time, for example if an addon is later found to be unsafe.
