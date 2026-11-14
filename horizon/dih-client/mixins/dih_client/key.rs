// A key went down or up: Zoom's key, or a module's toggle key.
fn dih_key(game: &mut crate::game::Game<'_>, key: &str, pressed: bool) {
    use crate::game::dih_client as dih;
    // Turned off in Options > Mods: nothing until the next build leaves it out.
    if !game.native_on(dih::ID) {
        return;
    }
    let playing = game.native_playing();
    let base_fov = game.settings.fov as f32;
    for (name, _, default) in dih::KEYS {
        let bound = game.native_mod_key(dih::ID, name, default);
        if bound == "key.keyboard.unknown" || key != bound {
            continue;
        }
        if name == "zoom" {
            let g = &*game;
            dih::zoom_key(pressed, playing, base_fov, |k| g.native_setting(dih::ID, k));
        } else if pressed && playing {
            let toggled = {
                let g = &*game;
                dih::toggle_key(name, |k| g.native_setting(dih::ID, k))
            };
            if let Some((settings, line)) = toggled {
                for (k, v) in settings.stored() {
                    game.native_set_setting(dih::ID, k, &v);
                }
                game.native_chat(&line);
            }
        }
    }
}
