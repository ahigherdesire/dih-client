package dihclient.script;
import java.util.List;
import java.text.Normalizer;
import java.util.Locale;
final class ScriptMenuMatching {
    private ScriptMenuMatching() { }
    static int score(String name,String id,List<?> lore,String query) {
        String target=normalize(query);
        if(target.isEmpty()) return 0;
        String shown=normalize(name);
        if(shown.equals(target)) return 4;
        if(shown.contains(target)) return 3;
        for(Object line:lore) if(normalize(String.valueOf(line)).contains(target)) return 2;
        String path=id.contains(":") ? id.substring(id.indexOf(':')+1) : id;
        return normalize(id).equals(target) || normalize(path).equals(target) ? 1 : 0;
    }
    private static String normalize(String text) {
        return Normalizer.normalize(text==null ? "" : text,Normalizer.Form.NFKC)
            .replaceAll("§.","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    }
}
