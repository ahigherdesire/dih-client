// The local player's movement input for this tick: SafeWalk and AntiAFK.
fn dih_movement(
    game: &crate::game::Game<'_>,
    forward: &mut f32,
    strafe: &mut f32,
    jump: &mut bool,
    sneak: &mut bool,
) {
    use crate::game::dih_client as dih;
    if !game.native_on(dih::ID) {
        return;
    }
    let player = game.native_player().map(|p| dih::Player {
        pos: [p.x, p.y, p.z],
        yaw: p.yaw,
        pitch: p.pitch,
    });
    // Whether anything solid overlaps a world box, from each block's
    // collision boxes (block-local) in the cells it covers.
    let collides = |b: &dih::Aabb| {
        let lo = |v: f64| v.floor() as i32;
        let hi = |v: f64| (v - 1.0e-7).floor() as i32;
        for x in lo(b[0])..=hi(b[3]) {
            for y in lo(b[1])..=hi(b[4]) {
                for z in lo(b[2])..=hi(b[5]) {
                    let (fx, fy, fz) = (f64::from(x), f64::from(y), f64::from(z));
                    for c in game.native_block_collision([x, y, z]) {
                        let world = [c[0] + fx, c[1] + fy, c[2] + fz, c[3] + fx, c[4] + fy, c[5] + fz];
                        if dih::intersects(b, &world) {
                            return true;
                        }
                    }
                }
            }
        }
        false
    };
    let mut input = dih::MoveInput {
        forward: *forward,
        strafe: *strafe,
        jump: *jump,
        sneak: *sneak,
    };
    let screen_open = !game.native_playing();
    dih::movement(&mut input, player, screen_open, &collides, |k| game.native_setting(dih::ID, k));
    *forward = input.forward;
    *strafe = input.strafe;
    *jump = input.jump;
    *sneak = input.sneak;
}
