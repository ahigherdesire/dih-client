// DIH Client's row in Options > Mods, with its keys.
fn dih_listed(mods: &mut Vec<crate::game::native_hooks::NativeMod>) {
    use crate::game::dih_client as dih;
    mods.push(crate::game::native_hooks::NativeMod {
        id: dih::ID.into(),
        title: format!("{} {}", dih::NAME, dih::VERSION),
        description: dih::DESCRIPTION.into(),
        keys: dih::KEYS
            .iter()
            .map(|(name, label, default)| [(*name).into(), (*label).into(), (*default).into()])
            .collect(),
    });
}
