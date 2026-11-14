package dihclient.util;

import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.world.level.block.entity.SignBlockEntity;
//? if >=26.3 {
/*import net.minecraft.world.level.block.entity.SignTextSlot;
*///?}

/** Vanilla screens whose constructors differ between Minecraft 26.2 and 26.3. */
public final class DihScreens {

    private DihScreens() {
    }

    /** The options screen; {@code inGame} only matters on 26.2 (26.3 works it out itself). */
    public static OptionsScreen options(Screen parent, Options options, boolean inGame) {
        //? if >=26.3 {
        /*return new OptionsScreen(parent, options);
        *///?} else {
        return new OptionsScreen(parent, options, inGame);
        //?}
    }

    public static SignEditScreen signEdit(SignBlockEntity sign, boolean front, boolean filtered) {
        //? if >=26.3 {
        /*return new SignEditScreen(sign, front ? SignTextSlot.FRONT : SignTextSlot.BACK, filtered);
        *///?} else {
        return new SignEditScreen(sign, front, filtered);
        //?}
    }

    public static HangingSignEditScreen hangingSignEdit(SignBlockEntity sign, boolean front, boolean filtered) {
        //? if >=26.3 {
        /*return new HangingSignEditScreen(sign, front ? SignTextSlot.FRONT : SignTextSlot.BACK, filtered);
        *///?} else {
        return new HangingSignEditScreen(sign, front, filtered);
        //?}
    }
}
