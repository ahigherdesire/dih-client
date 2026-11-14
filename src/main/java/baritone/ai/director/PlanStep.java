package baritone.ai.director;

import com.google.gson.JsonObject;

/** One tool call of a plan, with why it's there. */
public record PlanStep(String tool, JsonObject args, String reason) {

    /** "goto {"target":"1 2 3"}" */
    public String describe() {
        return this.args == null || this.args.isEmpty() ? this.tool : this.tool + " " + this.args;
    }
}
