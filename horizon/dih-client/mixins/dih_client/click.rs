// A click on DIH Client's settings page: change the setting and keep it.
fn dih_click(game: &mut crate::game::Game<'_>, mod_id: &str, button: usize) {
    use crate::game::dih_client as dih;
    if mod_id != dih::ID {
        return;
    }
    let mut settings = {
        let g = &*game;
        dih::settings(|k| g.native_setting(dih::ID, k))
    };
    settings.click(button);
    dih::set_settings(settings.clone());
    for (k, v) in settings.stored() {
        game.native_set_setting(dih::ID, k, &v);
    }
}
