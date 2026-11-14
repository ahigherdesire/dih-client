package baritone.ai.director;

/**
 * The director without a model: a rule table turns common objectives into fixed plans. It can't reflect, so a step
 * that fails pauses the run and says why; the player fixes it and resumes, or adds a key for the smart AI.
 */
public final class BasicDirector extends RunLoop {

    private final BasicRules rules;
    private Plan plan;

    public BasicDirector(BasicRules rules, DirectorHost host, DirectorLimits limits) {
        super(host, limits);
        this.rules = rules;
    }

    @Override
    public void start(String objective) {
        this.plan = this.rules.planFor(objective);
        if (this.plan == null) {
            failAtStart(objective, BasicRules.UNKNOWN);
            return;
        }
        super.start(objective);
    }

    @Override
    Plan makePlan(PlanRequest request) throws PlanException {
        if (request.trouble() != null || this.plan == null) {
            throw new PlanException("Basic mode can't plan around that. Add an AI key for the smart AI (.ai setup).");
        }
        return this.plan;
    }

    @Override
    boolean canReplan() {
        return false;
    }

    @Override
    boolean basic() {
        return true;
    }

    @Override
    String intro(Plan plan) {
        return "Basic mode (simpler plans; add an AI key for the smart AI): " + checklist(plan);
    }
}
