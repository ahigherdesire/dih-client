package dihclient.script;

/** Scripts written to the scripts folder the first time it is created. */
final class ExampleScripts {
    private ExampleScripts() {
    }

    static final String HELLO = """
        // hello.dscript — the smallest useful script.
        // Run it with:  .scripts run hello
        // Stop it with: .scripts stop hello

        function main() {
            print("Hello, " + playerName() + "! You have " + health() + " health.");
            repeat (3) {
                print("tick tock");
                waitSeconds(1);
            }
        }
        """;

    static final String GOLD_MINER = """
        // gold_miner.dscript
        // Mines gold with Baritone. Goes /home when health is low, a player shows up or the
        // inventory is full; at home it stores the raw gold in the nearest chest, eats, then
        // takes /rtpmenu -> Overworld -> Badlands back out and starts again.
        //
        // Run:  .scripts run gold_miner        Stop: .scripts stop gold_miner

        const ORE           = "gold_ore";
        const LOW_HEALTH    = 5;      // 20 = full, 2 per heart
        const SAFE_RADIUS   = 48;     // blocks
        const HOME_COMMAND  = "/home";
        const KEEP_RAW_GOLD = 27;     // raw gold to take back out of the chest

        function main() {
            while (true) {
                let reason = mineUntilDanger();
                print("Heading home: " + reason);
                goHome();
                restock();
                travelToBadlands();
            }
        }

        // Mines until something makes it unsafe, then returns why it stopped.
        function mineUntilDanger() {
            baritone("mine " + ORE);
            while (true) {
                if (health() <= LOW_HEALTH) { return "low health"; }
                if (playerNearby(SAFE_RADIUS)) { return "player nearby (" + nearestPlayer(SAFE_RADIUS) + ")"; }
                if (inventoryFull()) { return "inventory full"; }
                wait(5);
            }
        }

        function goHome() {
            stopBaritone();
            command(HOME_COMMAND);
            waitSeconds(5);   // teleport warm-up
        }

        function restock() {
            // walk to the chest if it is not already within reach
            if (findBlock("chest", 5) == null) {
                goto("chest");
            }
            if (openNearest("chest")) {
                let stored = deposit("raw_gold");
                print("Stored " + stored + " raw gold.");
                withdraw("raw_gold", KEEP_RAW_GOLD);
                closeGui();
            } else {
                print("Could not open a chest — skipping storage.");
            }
            eatUntilFull();
            // wait for health to regenerate before going back out
            while (health() < maxHealth() && food() >= 18) {
                waitSeconds(1);
            }
        }

        function travelToBadlands() {
            command("/rtpmenu");
            if (!waitForItem("Overworld", 100)) { error("The /rtpmenu GUI did not open"); }
            clickItem("Overworld");
            if (!waitForItem("Badlands", 100)) { error("No Badlands option in the RTP menu"); }
            clickItem("Badlands");
            waitSeconds(8);   // let the teleport land and chunks load
        }

        // Runs whenever the script ends (stopped, finished or crashed).
        function onStop() {
            stopBaritone();
        }
        """;
}
