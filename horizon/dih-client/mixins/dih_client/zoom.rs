// Zoom's view, unless another mod's zoom came first. DIH eases the zoom
// itself, so Horizon's own smoothing stays off.
fn dih_zoom(game: &crate::game::Game<'_>, zoom: &mut Option<crate::game::native_hooks::NativeZoom>) {
    use crate::game::dih_client as dih;
    if zoom.is_some() || !game.native_on(dih::ID) {
        return;
    }
    let base_fov = game.settings.fov as f32;
    if let Some((factor, turn)) = dih::zoom_view(base_fov, |k| game.native_setting(dih::ID, k)) {
        *zoom = Some(crate::game::native_hooks::NativeZoom {
            factor,
            smooth: false,
            turn,
        });
    }
}
