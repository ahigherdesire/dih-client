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
        // Mines gold. Goes /home when health is low or a player comes near.
        // At home: store raw gold in the chest, take 27 back (1 per slot), eat, then
        // /rtpmenu -> Overworld -> Badlands and mine again.
        //
        // Run:  .scripts run gold_miner      Stop: .scripts stop gold_miner

        const ORE          = "gold_ore";
        const LOW_HEALTH   = 5;       // 20 = full health, 2 per heart
        const SAFE_RADIUS  = 48;      // how close a player can get (blocks)
        const KEEP_GOLD    = 27;      // raw gold to spread back into the inventory, 1 per slot

        function main() {
            while (true) {
                goldMining();
                atHome();
            }
        }

        // ---------------------------------------------------------------- mining

        function goldMining() {
            baritone("mine " + ORE);                    // command #mine gold_ore
            while (true) {
                if (health() <= LOW_HEALTH) {
                    print("Low health (" + health() + ") - going home");
                    goHome();
                    eatUntilHealthFull();               // eat until health = 20
                    return;
                }
                if (playerNearby(SAFE_RADIUS)) {
                    print("Player nearby: " + nearestPlayer(SAFE_RADIUS) + " - going home");
                    goHome();
                    return;
                }
                if (inventoryFull("raw_gold")) {
                    print("Inventory full - going home");
                    goHome();
                    return;
                }
                wait(5);                                // check 4 times a second
            }
        }

        function goHome() {
            stopBaritone();                             // stop command #mine gold_ore
            command("/home");
            waitSeconds(5);                             // teleport warm-up
        }

        // ---------------------------------------------------------------- at home

        function atHome() {
            // go to chest
            if (findBlock("chest", 5) == null) {
                goto("chest");
            }
            if (!openNearest("chest")) {
                error("No chest found near /home");
            }

            // fill the chest with raw gold
            let stored = deposit("raw_gold");
            print("Stored " + stored + " raw gold");

            // take 27 raw gold back, 1 in each empty slot
            let filled = fillSlots("raw_gold", KEEP_GOLD);
            print("Filled " + filled + " slots with raw gold");
            closeGui();

            // eat until health is full
            eatUntilHealthFull();

            // /rtpmenu -> Overworld -> Badlands
            command("/rtpmenu");
            if (!waitForItem("Overworld", 100)) { error("/rtpmenu did not open"); }
            clickItem("Overworld");
            if (!waitForItem("Badlands", 100)) { error("No Badlands option in the RTP menu"); }
            clickItem("Badlands");
            waitSeconds(8);                             // let the teleport land
            // main() loops back to goldMining()
        }

        function eatUntilHealthFull() {
            while (health() < maxHealth()) {
                if (food() < 20) {
                    if (!eat()) {
                        print("Out of food - waiting for health to regenerate");
                        waitSeconds(2);
                    }
                } else {
                    waitSeconds(1);                     // full hunger -> health regenerates
                }
            }
        }

        // runs whenever the script stops
        function onStop() {
            stopBaritone();
        }
        """;
}
