/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Stores the world seed for the server you're on, so {@code #seedmap}, {@code #structure} and
 * {@code #where} work on multiplayer. Seeds are kept per server (shared with OreSim) and checked
 * against the seed hash the server sends at login, so a wrong seed can't silently draw a wrong map.
 *
 * <pre>
 *   #seedinput -4172144997902289642   numeric seed
 *   #seedinput glacier                text seed, converted exactly like the world-creation screen
 *   #seedinput &lt;seed&gt; force          store even though it doesn't match this server
 *   #seedinput                        show the seed for this server and whether it matches
 *   #seedinput check                  same, explicitly
 *   #seedinput clear                  forget this server's seed
 * </pre>
 */
public class SeedInputCommand extends Command {

    public SeedInputCommand(IBaritone baritone) {
        super(baritone, "seedinput", "seed");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        List<String> words = new ArrayList<>();
        while (args.hasAny()) words.add(args.getString());

        if (Minecraft.getInstance().getSingleplayerServer() != null) {
            logDirect("Singleplayer: the world's seed is read automatically — nothing to enter.", ChatFormatting.GRAY);
            if (words.isEmpty()) return;
        }

        if (words.isEmpty() || (words.size() == 1 && words.get(0).equalsIgnoreCase("check"))) {
            report();
            return;
        }

        if (words.size() == 1 && words.get(0).equalsIgnoreCase("clear")) {
            if (ClientStructureFinder.hasSeed()) {
                long old = ClientStructureFinder.getSeed();
                ClientStructureFinder.clearSeed();
                logDirect("Forgot seed " + old + " for " + ClientStructureFinder.scope() + ".");
            } else {
                logDirect("No seed was stored for " + ClientStructureFinder.scope() + ".");
            }
            return;
        }

        boolean force = words.size() > 1 && words.get(words.size() - 1).equalsIgnoreCase("force");
        if (force) words.remove(words.size() - 1);
        // Text seeds may contain spaces, exactly like the world-creation box.
        String raw = String.join(" ", words);
        OptionalLong parsed = ClientStructureFinder.parse(raw);
        if (parsed.isEmpty()) {
            logDirect("Enter a seed, e.g.  #seedinput -4172144997902289642", ChatFormatting.RED);
            return;
        }
        long seed = parsed.getAsLong();
        boolean numeric = raw.trim().matches("-?\\d+");
        String shown = numeric ? Long.toString(seed) : "\"" + raw.trim() + "\" → " + seed;

        switch (ClientStructureFinder.check(seed)) {
            case MATCH -> {
                ClientStructureFinder.setSeed(seed);
                logDirect("✔ Seed " + shown + " matches this server. Saved for " + ClientStructureFinder.scope() + ".",
                    ChatFormatting.GREEN);
                logDirect("Try  #seedmap  to see every structure on the JourneyMap map.", ChatFormatting.GRAY);
            }
            case MISMATCH -> {
                if (!force) {
                    logDirect("✘ Seed " + shown + " is NOT this server's seed (it doesn't match the seed hash "
                        + "the server sent). Not saved.", ChatFormatting.RED);
                    logDirect("Double-check it, or add  force  to save anyway (e.g. modded worldgen).", ChatFormatting.GRAY);
                    return;
                }
                ClientStructureFinder.setSeed(seed);
                logDirect("Saved " + shown + " for " + ClientStructureFinder.scope()
                    + " despite not matching this server (forced).", ChatFormatting.YELLOW);
            }
            case UNKNOWN -> {
                ClientStructureFinder.setSeed(seed);
                logDirect("Saved seed " + shown + " for " + ClientStructureFinder.scope()
                    + ". (Couldn't verify it — join the world to check with  #seedinput check.)", ChatFormatting.YELLOW);
            }
        }
    }

    private void report() {
        String where = ClientStructureFinder.scope();
        if (!ClientStructureFinder.hasSeed()) {
            logDirect("No seed stored for " + where + ".");
            logDirect("Usage:  #seedinput <seed>   (numbers or text, like the world-creation screen)", ChatFormatting.GRAY);
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("seedcrackerx")) {
                logDirect("Or install SeedCrackerX — its seed gets filled in here automatically.", ChatFormatting.GRAY);
            }
            return;
        }
        long seed = ClientStructureFinder.getSeed();
        switch (ClientStructureFinder.check(seed)) {
            case MATCH -> logDirect("✔ " + where + ": seed " + seed + " — verified against the server.", ChatFormatting.GREEN);
            case MISMATCH -> logDirect("✘ " + where + ": seed " + seed + " does NOT match this server. "
                + "Seed maps will be wrong — fix it with  #seedinput <seed>.", ChatFormatting.RED);
            case UNKNOWN -> logDirect(where + ": seed " + seed + " (not verifiable right now).");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String prefix = "";
            try { prefix = args.peekString().toLowerCase(Locale.ROOT); } catch (Exception ignored) {}
            final String p = prefix;
            return Stream.of("check", "clear").filter(s -> s.startsWith(p));
        }
        return Stream.of("force");
    }

    @Override
    public String getShortDesc() {
        return "Set / verify this server's world seed (for #seedmap, #structure, #where)";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "Stores the world seed for the server you're on, so #seedmap, #structure and",
            "#where work on multiplayer. Each server keeps its own seed (shared with OreSim).",
            "",
            "Seeds are verified: the server sends a hash of its real seed when you join,",
            "and a seed that doesn't match is refused (add 'force' to keep it anyway).",
            "",
            "Singleplayer needs nothing — the world's seed is read automatically.",
            "With SeedCrackerX installed the seed fills itself in (crack or database hit).",
            "",
            "Usage:",
            "> seedinput <seed>        - numbers or text (text converts like world creation)",
            "> seedinput <seed> force  - save even if it doesn't match this server",
            "> seedinput               - show this server's seed and whether it matches",
            "> seedinput check         - same",
            "> seedinput clear         - forget this server's seed"
        );
    }
}
