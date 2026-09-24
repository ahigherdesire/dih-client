package com.example.addon;

import dihclient.api.DihAddon;
import dihclient.api.DihAddons;
import dihclient.api.ApiVersion;
import dihclient.api.macro.MacroActionEntry;

import com.example.addon.commands.ExampleCommand;
import com.example.addon.events.ExampleEvents;
import com.example.addon.hud.ExampleHud;
import com.example.addon.macro.ExampleHeightCondition;
import com.example.addon.macro.ExamplePresets;
import com.example.addon.macro.ExampleSayAction;
import com.example.addon.modules.ExampleModule;

// Addon entrypoint (the "dih" entrypoint in fabric.mod.json). Start small:
// register only ExampleModule first, launch the client, then add macro/actions/HUD/events as needed.
// Modules, actions, conditions and presets all auto-group under a category named after this addon.
public final class ExampleAddon extends DihAddon {
    public static final String ID = "dih-advanced-addon-template";

    @Override
    public int apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public void onInitialize() {
        DihAddons.modules().register(new ExampleModule());

        // Action: appears in the "add action" picker.
        DihAddons.macroActions().register(MacroActionEntry.local("say", ExampleSayAction::new)
            .picker("Say", "Send a chat message.")
            .build());

        // Condition: appears in the "add condition" picker (it waits).
        DihAddons.macroActions().register(MacroActionEntry.local("wait-height", ExampleHeightCondition::new)
            .condition("Wait Height", "Wait until the player is at or above a Y level.")
            .build());

        DihAddons.presets().register("Reach Height", "Wait for Y 80, then say.",
            ExamplePresets::reachHeightThenSay);

        DihAddons.commands().register(new ExampleCommand());
        DihAddons.hud().register(new ExampleHud());
        ExampleEvents.register();
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
