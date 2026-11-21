package dihclient.script;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ScriptMenuMatchingTest {
    @Test void exactNameBeatsSubstringAndLore() {
        assertTrue(ScriptMenuMatching.score("Gold","minecraft:stone",List.of(),"Gold")>
            ScriptMenuMatching.score("Gold mine","minecraft:stone",List.of(),"Gold"));
        assertTrue(ScriptMenuMatching.score("Gold mine","minecraft:stone",List.of(),"Gold")>
            ScriptMenuMatching.score("Mine","minecraft:stone",List.of("Gold"),"Gold"));
        assertEquals(0,ScriptMenuMatching.score("Mine","minecraft:stone",List.of("Iron"),"Gold"));
    }
}
