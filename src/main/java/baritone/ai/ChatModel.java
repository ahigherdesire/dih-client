package baritone.ai;
import com.google.gson.JsonArray;
import java.io.IOException;
public interface ChatModel { LlmClient.Reply chat(JsonArray messages, JsonArray tools) throws IOException; }
