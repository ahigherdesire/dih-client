// DIH Client for Horizon (the Rust rewrite of Java Edition 26.3): the first
// modules ported from the Java client. This file is both the Cargo library
// (its tests) and the module the bundle adds to the game as
// `game::dih_client`. It holds every piece of logic and state; the helpers
// beside it only read the game through Horizon's native hook points and call
// in here.
//
// Ported so far, from src/main/java/dihclient:
// - the module core: key toggles, saved settings, a chat line on each toggle
// - Active Modules, the HUD list of what is on (util/DihHudManager)
// - Zoom (modules/ZoomModule)
// - SafeWalk (modules/SafeWalkModule)
// - AntiAFK, its walk, jump and sneak actions (modules/AntiAfkModule)
//
// Module payloads cannot use inner attributes, `mod` files or `include!`, so
// everything is in this one file, in inline modules.
use std::cell::RefCell;
use std::time::Instant;

/// The mod's id: its settings folder and its keys' lines.
pub const ID: &str = "dihclient";
pub const NAME: &str = "DIH Client";
pub const VERSION: &str = "0.1.0";
pub const DESCRIPTION: &str =
    "DIH Client's Zoom, SafeWalk, AntiAFK and Active Modules list, ported to Horizon.";

/// The keys: name, label, default. `key.keyboard.unknown` is unbound.
pub const KEYS: [(&str, &str, &str); 3] = [
    ("zoom", "Zoom", "key.keyboard.c"),
    ("safe_walk", "Toggle SafeWalk", "key.keyboard.unknown"),
    ("anti_afk", "Toggle AntiAFK", "key.keyboard.unknown"),
];

/// Milliseconds since the mod first asked, for the transitions and holds.
pub fn now_ms() -> f64 {
    thread_local! {
        static START: Instant = Instant::now();
    }
    START.with(|s| s.elapsed().as_secs_f64() * 1000.0)
}

/// A small xorshift: the game's own random sources are not on the hook
/// points, and the mod needs no more than jitter.
#[derive(Clone, Copy, Debug)]
pub struct Rng(u64);

impl Rng {
    pub fn seeded(seed: u64) -> Self {
        Self(seed | 1)
    }
    fn from_clock() -> Self {
        let nanos = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map_or(0x9E37_79B9, |d| d.as_nanos() as u64);
        Self::seeded(nanos)
    }
    pub fn next_u64(&mut self) -> u64 {
        let mut x = self.0;
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        self.0 = x;
        x
    }
    /// `lo` to `hi`, both inclusive.
    pub fn between(&mut self, lo: u32, hi: u32) -> u32 {
        let (lo, hi) = (lo.min(hi), lo.max(hi));
        lo + (self.next_u64() % u64::from(hi - lo + 1)) as u32
    }
}

/// Every setting, as the settings page cycles them. Names follow the Java
/// modules' setting ids.
pub mod settings {
    pub const ZOOM_FOVS: [u8; 7] = [5, 10, 15, 20, 30, 45, 60];
    pub const ZOOM_DURATIONS: [u16; 6] = [0, 75, 150, 250, 400, 600];
    pub const EASINGS: [&str; 3] = ["Ease Out", "Linear", "Smooth"];
    pub const COLOR_MODES: [&str; 3] = ["Rainbow", "Flat", "Random"];
    pub const EDGE_DISTANCES: [f64; 8] = [0.15, 0.2, 0.3, 0.45, 0.6, 0.8, 1.0, 1.5];
    pub const LOOK_DOWNS: [u8; 6] = [0, 15, 25, 45, 60, 75];
    pub const IDLE_TIMES: [u16; 7] = [0, 10, 30, 60, 120, 300, 600];
    pub const AFK_MODES: [&str; 2] = ["Random", "All"];

    #[derive(Clone, Debug, PartialEq)]
    pub struct Settings {
        // Active Modules
        pub module_list: bool,
        pub color_mode: usize,
        // Zoom
        pub zoom_fov: u8,
        pub zoom_duration: u16,
        pub zoom_easing: usize,
        pub zoom_hold: bool,
        pub zoom_scroll: bool,
        pub zoom_scale_sensitivity: bool,
        // SafeWalk
        pub safe_walk: bool,
        pub edge_distance: f64,
        pub min_drop: u8,
        pub look_down: u8,
        pub pause_when_jumping: bool,
        pub only_backwards: bool,
        // AntiAFK
        pub anti_afk: bool,
        pub idle_time: u16,
        pub afk_mode: usize,
        pub afk_jump: bool,
        pub afk_walk: bool,
        pub afk_sneak: bool,
    }

    impl Default for Settings {
        // The Java modules' defaults. AntiAFK's own defaults are Rotate and
        // Swing, which Horizon's hook points can't do, so Jump is on here.
        fn default() -> Self {
            Self {
                module_list: true,
                color_mode: 0,
                zoom_fov: 30,
                zoom_duration: 150,
                zoom_easing: 0,
                zoom_hold: true,
                zoom_scroll: true,
                zoom_scale_sensitivity: true,
                safe_walk: false,
                edge_distance: 0.3,
                min_drop: 1,
                look_down: 25,
                pause_when_jumping: false,
                only_backwards: false,
                anti_afk: false,
                idle_time: 30,
                afk_mode: 0,
                afk_jump: true,
                afk_walk: false,
                afk_sneak: false,
            }
        }
    }

    fn on(b: bool) -> &'static str {
        if b { "ON" } else { "OFF" }
    }

    fn next<T: PartialEq + Copy>(all: &[T], now: T) -> T {
        let at = all.iter().position(|v| *v == now).map_or(0, |i| i + 1);
        all[at % all.len()]
    }

    impl Settings {
        /// From the stored values (`get` reads one key's file); defaults for
        /// the ones missing or unreadable.
        pub fn read(get: impl Fn(&str) -> Option<String>) -> Self {
            let d = Self::default();
            let flag = |key: &str, default: bool| {
                get(key).map_or(default, |v| match v.trim() {
                    "true" => true,
                    "false" => false,
                    _ => default,
                })
            };
            let num = |key: &str| get(key).and_then(|v| v.trim().parse::<f64>().ok());
            let index = |key: &str, len: usize, default: usize| {
                num(key).map_or(default, |v| if v >= 0.0 && (v as usize) < len { v as usize } else { default })
            };
            Self {
                module_list: flag("module_list", d.module_list),
                color_mode: index("color_mode", COLOR_MODES.len(), d.color_mode),
                zoom_fov: num("zoom_fov").map_or(d.zoom_fov, |v| v.clamp(1.0, 110.0) as u8),
                zoom_duration: num("zoom_duration").map_or(d.zoom_duration, |v| v.clamp(0.0, 1000.0) as u16),
                zoom_easing: index("zoom_easing", EASINGS.len(), d.zoom_easing),
                zoom_hold: flag("zoom_hold", d.zoom_hold),
                zoom_scroll: flag("zoom_scroll", d.zoom_scroll),
                zoom_scale_sensitivity: flag("zoom_scale_sensitivity", d.zoom_scale_sensitivity),
                safe_walk: flag("safe_walk", d.safe_walk),
                edge_distance: num("edge_distance").map_or(d.edge_distance, |v| v.clamp(0.15, 1.5)),
                min_drop: num("min_drop").map_or(d.min_drop, |v| v.clamp(1.0, 6.0) as u8),
                look_down: num("look_down").map_or(d.look_down, |v| v.clamp(0.0, 90.0) as u8),
                pause_when_jumping: flag("pause_when_jumping", d.pause_when_jumping),
                only_backwards: flag("only_backwards", d.only_backwards),
                anti_afk: flag("anti_afk", d.anti_afk),
                idle_time: num("idle_time").map_or(d.idle_time, |v| v.clamp(0.0, 600.0) as u16),
                afk_mode: index("afk_mode", AFK_MODES.len(), d.afk_mode),
                afk_jump: flag("afk_jump", d.afk_jump),
                afk_walk: flag("afk_walk", d.afk_walk),
                afk_sneak: flag("afk_sneak", d.afk_sneak),
            }
        }

        /// What to store, key by key.
        pub fn stored(&self) -> Vec<(&'static str, String)> {
            let b = |v: bool| v.to_string();
            vec![
                ("module_list", b(self.module_list)),
                ("color_mode", self.color_mode.to_string()),
                ("zoom_fov", self.zoom_fov.to_string()),
                ("zoom_duration", self.zoom_duration.to_string()),
                ("zoom_easing", self.zoom_easing.to_string()),
                ("zoom_hold", b(self.zoom_hold)),
                ("zoom_scroll", b(self.zoom_scroll)),
                ("zoom_scale_sensitivity", b(self.zoom_scale_sensitivity)),
                ("safe_walk", b(self.safe_walk)),
                ("edge_distance", self.edge_distance.to_string()),
                ("min_drop", self.min_drop.to_string()),
                ("look_down", self.look_down.to_string()),
                ("pause_when_jumping", b(self.pause_when_jumping)),
                ("only_backwards", b(self.only_backwards)),
                ("anti_afk", b(self.anti_afk)),
                ("idle_time", self.idle_time.to_string()),
                ("afk_mode", self.afk_mode.to_string()),
                ("afk_jump", b(self.afk_jump)),
                ("afk_walk", b(self.afk_walk)),
                ("afk_sneak", b(self.afk_sneak)),
            ]
        }

        /// The settings page's buttons, in `click`'s order.
        pub fn labels(&self) -> Vec<String> {
            vec![
                format!("Active Modules: {}", on(self.module_list)),
                format!("Active Modules colors: {}", COLOR_MODES[self.color_mode]),
                format!("Zoom FOV: {}°", self.zoom_fov),
                format!("Zoom transition: {} ms", self.zoom_duration),
                format!("Zoom easing: {}", EASINGS[self.zoom_easing]),
                format!("Zoom key: {}", if self.zoom_hold { "Hold" } else { "Toggle" }),
                format!("Zoom scroll: {}", on(self.zoom_scroll)),
                format!("Zoom scale sensitivity: {}", on(self.zoom_scale_sensitivity)),
                format!("SafeWalk: {}", on(self.safe_walk)),
                format!("SafeWalk edge distance: {:.2}", self.edge_distance),
                format!("SafeWalk min drop: {}", self.min_drop),
                format!("SafeWalk look down: {}°", self.look_down),
                format!("SafeWalk pause when jumping: {}", on(self.pause_when_jumping)),
                format!("SafeWalk only backwards: {}", on(self.only_backwards)),
                format!("AntiAFK: {}", on(self.anti_afk)),
                format!("AntiAFK idle time: {} s", self.idle_time),
                format!("AntiAFK mode: {}", AFK_MODES[self.afk_mode]),
                format!("AntiAFK jump: {}", on(self.afk_jump)),
                format!("AntiAFK walk: {}", on(self.afk_walk)),
                format!("AntiAFK sneak: {}", on(self.afk_sneak)),
            ]
        }

        /// A click on button `i` of `labels`.
        pub fn click(&mut self, i: usize) {
            match i {
                0 => self.module_list = !self.module_list,
                1 => self.color_mode = (self.color_mode + 1) % COLOR_MODES.len(),
                2 => self.zoom_fov = next(&ZOOM_FOVS, self.zoom_fov),
                3 => self.zoom_duration = next(&ZOOM_DURATIONS, self.zoom_duration),
                4 => self.zoom_easing = (self.zoom_easing + 1) % EASINGS.len(),
                5 => self.zoom_hold = !self.zoom_hold,
                6 => self.zoom_scroll = !self.zoom_scroll,
                7 => self.zoom_scale_sensitivity = !self.zoom_scale_sensitivity,
                8 => self.safe_walk = !self.safe_walk,
                9 => self.edge_distance = next(&EDGE_DISTANCES, self.edge_distance),
                10 => self.min_drop = self.min_drop % 6 + 1,
                11 => self.look_down = next(&LOOK_DOWNS, self.look_down),
                12 => self.pause_when_jumping = !self.pause_when_jumping,
                13 => self.only_backwards = !self.only_backwards,
                14 => self.anti_afk = !self.anti_afk,
                15 => self.idle_time = next(&IDLE_TIMES, self.idle_time),
                16 => self.afk_mode = (self.afk_mode + 1) % AFK_MODES.len(),
                17 => self.afk_jump = !self.afk_jump,
                18 => self.afk_walk = !self.afk_walk,
                19 => self.afk_sneak = !self.afk_sneak,
                _ => {}
            }
        }
    }
}

pub use settings::Settings;

/// A box in world coordinates: min x, y, z, max x, y, z.
pub type Aabb = [f64; 6];

/// Whether two boxes overlap (touching faces do not).
pub fn intersects(a: &Aabb, b: &Aabb) -> bool {
    a[0] < b[3] && a[3] > b[0] && a[1] < b[4] && a[4] > b[1] && a[2] < b[5] && a[5] > b[2]
}

/// The player's movement input for one tick, as Horizon's
/// `native_movement_input` hands it over.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct MoveInput {
    /// Positive forward, negative back.
    pub forward: f32,
    pub strafe: f32,
    pub jump: bool,
    pub sneak: bool,
}

/// The local player as the hook points show it.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Player {
    pub pos: [f64; 3],
    /// Degrees; 0 faces south, 90 west.
    pub yaw: f32,
    /// Degrees; positive looks down.
    pub pitch: f32,
}

/// Zoom: hold (or toggle) to zoom, eased in and out; the wheel zooms further
/// while it is on. ZoomModule.java's behaviour, on Horizon's `native_zoom`.
pub mod zoom {
    use super::Settings;

    #[derive(Clone, Copy, Debug, Default, PartialEq)]
    pub struct Zoom {
        pub active: bool,
        zooming_in: bool,
        from: f32,
        to: f32,
        /// When the current transition started; None before the first zoom.
        start: Option<f64>,
        scrolled: Option<f32>,
    }

    /// Ease-out quad of `elapsed` through a `duration` transition, 0 to 1.
    pub fn eased(elapsed: f32, duration: f32) -> f32 {
        let t = linear(elapsed, duration);
        1.0 - (1.0 - t) * (1.0 - t)
    }

    fn linear(elapsed: f32, duration: f32) -> f32 {
        if duration <= 0.0 { 1.0 } else { (elapsed / duration).clamp(0.0, 1.0) }
    }

    impl Zoom {
        fn progress(&self, s: &Settings, now: f64) -> f32 {
            let Some(start) = self.start else { return 1.0 };
            let elapsed = (now - start) as f32;
            let duration = f32::from(s.zoom_duration);
            let t = linear(elapsed, duration);
            match s.zoom_easing {
                1 => t,
                2 => t * t * (3.0 - 2.0 * t),
                _ => eased(elapsed, duration),
            }
        }

        /// The field of view shown now, in degrees of the base one.
        pub fn displayed(&self, s: &Settings, now: f64, base: f32) -> f32 {
            if self.start.is_none() {
                return base;
            }
            (self.from + (self.to - self.from) * self.progress(s, now)).max(1.0)
        }

        fn retarget(&mut self, s: &Settings, now: f64, base: f32, target: f32) {
            self.from = self.displayed(s, now, base);
            self.to = target;
            self.start = Some(now);
        }

        pub fn set_active(&mut self, on: bool, s: &Settings, now: f64, base: f32) {
            if on == self.active {
                return;
            }
            self.active = on;
            self.zooming_in = on;
            if on {
                self.scrolled = None;
                self.retarget(s, now, base, f32::from(s.zoom_fov));
            } else {
                self.retarget(s, now, base, base);
            }
        }

        /// The zoom key went down or up. Hold mode follows the key; toggle
        /// mode flips on each press. Presses only count while playing.
        pub fn key(&mut self, pressed: bool, playing: bool, s: &Settings, now: f64, base: f32) {
            if s.zoom_hold {
                if !pressed {
                    self.set_active(false, s, now, base);
                } else if playing {
                    self.set_active(true, s, now, base);
                }
            } else if pressed && playing {
                self.set_active(!self.active, s, now, base);
            }
        }

        /// Wheel steps: true when they changed the zoom (so not the hotbar).
        pub fn wheel(&mut self, steps: f32, s: &Settings, now: f64, base: f32) -> bool {
            if !self.active || !s.zoom_scroll || steps == 0.0 {
                return false;
            }
            const STEP: f32 = 3.0;
            let current = self.scrolled.unwrap_or(f32::from(s.zoom_fov));
            let next = (current - steps.signum() * STEP).clamp(1.0, base.max(1.0));
            self.scrolled = Some(next);
            self.retarget(s, now, base, next);
            true
        }

        /// What `native_zoom` should apply: (magnification, turning scale),
        /// None once zoomed out and settled.
        pub fn view(&self, s: &Settings, now: f64, base: f32) -> Option<(f32, f32)> {
            if !self.active && (self.start.is_none() || self.progress(s, now) >= 1.0) {
                return None;
            }
            let base = base.max(1.0);
            let shown = self.displayed(s, now, base);
            let factor = base / shown;
            let turn = if s.zoom_scale_sensitivity { shown / base } else { 1.0 };
            Some((factor, turn))
        }
    }
}

/// SafeWalk: sneaks at ledges. SafeWalkModule.java's probe: look far enough
/// down, stand on the ground, and find nothing under a point a little ahead.
pub mod safe_walk {
    use super::{Aabb, MoveInput, Player, Rng, Settings};

    const DROP_PROBE_DEPTH: f64 = 0.55;
    const PROBE_INSET: f64 = 0.05;
    /// The player's half width (0.6 / 2).
    const HALF_WIDTH: f64 = 0.3;
    const RELEASE_CLEAR_TICKS: u32 = 3;
    /// The Java module's hold band, 100 to 200 ms.
    const HOLD_MS: (u32, u32) = (100, 200);

    #[derive(Clone, Copy, Debug, Default)]
    pub struct SafeWalk {
        holding: bool,
        hold_until: f64,
        clear_ticks: u32,
        last_pos: Option<[f64; 3]>,
    }

    pub fn drop_probe_depth(min_drop: u8) -> f64 {
        DROP_PROBE_DEPTH + f64::from(min_drop.max(1) - 1)
    }

    /// Whether a box `y` to `y - 0.05` under the feet touches anything.
    pub fn on_ground(p: &Player, collides: &dyn Fn(&Aabb) -> bool) -> bool {
        let [x, y, z] = p.pos;
        let h = HALF_WIDTH - PROBE_INSET;
        collides(&[x - h, y - 0.05, z - h, x + h, y, z + h])
    }

    /// Whether there is a drop `distance` ahead of where the player is
    /// heading (or facing, standing still).
    pub fn drop_ahead(
        p: &Player,
        velocity: [f64; 2],
        distance: f64,
        min_drop: u8,
        collides: &dyn Fn(&Aabb) -> bool,
    ) -> bool {
        let [dx, dz] = if velocity[0] * velocity[0] + velocity[1] * velocity[1] > 1.0e-6 {
            let len = velocity[0].hypot(velocity[1]);
            [velocity[0] / len, velocity[1] / len]
        } else {
            let yaw = f64::from(p.yaw).to_radians();
            [-yaw.sin(), yaw.cos()]
        };
        let [x, y, z] = p.pos;
        let (px, pz) = (x + dx * distance, z + dz * distance);
        let h = (HALF_WIDTH - PROBE_INSET).max(0.05);
        let below = [px - h, y - drop_probe_depth(min_drop), pz - h, px + h, y, pz + h];
        !collides(&below)
    }

    impl SafeWalk {
        pub fn reset(&mut self) {
            *self = Self::default();
        }

        /// One tick of movement input: sets `sneak` at a ledge.
        pub fn tick(
            &mut self,
            s: &Settings,
            input: &mut MoveInput,
            player: Option<Player>,
            collides: &dyn Fn(&Aabb) -> bool,
            now: f64,
            rng: &mut Rng,
        ) {
            let Some(p) = player else {
                self.reset();
                return;
            };
            let velocity = self.last_pos.map_or([0.0, 0.0], |l| [p.pos[0] - l[0], p.pos[2] - l[2]]);
            self.last_pos = Some(p.pos);
            if !s.safe_walk {
                self.holding = false;
                return;
            }
            if input.sneak || !self.requested(s, input, &p, velocity, collides, now, rng) {
                return;
            }
            input.sneak = true;
        }

        fn requested(
            &mut self,
            s: &Settings,
            input: &MoveInput,
            p: &Player,
            velocity: [f64; 2],
            collides: &dyn Fn(&Aabb) -> bool,
            now: f64,
            rng: &mut Rng,
        ) -> bool {
            let paused = (s.pause_when_jumping && input.jump)
                || (s.only_backwards && !(input.forward < 0.0));
            if paused {
                self.holding = false;
                return false;
            }
            let at_edge = p.pitch >= f32::from(s.look_down)
                && on_ground(p, collides)
                && drop_ahead(p, velocity, s.edge_distance.max(0.15), s.min_drop, collides);
            self.clear_ticks = if at_edge { 0 } else { self.clear_ticks + 1 };
            if self.holding && now < self.hold_until {
                return true;
            }
            if at_edge {
                self.holding = true;
                self.hold_until = now + f64::from(rng.between(HOLD_MS.0, HOLD_MS.1));
                return true;
            }
            if self.holding && self.clear_ticks < RELEASE_CLEAR_TICKS {
                return true;
            }
            self.holding = false;
            false
        }
    }
}

/// AntiAFK: after a while without input, now and then jumps, steps or
/// crouches, and stops the moment the player touches a key or turns.
/// AntiAfkModule.java's walk, jump and sneak actions; its turn, swing,
/// hotbar and chat actions need writes Horizon's hook points don't have yet.
pub mod anti_afk {
    use super::{MoveInput, Player, Rng, Settings};

    const INTERVAL: (u32, u32) = (40, 100);
    const JUMP_TICKS: u32 = 2;
    const WALK_TICKS: (u32, u32) = (4, 11);
    const SNEAK_TICKS: (u32, u32) = (5, 14);

    #[derive(Clone, Copy, Debug, Default, PartialEq)]
    pub struct Held {
        pub jump: bool,
        pub walk: Option<(f32, f32)>,
        pub sneak: bool,
    }

    #[derive(Clone, Copy, Debug, Default)]
    pub struct AntiAfk {
        idle_ticks: u32,
        cooldown: u32,
        hold_ticks: u32,
        held: Held,
        last_look: Option<(f32, f32)>,
    }

    fn user_input(i: &MoveInput) -> bool {
        i.forward != 0.0 || i.strafe != 0.0 || i.jump || i.sneak
    }

    impl AntiAfk {
        pub fn reset(&mut self) {
            *self = Self::default();
        }

        #[cfg(test)]
        pub fn held(&self) -> Held {
            self.held
        }

        /// One tick of movement input. `screen_open` pauses it.
        pub fn tick(
            &mut self,
            s: &Settings,
            input: &mut MoveInput,
            player: Option<Player>,
            screen_open: bool,
            rng: &mut Rng,
        ) {
            let Some(p) = player else {
                self.reset();
                return;
            };
            if !s.anti_afk {
                self.reset();
                return;
            }
            let look = (p.yaw, p.pitch);
            let turned = self
                .last_look
                .is_some_and(|(y, x)| (look.0 - y).abs() > 1.0e-3 || (look.1 - x).abs() > 1.0e-3);
            self.last_look = Some(look);
            if turned || user_input(input) || screen_open {
                self.idle_ticks = 0;
                self.release();
                return;
            }
            self.idle_ticks = self.idle_ticks.saturating_add(1);
            if self.hold_ticks > 0 {
                self.hold_ticks -= 1;
                if self.hold_ticks == 0 {
                    self.release();
                }
            }
            let idle = self.idle_ticks >= u32::from(s.idle_time) * 20;
            if idle {
                self.cooldown = self.cooldown.saturating_sub(1);
                if self.cooldown == 0 {
                    self.cooldown = rng.between(INTERVAL.0, INTERVAL.1);
                    self.perform(s, rng);
                }
            }
            if self.hold_ticks > 0 {
                let h = self.held;
                input.jump |= h.jump;
                input.sneak |= h.sneak;
                if let Some((f, st)) = h.walk {
                    input.forward = f;
                    input.strafe = st;
                }
            }
        }

        fn perform(&mut self, s: &Settings, rng: &mut Rng) {
            let mut actions: Vec<u8> = Vec::new();
            if s.afk_jump {
                actions.push(0);
            }
            if s.afk_walk {
                actions.push(1);
            }
            if s.afk_sneak {
                actions.push(2);
            }
            if actions.is_empty() {
                return;
            }
            self.release();
            let chosen: Vec<u8> = if s.afk_mode == 1 {
                actions
            } else {
                vec![actions[rng.between(0, actions.len() as u32 - 1) as usize]]
            };
            for a in chosen {
                match a {
                    0 => {
                        self.held.jump = true;
                        self.hold_ticks = self.hold_ticks.max(JUMP_TICKS);
                    }
                    1 => {
                        let dirs = [(1.0, 0.0), (-1.0, 0.0), (0.0, 1.0), (0.0, -1.0)];
                        self.held.walk = Some(dirs[rng.between(0, 3) as usize]);
                        self.hold_ticks = self.hold_ticks.max(rng.between(WALK_TICKS.0, WALK_TICKS.1));
                    }
                    _ => {
                        self.held.sneak = true;
                        self.hold_ticks = self.hold_ticks.max(rng.between(SNEAK_TICKS.0, SNEAK_TICKS.1));
                    }
                }
            }
        }

        fn release(&mut self) {
            self.held = Held::default();
            self.hold_ticks = 0;
        }
    }
}

/// Active Modules: the HUD list of what is on, widest first, in DIH's
/// soft rainbow (DihHudManager.activeModuleLines).
pub mod module_list {
    pub const LINE_HEIGHT: f32 = 10.0;
    const RAINBOW_SPREAD: f32 = 0.035;
    const RAINBOW_SATURATION: f32 = 0.35;
    const FLAT: u32 = 0xFF3B3B;
    const VALUE_COLOR: u32 = 0xF3ECE7;

    /// `java.awt.Color.HSBtoRGB`, as 0xRRGGBB.
    pub fn hsb_to_rgb(hue: f32, saturation: f32, brightness: f32) -> u32 {
        let channel = |v: f32| ((v * 255.0 + 0.5) as u32).min(255);
        if saturation == 0.0 {
            let c = channel(brightness);
            return c << 16 | c << 8 | c;
        }
        let h = (hue - hue.floor()) * 6.0;
        let f = h - h.floor();
        let p = brightness * (1.0 - saturation);
        let q = brightness * (1.0 - saturation * f);
        let t = brightness * (1.0 - saturation * (1.0 - f));
        let (r, g, b) = match h as u32 {
            0 => (brightness, t, p),
            1 => (q, brightness, p),
            2 => (p, brightness, t),
            3 => (p, q, brightness),
            4 => (t, p, brightness),
            _ => (brightness, p, q),
        };
        channel(r) << 16 | channel(g) << 8 | channel(b)
    }

    fn lerp(a: u32, b: u32, t: f32) -> u32 {
        let mix = |s: u32| {
            let (x, y) = (((a >> s) & 0xFF) as f32, ((b >> s) & 0xFF) as f32);
            ((x + (y - x) * t).round() as u32) << s
        };
        mix(16) | mix(8) | mix(0)
    }

    fn hash(text: &str) -> u32 {
        // Java's String.hashCode, so Random picks the Java client's colours.
        text.encode_utf16().fold(0i32, |h, c| h.wrapping_mul(31).wrapping_add(i32::from(c))) as u32
    }

    /// The rainbow's phase at `now_ms`, one turn in about 19 seconds.
    pub fn phase(now_ms: f64) -> f32 {
        let period = (1000.0 / 0.0525) as u64;
        ((now_ms as u64 % period) as f64 / period as f64) as f32
    }

    /// Row `index`'s colour, 0xRRGGBB.
    pub fn color(mode: usize, id: &str, index: usize, phase: f32) -> u32 {
        match mode {
            1 => FLAT,
            2 => hash(id) & 0x00FF_FFFF,
            _ => {
                let hue = (phase + index as f32 * RAINBOW_SPREAD) % 1.0;
                lerp(hsb_to_rgb(hue, RAINBOW_SATURATION, 1.0), VALUE_COLOR, 0.55)
            }
        }
    }

    /// The names to list, widest first (then by name), with their ids.
    pub fn rows(on: &[(&'static str, &'static str)], width: &dyn Fn(&str) -> f32) -> Vec<(&'static str, &'static str, f32)> {
        let mut rows: Vec<_> = on.iter().map(|&(id, name)| (id, name, width(name))).collect();
        rows.sort_by(|a, b| b.2.total_cmp(&a.2).then_with(|| a.1.to_lowercase().cmp(&b.1.to_lowercase())));
        rows
    }
}

#[derive(Default)]
struct State {
    settings: Option<Settings>,
    zoom: zoom::Zoom,
    safe_walk: safe_walk::SafeWalk,
    anti_afk: anti_afk::AntiAfk,
    rng: Option<Rng>,
}

thread_local! {
    static STATE: RefCell<State> = RefCell::new(State::default());
}

fn with<R>(get: impl Fn(&str) -> Option<String>, f: impl FnOnce(&mut State, &Settings) -> R) -> R {
    STATE.with_borrow_mut(|s| {
        let settings = s.settings.get_or_insert_with(|| Settings::read(get)).clone();
        f(s, &settings)
    })
}

/// The settings, read once through `get`.
pub fn settings(get: impl Fn(&str) -> Option<String>) -> Settings {
    with(get, |_, s| s.clone())
}

/// Replaces the settings (the page or a key changed them), resetting a
/// module that was turned off.
pub fn set_settings(settings: Settings) {
    STATE.with_borrow_mut(|s| {
        if !settings.safe_walk {
            s.safe_walk.reset();
        }
        if !settings.anti_afk {
            s.anti_afk.reset();
        }
        s.settings = Some(settings);
    });
}

/// A module key went down while playing. Returns the settings to store and
/// the chat line to show, when it toggled a module.
pub fn toggle_key(name: &str, get: impl Fn(&str) -> Option<String>) -> Option<(Settings, String)> {
    let mut s = settings(get);
    let (label, on) = match name {
        "safe_walk" => {
            s.safe_walk = !s.safe_walk;
            ("SafeWalk", s.safe_walk)
        }
        "anti_afk" => {
            s.anti_afk = !s.anti_afk;
            ("AntiAFK", s.anti_afk)
        }
        _ => return None,
    };
    set_settings(s.clone());
    let line = format!("[DIH] {label} {}", if on { "enabled" } else { "disabled" });
    Some((s, line))
}

/// The zoom key went down or up.
pub fn zoom_key(pressed: bool, playing: bool, base_fov: f32, get: impl Fn(&str) -> Option<String>) {
    let now = now_ms();
    with(get, |st, s| st.zoom.key(pressed, playing, s, now, base_fov));
}

/// Wheel steps while playing: true when Zoom took them.
pub fn zoom_wheel(steps: f32, base_fov: f32, get: impl Fn(&str) -> Option<String>) -> bool {
    let now = now_ms();
    with(get, |st, s| st.zoom.wheel(steps, s, now, base_fov))
}

/// The zoom in effect: (magnification, turning scale).
pub fn zoom_view(base_fov: f32, get: impl Fn(&str) -> Option<String>) -> Option<(f32, f32)> {
    let now = now_ms();
    with(get, |st, s| st.zoom.view(s, now, base_fov))
}

/// One tick of the local player's movement input: SafeWalk, then AntiAFK.
pub fn movement(
    input: &mut MoveInput,
    player: Option<Player>,
    screen_open: bool,
    collides: &dyn Fn(&Aabb) -> bool,
    get: impl Fn(&str) -> Option<String>,
) {
    let now = now_ms();
    with(get, |st, s| {
        let mut rng = st.rng.unwrap_or_else(Rng::from_clock);
        st.anti_afk.tick(s, input, player, screen_open, &mut rng);
        st.safe_walk.tick(s, input, player, collides, now, &mut rng);
        st.rng = Some(rng);
    });
}

/// The modules that are on, (id, name), for Active Modules.
pub fn active(get: impl Fn(&str) -> Option<String>) -> Vec<(&'static str, &'static str)> {
    with(get, |st, s| {
        let mut on = Vec::new();
        if st.zoom.active {
            on.push(("zoom", "Zoom"));
        }
        if s.safe_walk {
            on.push(("safe-walk", "SafeWalk"));
        }
        if s.anti_afk {
            on.push(("anti-afk", "AntiAFK"));
        }
        on
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn stored<'a>(pairs: &'a [(&'a str, &'a str)]) -> impl Fn(&str) -> Option<String> + 'a {
        |key| pairs.iter().find(|p| p.0 == key).map(|p| p.1.to_owned())
    }

    #[test]
    fn settings_round_trip_and_defaults() {
        let d = Settings::read(|_| None);
        assert_eq!(d, Settings::default());
        let mut s = d.clone();
        for i in 0..20 {
            s.click(i);
        }
        let kept: Vec<(String, String)> = s.stored().into_iter().map(|(k, v)| (k.to_owned(), v)).collect();
        let back = Settings::read(|key| kept.iter().find(|p| p.0 == key).map(|p| p.1.clone()));
        assert_eq!(back, s);
        assert_eq!(s.labels().len(), 20);
    }

    #[test]
    fn bad_values_fall_back() {
        let s = Settings::read(stored(&[("zoom_fov", "nope"), ("color_mode", "9"), ("safe_walk", "maybe")]));
        assert_eq!(s.zoom_fov, 30);
        assert_eq!(s.color_mode, 0);
        assert!(!s.safe_walk);
    }

    #[test]
    fn cycles_wrap() {
        let mut s = Settings { zoom_fov: 60, min_drop: 6, ..Settings::default() };
        s.click(2);
        s.click(10);
        assert_eq!(s.zoom_fov, 5);
        assert_eq!(s.min_drop, 1);
    }

    #[test]
    fn zoom_eases_in_and_out() {
        let s = Settings::default();
        let mut z = zoom::Zoom::default();
        assert_eq!(z.view(&s, 0.0, 70.0), None);
        z.key(true, true, &s, 0.0, 70.0);
        let (mid, _) = z.view(&s, 75.0, 70.0).unwrap();
        assert!(mid > 1.0 && mid < 70.0 / 30.0);
        let (full, turn) = z.view(&s, 1000.0, 70.0).unwrap();
        assert!((full - 70.0 / 30.0).abs() < 1e-4);
        assert!((turn - 30.0 / 70.0).abs() < 1e-4);
        z.key(false, true, &s, 1000.0, 70.0);
        assert!(z.view(&s, 1050.0, 70.0).is_some());
        assert_eq!(z.view(&s, 2000.0, 70.0), None);
    }

    #[test]
    fn zoom_needs_play_to_start_and_toggle_mode_flips() {
        let mut s = Settings::default();
        let mut z = zoom::Zoom::default();
        z.key(true, false, &s, 0.0, 70.0);
        assert!(!z.active);
        s.zoom_hold = false;
        z.key(true, true, &s, 0.0, 70.0);
        z.key(false, true, &s, 10.0, 70.0);
        assert!(z.active);
        z.key(true, true, &s, 20.0, 70.0);
        assert!(!z.active);
    }

    #[test]
    fn zoom_wheel_narrows_and_stops_at_one() {
        let s = Settings { zoom_duration: 0, ..Settings::default() };
        let mut z = zoom::Zoom::default();
        assert!(!z.wheel(1.0, &s, 0.0, 70.0));
        z.key(true, true, &s, 0.0, 70.0);
        for _ in 0..20 {
            assert!(z.wheel(1.0, &s, 0.0, 70.0));
        }
        let (factor, _) = z.view(&s, 0.0, 70.0).unwrap();
        assert!((factor - 70.0).abs() < 1e-3);
        let off = Settings { zoom_scroll: false, ..s.clone() };
        assert!(!z.wheel(1.0, &off, 0.0, 70.0));
    }

    // A floor of full blocks under y < 64 for x < 0, open air beyond.
    fn ledge(b: &Aabb) -> bool {
        intersects(b, &[-100.0, 0.0, -100.0, 0.0, 64.0, 100.0])
    }

    fn at(x: f64, yaw: f32, pitch: f32) -> Player {
        Player { pos: [x, 64.0, 0.0], yaw, pitch }
    }

    #[test]
    fn safe_walk_sneaks_at_a_ledge_only() {
        let s = Settings { safe_walk: true, ..Settings::default() };
        let mut rng = Rng::seeded(7);
        // Facing east (+x is yaw -90), looking down, centred on the edge
        // (the 0.6-wide box still stands on the floor).
        let mut w = safe_walk::SafeWalk::default();
        let mut input = MoveInput { forward: 1.0, ..MoveInput::default() };
        w.tick(&s, &mut input, Some(at(0.0, -90.0, 60.0)), &ledge, 0.0, &mut rng);
        assert!(input.sneak);
        // Far from the edge: walks on.
        let mut w = safe_walk::SafeWalk::default();
        let mut input = MoveInput { forward: 1.0, ..MoveInput::default() };
        w.tick(&s, &mut input, Some(at(-5.0, -90.0, 60.0)), &ledge, 0.0, &mut rng);
        assert!(!input.sneak);
        // Looking ahead, not down: walks on.
        let mut w = safe_walk::SafeWalk::default();
        let mut input = MoveInput { forward: 1.0, ..MoveInput::default() };
        w.tick(&s, &mut input, Some(at(0.0, -90.0, 0.0)), &ledge, 0.0, &mut rng);
        assert!(!input.sneak);
        // Off: never.
        let off = Settings::default();
        let mut input = MoveInput { forward: 1.0, ..MoveInput::default() };
        w.tick(&off, &mut input, Some(at(0.0, -90.0, 60.0)), &ledge, 0.0, &mut rng);
        assert!(!input.sneak);
    }

    #[test]
    fn safe_walk_holds_then_lets_go() {
        let s = Settings { safe_walk: true, ..Settings::default() };
        let mut rng = Rng::seeded(3);
        let mut w = safe_walk::SafeWalk::default();
        let mut input = MoveInput::default();
        w.tick(&s, &mut input, Some(at(0.0, -90.0, 60.0)), &ledge, 0.0, &mut rng);
        assert!(input.sneak);
        // Back from the edge, still inside the hold.
        let mut input = MoveInput::default();
        w.tick(&s, &mut input, Some(at(-5.0, -90.0, 60.0)), &ledge, 50.0, &mut rng);
        assert!(input.sneak);
        // Long after: released once the flicker ticks pass.
        let mut last = true;
        for t in 0..5 {
            let mut input = MoveInput::default();
            w.tick(&s, &mut input, Some(at(-5.0, -90.0, 60.0)), &ledge, 1000.0 + f64::from(t), &mut rng);
            last = input.sneak;
        }
        assert!(!last);
    }

    #[test]
    fn safe_walk_min_drop_lets_you_step_down() {
        // One-block step: floor at 63 beyond x = 0.
        let step = |b: &Aabb| ledge(b) || intersects(b, &[0.0, 0.0, -100.0, 100.0, 63.0, 100.0]);
        let mut rng = Rng::seeded(5);
        let one = Settings { safe_walk: true, min_drop: 1, ..Settings::default() };
        let mut input = MoveInput::default();
        safe_walk::SafeWalk::default().tick(&one, &mut input, Some(at(0.0, -90.0, 60.0)), &step, 0.0, &mut rng);
        assert!(input.sneak);
        let two = Settings { min_drop: 2, ..one };
        let mut input = MoveInput::default();
        safe_walk::SafeWalk::default().tick(&two, &mut input, Some(at(0.0, -90.0, 60.0)), &step, 0.0, &mut rng);
        assert!(!input.sneak);
    }

    #[test]
    fn anti_afk_waits_acts_and_yields() {
        let s = Settings { anti_afk: true, idle_time: 1, afk_jump: true, ..Settings::default() };
        let mut rng = Rng::seeded(11);
        let mut a = anti_afk::AntiAfk::default();
        let p = Some(at(0.0, 0.0, 0.0));
        let mut jumped_at = None;
        for t in 0..200 {
            let mut input = MoveInput::default();
            a.tick(&s, &mut input, p, false, &mut rng);
            if input.jump && jumped_at.is_none() {
                jumped_at = Some(t);
            }
        }
        let t = jumped_at.expect("jumped once idle");
        assert!(t >= 19, "not before the idle time: {t}");
        // The player moves: it lets go and starts counting again.
        let mut input = MoveInput { forward: 1.0, ..MoveInput::default() };
        a.tick(&s, &mut input, p, false, &mut rng);
        assert_eq!(a.held(), anti_afk::Held::default());
        let mut input = MoveInput::default();
        a.tick(&s, &mut input, p, false, &mut rng);
        assert!(!input.jump);
    }

    #[test]
    fn anti_afk_pauses_in_screens_and_when_turned() {
        let s = Settings { anti_afk: true, idle_time: 0, ..Settings::default() };
        let mut rng = Rng::seeded(2);
        let mut a = anti_afk::AntiAfk::default();
        for _ in 0..300 {
            let mut input = MoveInput::default();
            a.tick(&s, &mut input, Some(at(0.0, 0.0, 0.0)), true, &mut rng);
            assert!(!input.jump);
        }
        // The screen loop left the look at yaw 0; every tick here turns.
        for i in 1..300 {
            let mut input = MoveInput::default();
            a.tick(&s, &mut input, Some(at(0.0, i as f32, 0.0)), false, &mut rng);
            assert!(!input.jump);
        }
    }

    #[test]
    fn module_list_sorts_widest_first() {
        let rows = module_list::rows(&[("zoom", "Zoom"), ("anti-afk", "AntiAFK"), ("safe-walk", "SafeWalk")], &|t| t.len() as f32);
        let names: Vec<_> = rows.iter().map(|r| r.1).collect();
        assert_eq!(names, ["SafeWalk", "AntiAFK", "Zoom"]);
    }

    #[test]
    fn hsb_matches_java() {
        assert_eq!(module_list::hsb_to_rgb(0.0, 1.0, 1.0), 0xFF0000);
        assert_eq!(module_list::hsb_to_rgb(1.0 / 3.0, 1.0, 1.0), 0x00FF00);
        assert_eq!(module_list::hsb_to_rgb(0.5, 0.0, 1.0), 0xFFFFFF);
        assert_eq!(module_list::color(1, "x", 0, 0.0), 0xFF3B3B);
    }

    #[test]
    fn toggle_keys_flip_modules() {
        STATE.with_borrow_mut(|s| *s = State::default());
        let (s, line) = toggle_key("safe_walk", |_| None).unwrap();
        assert!(s.safe_walk);
        assert_eq!(line, "[DIH] SafeWalk enabled");
        assert!(toggle_key("zoom", |_| None).is_none());
        assert_eq!(active(|_| None), [("safe-walk", "SafeWalk")]);
    }
}
