// DIH Client's settings page: one button per setting, each click cycles it.
fn dih_page(game: &mut crate::game::Game<'_>, mod_id: &str, buttons: &mut Vec<String>) {
    use crate::game::dih_client as dih;
    if mod_id != dih::ID {
        return;
    }
    let game = &*game;
    let settings = dih::settings(|k| game.native_setting(dih::ID, k));
    buttons.extend(settings.labels());
}
