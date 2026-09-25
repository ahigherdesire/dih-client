package baritone.acquire.model;

/**
 * Breaking {@code block} drops {@code output}.
 *
 * @param dropsPerBlock expected count without Fortune (averaged over random counts and chances)
 * @param needsSilkTouch the drop only happens with Silk Touch (the planner skips these for now)
 */
public record MineSource(String block, String output, double dropsPerBlock, ToolReq tool, boolean needsSilkTouch)
        implements Source {
    @Override
    public double outputPerAction() {
        return dropsPerBlock;
    }
}
