package baritone.ai.director;

import baritone.ai.ItemRequest;
import baritone.ai.catalog.CatalogTools;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Basic mode's rule table: objective phrases to fixed plans. It covers getting an item, armour sets, going to a
 * structure, following a player and farming, and says so plainly for anything else rather than guessing.
 */
public final class BasicRules {

    public static final String UNKNOWN = "Basic mode can't do that yet: add an AI key for the smart AI (.ai setup).";

    private static final Pattern POLITE = Pattern.compile(
            "^(?:please |can you |could you |would you |i want you to |i need you to |i want |i need )+");
    private static final Pattern ARMOUR = Pattern.compile(
            "^(?:(?:get|make|craft) )?(?:me )?(?:a )?(?:full )?(?:set of )?(iron|diamond|netherite|golden|gold|chainmail|leather) armou?r(?: set)?$");
    private static final Pattern PLACE = Pattern.compile(
            "^(?:go to|goto|find|take me to|travel to|head to|walk to) (?:a |an |the )?(?:nearest |closest )?(.+)$");
    private static final Pattern FOLLOW = Pattern.compile("^follow ([A-Za-z0-9_]{1,16})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FARM = Pattern.compile("^farm(?: .*)?$");
    private static final Pattern BEAT = Pattern.compile(
            "^(?:beat|finish|complete|win) (?:the game|minecraft|the ender dragon|the dragon)(?: for me)?$"
                    + "|^(?:kill|defeat|slay) (?:the )?(?:ender )?dragon$");
    private static final Pattern GET = Pattern.compile(
            "^(?:get|gather|collect|acquire|make|craft|obtain|fetch|grab|mine|smelt) (?:me )?(.+)$");
    private static final Pattern ARTICLE = Pattern.compile("^(?:a|an|some|the) ");
    private static final List<String> PIECES = List.of("helmet", "chestplate", "leggings", "boots");

    private final Function<String, Optional<String>> resolveItem;

    /** {@code resolveItem} turns words ("oak log") into an item id, or empty. */
    public BasicRules(Function<String, Optional<String>> resolveItem) {
        this.resolveItem = resolveItem;
    }

    /** The plan for {@code objective}, or null when basic mode doesn't know it. */
    public Plan planFor(String objective) {
        String original = objective == null ? "" : objective.trim().replaceAll("[.!?]+$", "").replaceAll("\s+", " ");
        String text = POLITE.matcher(original.toLowerCase(Locale.ROOT)).replaceFirst("");
        String originalText = original.substring(original.length() - text.length());
        if (text.isEmpty()) return null;

        if (BEAT.matcher(text).matches()) {
            return new Plan(List.of(step("beat_stage", "action", "start", "the #beat campaign beats the game phase by phase")),
                    "Beat the game with #beat.");
        }

        Matcher armour = ARMOUR.matcher(text);
        if (armour.matches()) {
            String tier = armour.group(1).equals("gold") ? "golden" : armour.group(1);
            List<PlanStep> steps = new ArrayList<>();
            for (String piece : PIECES) {
                steps.add(acquire("minecraft:" + tier + "_" + piece, 1, "one piece of the set"));
            }
            return new Plan(steps, "Get " + tier + " armour piece by piece.");
        }

        Matcher place = PLACE.matcher(text);
        if (place.matches()) {
            String structure = structure(place.group(1));
            if (structure == null) return null;
            return new Plan(List.of(step("goto_structure", "structure", structure, "go there")), "Go to the nearest " + structure + ".");
        }

        Matcher follow = FOLLOW.matcher(originalText);
        if (follow.matches()) {
            return new Plan(List.of(step("follow_player", "player", follow.group(1), "asked to")), "Follow " + follow.group(1) + ".");
        }

        if (FARM.matcher(text).matches()) {
            return new Plan(List.of(new PlanStep("farm", new JsonObject(), "harvest and replant nearby crops")), "Farm nearby crops.");
        }

        Matcher get = GET.matcher(text);
        return itemPlan(get.matches() ? get.group(1) : text);
    }

    private Plan itemPlan(String words) {
        ItemRequest request = ItemRequest.fromText(words);
        if (!request.ok()) return null;
        String name = ARTICLE.matcher(request.item()).replaceFirst("");
        Optional<String> id = resolve(name);
        if (id.isEmpty()) return null;
        return new Plan(List.of(acquire(id.get(), request.count(), "asked for")), "Get " + request.count() + " " + name + ".");
    }

    private Optional<String> resolve(String name) {
        for (String candidate : List.of(name, name.replaceAll("es$", ""), name.replaceAll("s$", ""))) {
            if (candidate.isBlank()) continue;
            Optional<String> id = this.resolveItem.apply(candidate);
            if (id.isPresent()) return id;
        }
        return Optional.empty();
    }

    private static String structure(String words) {
        String id = words.trim().replace(' ', '_');
        if (id.equals("fortress")) id = "nether_fortress";
        if (id.equals("outpost")) id = "pillager_outpost";
        if (id.equals("temple") || id.equals("pyramid")) id = "desert_pyramid";
        if (id.equals("witch_hut")) id = "swamp_hut";
        return CatalogTools.structures().contains(id) ? id : null;
    }

    private static PlanStep acquire(String item, int count, String reason) {
        JsonObject args = new JsonObject();
        args.addProperty("item", item);
        args.addProperty("count", count);
        return new PlanStep("acquire", args, reason);
    }

    private static PlanStep step(String tool, String key, String value, String reason) {
        JsonObject args = new JsonObject();
        args.addProperty(key, value);
        return new PlanStep(tool, args, reason);
    }
}
