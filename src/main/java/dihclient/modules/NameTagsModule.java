package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;

public final class NameTagsModule extends Module {
    public NameTagsModule() {
        super("nametags", "Nametags", ModuleCategory.RENDER, "Labels above entities.");
        add(new BoolSetting("players", "Players", true).group("General").description("Tag players"));
        add(new BoolSetting("mobs", "Mobs", false).group("General").description("Tag mobs"));
        add(new BoolSetting("items", "Items", true).group("General").description("Tag dropped items"));
        add(new BoolSetting("self", "Self", false).group("General").description("Tag yourself in third person"));
        add(new BoolSetting("ignore-invisible", "Ignore Invisible", false).group("General")
            .description("Skips invisible entities"));

        add(new BoolSetting("show-health", "Show Health", true).group("Info").description("Show health"));
        add(new ChoiceSetting("health-format", "Health Format", "HP", "HP", "Hearts", "Percent").group("Info")
            .description("Health as points, hearts or percent").visibleWhen(() -> bool("show-health")));
        add(new BoolSetting("health-color", "Health Color", false).group("Info")
            .description("Colour health from green to red").visibleWhen(() -> bool("show-health")));
        add(new BoolSetting("show-distance", "Show Distance", false).group("Info").description("Show distance"));
        add(new BoolSetting("show-ping", "Show Ping", false).group("Info").description("Show players' ping"));
        add(new BoolSetting("show-gamemode", "Show Game Mode", false).group("Info")
            .description("Show players' game mode, such as [C] for creative"));

        add(new BoolSetting("equipment", "Equipment", true).group("Equipment")
            .description("Show armour and held items above the tag"));
        add(new BoolSetting("equipment-enchants", "Enchantments", true).group("Equipment")
            .description("Short enchantment tags over each item")
            .visibleWhen(() -> bool("equipment")));
        add(new IntSetting("max-enchants", "Max Enchantments", ModuleNameTagRenderer.MAX_ENCHANT_LINES, 1, 10, 1)
            .group("Equipment").description("Most enchantment tags shown per item")
            .visibleWhen(() -> bool("equipment") && bool("equipment-enchants")));
        add(new BoolSetting("equipment-durability", "Durability", true).group("Equipment")
            .description("Durability bars and remaining uses")
            .visibleWhen(() -> bool("equipment")));

        add(new DoubleSetting("scale", "Scale", 1.0, 0.5, 4.0, 0.05).group("Display").description("Label size"));
        add(new BoolSetting("distance-scale", "Distance Scaling", true).group("Display").description("Shrink with distance"));
        add(new DoubleSetting("max-distance", "Max Distance", 64.0, 0.0, 256.0, 1.0).group("Display").description("0 = unlimited"));
        add(new DoubleSetting("y-offset", "Height", 0.45, 0.0, 2.0, 0.05).group("Display")
            .description("How far above the head the tag sits"));
        add(new BoolSetting("background", "Background", true).group("Display").description("Dark box behind the text"));
        add(new ColorSetting("background-color", "Background Color", ModuleNameTagRenderer.BG_COLOR).group("Display")
            .description("Colour of the box").visibleWhen(() -> bool("background")));
        add(new BoolSetting("text-shadow", "Text Shadow", true).group("Display").description("Shadow under the text"));
        add(new BoolSetting("friend-color", "Friend Color", true).group("Display")
            .description("Writes friends' names in the Teams friend colour"));

        add(new BoolSetting("group-items", "Group Items", true).group("Items")
            .description("Merge nearby items")
            .visibleWhen(() -> bool("items")));
        add(new DoubleSetting("group-radius", "Group Radius", 5.0, 0.5, 16.0, 0.5).group("Items")
            .description("Item merge radius")
            .visibleWhen(() -> bool("items") && bool("group-items")));
        add(new IntSetting("max-item-lines", "Max Lines", 6, 1, 20, 1).group("Items")
            .description("Most item kinds listed in one tag")
            .visibleWhen(() -> bool("items")));
    }
}
