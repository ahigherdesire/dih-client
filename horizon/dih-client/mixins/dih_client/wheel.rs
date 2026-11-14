// While Zoom is on, the wheel zooms instead of moving the hotbar.
fn dih_wheel(game: &crate::game::Game<'_>, steps: f32, claimed: &mut bool) {
    use crate::game::dih_client as dih;
    if *claimed || !game.native_on(dih::ID) {
        return;
    }
    let base_fov = game.settings.fov as f32;
    if dih::zoom_wheel(steps, base_fov, |k| game.native_setting(dih::ID, k)) {
        *claimed = true;
    }
}
