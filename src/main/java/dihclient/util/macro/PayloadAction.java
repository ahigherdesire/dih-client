package dihclient.util.macro;

import dihclient.modules.PackHideState;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihPayloadJsonSupport;
import dihclient.util.DihPayloadScriptExecutor;
import dihclient.util.DihPayloadSupport;
import dihclient.util.DihPayloadTemplate;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class PayloadAction implements MacroAction {
    public String channel = "minecraft:brand";
    public String payloadData = "";
    public String payloadJson = "";
    public String payloadClassName = "";
    public String javaSource = "";
    public boolean commandApiRecognized = false;
    public boolean commandApiOverride = false;
    public int commandApiValue = Integer.MAX_VALUE - 8;
    public String sourceDirection = "C2S";
    public String sourceProtocol = "";
    public String payloadDirection = "C2S";
    public String payloadPhase = "PLAY";
    public String payloadEncodingMode = "";
    public String payloadFields = "";
    public int payloadPacketId = -1;
    public String payloadProvenance = "userEdited";
    public boolean payloadScriptEnabled = false;
    private boolean enabled = true;

    @Override
    public void execute(Minecraft mc) {
        if (PackHideState.isHardLocked()) return;
        if (!enabled) return;
        if (mc == null || mc.getConnection() == null) {
            DihClientMessaging.sendPrefixed("§cCannot send payload while disconnected.");
            return;
        }

        try {
            MacroTemplate.Resolution channelResolution = MacroVariables.resolve(channel, mc);
            if (!channelResolution.success() || channelResolution.value().isBlank()) return;
            String resolvedChannel = channelResolution.value().trim();
            String resolvedPayloadData = payloadData;
            if (MacroTemplate.hasVariables(payloadData)) {
                MacroTemplate.Resolution payloadResolution = MacroVariables.resolve(payloadData, mc);
                if (!payloadResolution.success()) return;
                resolvedPayloadData = payloadResolution.value();
            }
            boolean hasRawPayload = resolvedPayloadData != null && !resolvedPayloadData.isBlank();
            boolean hasStructuredPayload = (payloadFields != null && !payloadFields.isBlank())
                || (payloadEncodingMode != null && !payloadEncodingMode.isBlank());
            boolean useJsonModel = !hasRawPayload
                && !hasStructuredPayload
                && payloadJson != null && !payloadJson.isBlank()
                && payloadClassName != null && !payloadClassName.isBlank();
            String targetChannel = resolvedChannel;
            String sendProtocol = payloadPhase == null || payloadPhase.isBlank()
                ? (sourceProtocol == null ? "" : sourceProtocol)
                : payloadPhase;
            byte[] rawBytes;
            if (hasStructuredPayload) {
                DihPayloadTemplate.Template template = DihPayloadTemplate.fromAction(this);
                DihPayloadTemplate.BuildResult built = template.build();
                if (!built.ok()) {
                    throw new IllegalArgumentException(String.join("; ", built.errors()));
                }
                if (!MacroTemplate.hasVariables(channel)) targetChannel = template.channel();
                rawBytes = built.bytes();
                if (sendProtocol.isBlank()) sendProtocol = template.phase().name();
            } else if (useJsonModel) {
                DihPayloadJsonSupport.EncodedPayload encoded = DihPayloadJsonSupport.encodeAction(this);
                if (!MacroTemplate.hasVariables(channel)) targetChannel = encoded.channel();
                rawBytes = encoded.bytes();
            } else {
                rawBytes = DihPayloadSupport.parsePayloadBytes(resolvedPayloadData);
            }
            if (!useJsonModel && !hasStructuredPayload && !hasRawPayload && DihPayloadSupport.isBrandChannel(targetChannel)) {
                rawBytes = DihPayloadSupport.encodeMinecraftStringPayload(
                    DihPayloadSupport.defaultBrandPayloadString());
            }
            if (commandApiRecognized && commandApiOverride) {
                rawBytes = DihPayloadSupport.withCommandApiValue(rawBytes, commandApiValue);
            }
            DihPayloadScriptExecutor.Context context = new DihPayloadScriptExecutor.Context(targetChannel, rawBytes,
                commandApiRecognized && commandApiOverride ? commandApiValue : null);
            DihPayloadScriptExecutor.ScriptResult result = DihPayloadScriptExecutor.execute(
                payloadScriptEnabled ? javaSource : "", context);
            if (DihPayloadSupport.sendPayload(result.channel(), result.bytes(), sendProtocol)) {
                DihClientMessaging.sendPrefixed("Sent payload: " + result.channel());
            }
        } catch (Exception e) {
            DihClientMessaging.sendPrefixed("§cPayload action failed: " + DihPayloadSupport.safeMessage(e));
        }
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putBoolean("enabled", enabled);
        tag.putString("channel", channel == null ? "" : channel);
        tag.putString("payloadData", payloadData == null ? "" : payloadData);
        tag.putString("payloadJson", payloadJson == null ? "" : payloadJson);
        tag.putString("payloadClassName", payloadClassName == null ? "" : payloadClassName);
        tag.putString("javaSource", javaSource == null ? "" : javaSource);
        tag.putBoolean("payloadScriptEnabled", payloadScriptEnabled);
        tag.putBoolean("commandApiRecognized", commandApiRecognized);
        tag.putBoolean("commandApiOverride", commandApiOverride);
        tag.putInt("commandApiValue", commandApiValue);
        tag.putString("sourceDirection", sourceDirection == null ? "" : sourceDirection);
        tag.putString("sourceProtocol", sourceProtocol == null ? "" : sourceProtocol);
        tag.putString("payloadDirection", payloadDirection == null ? "" : payloadDirection);
        tag.putString("payloadPhase", payloadPhase == null ? "" : payloadPhase);
        tag.putString("payloadEncodingMode", payloadEncodingMode == null ? "" : payloadEncodingMode);
        tag.putString("payloadFields", payloadFields == null ? "" : payloadFields);
        tag.putInt("payloadPacketId", payloadPacketId);
        tag.putString("payloadProvenance", payloadProvenance == null ? "" : payloadProvenance);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        enabled = tag.getBooleanOr("enabled", true);
        channel = tag.getStringOr("channel", "minecraft:brand");
        payloadData = tag.getStringOr("payloadData", "");
        payloadJson = tag.getStringOr("payloadJson", "");
        payloadClassName = tag.getStringOr("payloadClassName", "");
        javaSource = tag.getStringOr("javaSource", "");
        payloadScriptEnabled = tag.getBooleanOr("payloadScriptEnabled", false);
        commandApiRecognized = tag.getBooleanOr("commandApiRecognized", false);
        commandApiOverride = tag.getBooleanOr("commandApiOverride", false);
        commandApiValue = tag.getIntOr("commandApiValue", Integer.MAX_VALUE - 8);
        sourceDirection = tag.getStringOr("sourceDirection", "C2S");
        sourceProtocol = tag.getStringOr("sourceProtocol", "");
        payloadDirection = tag.getStringOr("payloadDirection", sourceDirection == null || sourceDirection.isBlank() ? "C2S" : sourceDirection);
        payloadPhase = tag.getStringOr("payloadPhase", sourceProtocol != null && sourceProtocol.toLowerCase(java.util.Locale.ROOT).contains("configuration") ? "CONFIGURATION" : "PLAY");
        payloadEncodingMode = tag.getStringOr("payloadEncodingMode", "");
        payloadFields = tag.getStringOr("payloadFields", "");
        payloadPacketId = tag.getIntOr("payloadPacketId", -1);
        payloadProvenance = tag.getStringOr("payloadProvenance", "userEdited");
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.PAYLOAD;
    }

    @Override
    public String getDisplayName() {
        if (channel == null || channel.isBlank()) return "Payload";
        return "Payload - " + channel;
    }

    @Override
    public String getIcon() {
        return "network";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void sanitizeForSharing() {
        payloadScriptEnabled = false;
        javaSource = "";
    }
}
