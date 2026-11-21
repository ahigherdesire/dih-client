package dihclient.script;

import dihclient.script.lang.Checker;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptApiCatalogueTest {
    @Test void proposedApiScriptChecksAgainstTheActualRunnerLibrary() {
        var problems = Checker.check("""
            function monitor(snapshot) { print(snapshot); }
            function main() {
                inventoryItem("offhand"); inventoryCount(1); durability("offhand");
                maxDurability(1); hasEnchantment(1, "mending");
                swapInventory(1, "offhand"); moveInventory(1, 2, 3); dropInventory(2, 1);
                cursorItem(); cursorCount(); usingItem(); eat("offhand");
                yaw(); pitch(); look(0, 0, true); attackCooldown(); attack();
                useItem("mainhand"); interactBlock(0, 64, 0, "up", "mainhand"); setKey("jump", false);
                alive(); onGround(); inWater(); submerged(); inLava(); onFire(); air(); fallDistance();
                velocity(); biome(); groundDistance(32); nearestThreat(32); blockInfo(0, 64, 0); crosshairTarget();
                slotLore(1); clickSlotAction(1, "swap", 0);
                modLoaded("meteor-client"); moduleEnabled("meteor", "auto-eat");
                setModuleEnabled("meteor", "auto-log", false);
                readText("instance", "settings.json"); writeTextAtomic("instance", "settings.json", "{}");
                appendText("shared", "log.txt", "entry"); listFiles("instance", "");
                let lock = tryLock("shared", "sword", 180000); renewLock(lock); releaseLock(lock); lockOwner("shared", "sword");
                let config = jsonParse("{}"); config = jsonParse("{bad", config); jsonStringify(config);
                httpRequest("GET", "https://example.com", config, null, 1000); secret("discord");
                registerClientCommand("botstatus", "monitor"); on("death", "monitor"); on("tick", "monitor");
                on("damage", "monitor"); on("join", "monitor"); on("disconnect", "monitor"); on("chat", "monitor");
                disconnect("test"); reconnectLastServer(); respawn(); setPauseOnLostFocus(false);
                armAutoLogResume(false); supervise(true); startScript("miner"); stopScript("miner"); scriptRunning("miner");
            }
            """, ScriptManager.library());
        assertTrue(problems.isEmpty(), problems.toString());
    }
}
