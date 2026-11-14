// Active Modules in the top right corner: one line per module that is on,
// right-aligned, widest first.
fn dih_hud(
    game: &mut crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    corners: &mut horizon_client::extras_hud::Corners,
) {
    use crate::game::dih_client as dih;
    use horizon_client::extras::Corner;
    if !game.native_on(dih::ID) || !game.native_corner_shown(Corner::TopRight) {
        return;
    }
    let game = &*game;
    let get = |k: &str| game.native_setting(dih::ID, k);
    let settings = dih::settings(get);
    if !settings.module_list {
        return;
    }
    let active = dih::active(get);
    if active.is_empty() {
        return;
    }
    let rows = dih::module_list::rows(&active, &|t| game.native_text_width(t));
    let width = rows.iter().map(|r| r.2).fold(0.0, f32::max);
    let height = rows.len() as f32 * dih::module_list::LINE_HEIGHT;
    let (x, y) = corners.place(Corner::TopRight, width, height);
    let phase = dih::module_list::phase(dih::now_ms());
    for (i, (id, name, w)) in rows.iter().enumerate() {
        let rgb = dih::module_list::color(settings.color_mode, id, i, phase);
        let at = [x + width - w, y + i as f32 * dih::module_list::LINE_HEIGHT];
        game.native_text(batch, name, at, rgb, true);
    }
}
