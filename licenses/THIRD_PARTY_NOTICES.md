# Third-party notices

DIH Client as a whole is licensed under the GNU GPL v3 or later (see `LICENSE` / `LICENSE_DIH Client`).
It contains or is derived from the following works. License texts are in this folder.

## Source code derived from

| Project | Author | License | Text |
|---|---|---|---|
| [Autism Client](https://github.com/AutismDevelopment/Autism-Client) (the upstream this is a fork of, including its artwork such as the title-screen panorama) | Melonik, simeonvartik, AutismDevelopment contributors | GPL-3.0 | `LICENSE` |
| [Baritone](https://github.com/cabaletta/baritone) / MinecraftAI (`baritone.*` packages) | cabaletta and contributors | LGPL-3.0 | `LGPL-3.0.txt` + `LICENSE` |
| [Meteor Client](https://github.com/MeteorDevelopment/meteor-client) | MeteorDevelopment | GPL-3.0 | `LICENSE` |
| [Wurst7](https://github.com/Wurst-Imperium/Wurst7) | Wurst-Imperium | GPL-3.0 | `LICENSE` |
| [OpSec](https://github.com/aurickk/OpSec) | aurickk | GPL-3.0 | `LICENSE` |
| [ExploitPreventer](https://github.com/NikOverflow/ExploitPreventer) | Niklas S. | MIT | `MIT-ExploitPreventer.txt` |
| [Dupe Radar](https://github.com/ErneTalu/Dupe-Radar) | Erne | MIT | `MIT-Dupe-Radar.txt` |
| [Better Storage ESP](https://github.com/bestluaucoder/Xinyuan-Better-Storage-esp-1-addon) (idea and structure list only, no code) | bestluaucoder | — | — |

## Bundled libraries (nested in `META-INF/jars/`)

| Library | License | Text |
|---|---|---|
| [MixinExtras](https://github.com/LlamaLad7/MixinExtras) 0.5.4 | MIT | `MIT-MixinExtras.txt` |
| [JSVG](https://github.com/weisJ/jsvg) 2.1.0 | MIT | `MIT-JSVG.txt` |
| [Netty](https://netty.io) codec-socks, handler-proxy 4.1.118 | Apache-2.0 | `Apache-2.0.txt`, `NOTICE-Netty.txt` |
| [Eclipse Paho MQTT](https://github.com/eclipse-paho/paho.mqtt.java) 1.2.5 | EPL-2.0 or EDL-1.0 (used under EDL-1.0) | `EDL-1.0-Paho.txt` |
| [WaybackAuthLib](https://github.com/FlorianMichael/WaybackAuthLib) 1.1.0 | Apache-2.0 | `Apache-2.0.txt` |
| [nether-pathfinder](https://github.com/babbaj/nether-pathfinder) 1.4.1 | published by babbaj for use by Baritone, redistributed unmodified exactly as Baritone does; no separate license file is provided upstream | — |

## Fonts

| Font | License | Text |
|---|---|---|
| [Geist](https://github.com/vercel/geist-font) (UI font) | SIL OFL 1.1 | `OFL-Geist.txt` |

## Not bundled

JourneyMap's API is used only at compile time and is not included. Minecraft is not included; the
few classes under `net.minecraft.*` package names in this jar are DIH Client's own code.
