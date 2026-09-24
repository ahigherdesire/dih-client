package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.Command;
import dihclient.modules.Module;
import dihclient.modules.ModuleOreSim;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihWaypoints;
import dihclient.util.oresim.DihOreSimEngine;
import dihclient.util.oresim.DihOreSimSeedInput;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;

public class OreSimCommand extends Command {

    private static final String G = "§7";
    private static final String W = "§f";
    private static final String R = "§c";
    private static final String Y = "§e";
    private static final String A = "§a";

    public OreSimCommand() {
        super("oresim", "Report why OreSim is or is not predicting.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            report();
            return SUCCESS;
        });
    }

    private static void report() {
        Module xray = ModuleRegistry.get("xray");
        if (xray == null) {
            DihClientMessaging.sendPrefixed(R + "Xray module not registered.");
            return;
        }

        DihClientMessaging.sendPrefixed(Y + "--- OreSim ---");
        DihClientMessaging.sendPrefixed(G + "enabled: " + W + xray.isEnabled()
            + G + "  mode: " + W + xray.value("mode")
            + G + "  style: " + W + xray.value("render-style"));

        Long seed = ModuleOreSim.debugSeed(xray);
        DihOreSimSeedInput.Status seedStatus = ModuleOreSim.seedInputStatus(xray);
        DihClientMessaging.sendPrefixed(G + "seed: "
            + (seedStatus == DihOreSimSeedInput.Status.INVALID ? R + "INVALID"
                : seed == null ? R + "NONE - enter a signed 64-bit value" : W + seed)
            + G + "  from: " + W + "the World Seed text field");
        DihClientMessaging.sendPrefixed(G + "saved scope: " + W
            + DihWaypoints.scopeKey(Minecraft.getInstance())
            + G + "; the same world/server scoping as waypoints. No world or server seed is read.");

        DihClientMessaging.sendPrefixed(G + "ore list entries: " + W + ModuleOreSim.debugSelectionSize(xray)
            + G + "  families: " + W + Integer.bitCount(ModuleOreSim.debugEnabledMask(xray)));

        String worldgen = DihOreSimEngine.failed() ? R + "FAILED"
            : DihOreSimEngine.loading() ? Y + DihOreSimEngine.status().name().toLowerCase(java.util.Locale.ROOT)
            : DihOreSimEngine.ready() ? A + "ready" : R + DihOreSimEngine.status().name().toLowerCase(java.util.Locale.ROOT);
        DihClientMessaging.sendPrefixed(G + "local Minecraft 26.2 worldgen: " + worldgen);
        if (DihOreSimEngine.failed()
            || DihOreSimEngine.status() == DihOreSimEngine.Status.UNVERIFIED_WORLDGEN) {
            DihClientMessaging.sendPrefixed(R + DihOreSimEngine.failureMessage());
        }

        DihClientMessaging.sendPrefixed(G + "chunks simulated: " + W + DihOreSimEngine.chunkCount()
            + G + "  positions: " + W + DihOreSimEngine.storedPositions());

        DihClientMessaging.sendPrefixed(G + "generation source: " + W
            + "local vanilla registries + terrain + carvers + biome decoration"
            + G + "; received server chunks are not inputs.");

        DihClientMessaging.sendPrefixed(G + "nearby real ore: " + W + ModuleOreSim.nearDiagnostics());
        DihClientMessaging.sendPrefixed(G + "matched = real ore within 5 blocks; touchingAir = of those,"
            + " exposed; drawn = also in line of sight.");
    }
}
