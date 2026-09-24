package dihclient.util;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DihPayloadChannelRegistrations {
    public static final String SOURCE_CUSTOM = "custom";
    public static final String SOURCE_PRESET = "preset";
    public static final String SOURCE_LEARNED = "learned";

    private final List<DihConfig.PayloadChannelRegistrationRule> rules = new ArrayList<>();

    public DihPayloadChannelRegistrations() {
        load();
    }

    public void load() {
        rules.clear();
        DihConfig config = DihConfig.getGlobal();
        if (config.packetLoggerPayloadRegistrations == null) {
            config.packetLoggerPayloadRegistrations = new ArrayList<>();
        }

        Map<String, DihConfig.PayloadChannelRegistrationRule> merged = new LinkedHashMap<>();
        for (DihConfig.PayloadChannelRegistrationRule rule : config.packetLoggerPayloadRegistrations) {
            DihConfig.PayloadChannelRegistrationRule clean = clean(rule);
            if (clean == null) continue;
            mergeInto(merged, clean);
        }

        if (merged.isEmpty() && config.packetLoggerPayloadFilters != null) {
            migrateExactFilters(merged, config.packetLoggerPayloadFilters);
        }

        rules.addAll(merged.values());
    }

    public List<DihConfig.PayloadChannelRegistrationRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    List<DihConfig.PayloadChannelRegistrationRule> mutableRules() {
        return rules;
    }

    public List<String> enabledChannels() {
        List<String> channels = new ArrayList<>();
        for (DihConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule != null && rule.enabled && isRegisterableChannel(rule.channel)) {
                channels.add(normalizeChannel(rule.channel));
            }
        }
        return channels;
    }

    public boolean hasEnabled(String channel) {
        String normalized = normalizeChannel(channel);
        if (normalized.isBlank()) return false;
        for (DihConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule != null && rule.enabled && normalized.equals(normalizeChannel(rule.channel))) return true;
        }
        return false;
    }

    public int indexOf(String channel) {
        String normalized = normalizeChannel(channel);
        for (int i = 0; i < rules.size(); i++) {
            DihConfig.PayloadChannelRegistrationRule rule = rules.get(i);
            if (rule != null && normalized.equals(normalizeChannel(rule.channel))) return i;
        }
        return -1;
    }

    public boolean addOrEnable(String label, String channel, String source) {
        boolean changed = addOrEnableInMemory(label, channel, source);
        if (changed) save();
        return changed;
    }

    public boolean addOrEnableInMemory(String label, String channel, String source) {
        String normalized = normalizeChannel(channel);
        if (!isRegisterableChannel(normalized)) return false;
        int index = indexOf(normalized);
        if (index >= 0) {
            DihConfig.PayloadChannelRegistrationRule rule = rules.get(index);
            boolean changed = false;
            if (!rule.enabled) {
                rule.enabled = true;
                changed = true;
            }
            String cleanLabel = cleanLabel(label, normalized);
            if ((rule.label == null || rule.label.isBlank() || rule.label.equals(rule.channel)) && !cleanLabel.equals(normalized)) {
                rule.label = cleanLabel;
                changed = true;
            }
            if (rule.source == null || rule.source.isBlank() || SOURCE_LEARNED.equals(rule.source)) {
                rule.source = cleanSource(source);
                changed = true;
            }
            return changed;
        }

        DihConfig.PayloadChannelRegistrationRule rule = new DihConfig.PayloadChannelRegistrationRule();
        rule.channel = normalized;
        rule.label = cleanLabel(label, normalized);
        rule.enabled = true;
        rule.source = cleanSource(source);
        rules.add(rule);
        return true;
    }

    public boolean addLearnedSuggestion(String label, String channel) {
        String normalized = normalizeChannel(channel);
        if (!isRegisterableChannel(normalized)) return false;
        if (indexOf(normalized) >= 0) return false;
        DihConfig.PayloadChannelRegistrationRule rule = new DihConfig.PayloadChannelRegistrationRule();
        rule.channel = normalized;
        rule.label = cleanLabel(label, normalized);
        rule.enabled = false;
        rule.source = SOURCE_LEARNED;
        rules.add(rule);
        save();
        return true;
    }

    public void toggle(int index) {
        if (index < 0 || index >= rules.size()) return;
        DihConfig.PayloadChannelRegistrationRule rule = rules.get(index);
        if (rule == null) return;
        rule.enabled = !rule.enabled;
        save();
    }

    public void toggleInMemory(int index) {
        if (index < 0 || index >= rules.size()) return;
        DihConfig.PayloadChannelRegistrationRule rule = rules.get(index);
        if (rule != null) rule.enabled = !rule.enabled;
    }

    public void remove(int index) {
        if (index < 0 || index >= rules.size()) return;
        rules.remove(index);
        save();
    }

    public void removeInMemory(int index) {
        if (index < 0 || index >= rules.size()) return;
        rules.remove(index);
    }

    public void disableAll() {
        boolean changed = false;
        for (DihConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule != null && rule.enabled) {
                rule.enabled = false;
                changed = true;
            }
        }
        if (changed) save();
    }

    public void disableAllInMemory() {
        for (DihConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule != null) rule.enabled = false;
        }
    }

    public void replaceWithApplied(Collection<String> channels) {
        Set<String> applied = new LinkedHashSet<>();
        if (channels != null) {
            for (String channel : channels) {
                String normalized = normalizeChannel(channel);
                if (isRegisterableChannel(normalized)) applied.add(normalized);
            }
        }

        boolean changed = false;
        for (String channel : applied) {
            int index = indexOf(channel);
            if (index >= 0) {
                DihConfig.PayloadChannelRegistrationRule rule = rules.get(index);
                if (!rule.enabled) {
                    rule.enabled = true;
                    changed = true;
                }
            } else {
                DihConfig.PayloadChannelRegistrationRule rule = new DihConfig.PayloadChannelRegistrationRule();
                rule.channel = channel;
                rule.label = channel;
                rule.enabled = true;
                rule.source = SOURCE_CUSTOM;
                rules.add(rule);
                changed = true;
            }
        }
        for (DihConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule == null) continue;
            boolean shouldEnable = applied.contains(normalizeChannel(rule.channel));
            if (rule.enabled != shouldEnable) {
                rule.enabled = shouldEnable;
                changed = true;
            }
        }
        if (changed) save();
    }

    public void applyRecommendedOnly() {
        boolean changed = false;
        Set<String> defaults = new LinkedHashSet<>();
        for (DihPayloadChannelListeners.Preset preset : DihPayloadChannelListeners.presetCatalog()) {
            if (!DihPayloadChannelListeners.isDefaultRecommendedPresetPublic(preset)) continue;
            String pattern = DihPayloadChannelListeners.normalizePattern(preset.pattern());
            if (DihPayloadChannelListeners.isRegisterablePreset(preset)) defaults.add(pattern);
        }

        for (DihConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule == null) continue;
            boolean shouldEnable = defaults.contains(normalizeChannel(rule.channel));
            if (rule.enabled != shouldEnable) {
                rule.enabled = shouldEnable;
                changed = true;
            }
        }
        for (DihPayloadChannelListeners.Preset preset : DihPayloadChannelListeners.presetCatalog()) {
            if (!DihPayloadChannelListeners.isDefaultRecommendedPresetPublic(preset)) continue;
            String pattern = DihPayloadChannelListeners.normalizePattern(preset.pattern());
            if (DihPayloadChannelListeners.isRegisterablePreset(preset) && addOrEnableNoSave(preset.label(), pattern, SOURCE_PRESET)) {
                changed = true;
            }
        }
        if (changed) save();
    }

    public void applyRecommendedOnlyInMemory() {
        Set<String> defaults = new LinkedHashSet<>();
        for (DihPayloadChannelListeners.Preset preset : DihPayloadChannelListeners.presetCatalog()) {
            if (!DihPayloadChannelListeners.isDefaultRecommendedPresetPublic(preset)) continue;
            String pattern = DihPayloadChannelListeners.normalizePattern(preset.pattern());
            if (DihPayloadChannelListeners.isRegisterablePreset(preset)) defaults.add(pattern);
        }

        for (DihConfig.PayloadChannelRegistrationRule rule : rules) {
            if (rule == null) continue;
            rule.enabled = defaults.contains(normalizeChannel(rule.channel));
        }
        for (DihPayloadChannelListeners.Preset preset : DihPayloadChannelListeners.presetCatalog()) {
            if (!DihPayloadChannelListeners.isDefaultRecommendedPresetPublic(preset)) continue;
            String pattern = DihPayloadChannelListeners.normalizePattern(preset.pattern());
            if (DihPayloadChannelListeners.isRegisterablePreset(preset)) addOrEnableNoSave(preset.label(), pattern, SOURCE_PRESET);
        }
    }

    public static boolean isRegisterableChannel(String channel) {
        String normalized = normalizeChannel(channel);
        if (normalized.isBlank() || normalized.indexOf('*') >= 0 || normalized.indexOf(':') <= 0) return false;
        if ("minecraft:register".equals(normalized) || "minecraft:unregister".equals(normalized)
            || "minecraft:brand".equals(normalized)) return false;
        return Identifier.tryParse(normalized) != null;
    }

    public static String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
    }

    public static String sourceLabel(String source) {
        return switch (cleanSource(source)) {
            case SOURCE_PRESET -> "Preset";
            case SOURCE_LEARNED -> "Learned";
            default -> "Custom";
        };
    }

    private void migrateExactFilters(Map<String, DihConfig.PayloadChannelRegistrationRule> merged,
                                     List<DihConfig.PayloadChannelFilterRule> filterRules) {
        for (DihConfig.PayloadChannelFilterRule filter : filterRules) {
            if (filter == null || !filter.enabled) continue;
            String pattern = DihPayloadChannelListeners.normalizePattern(filter.pattern);
            if (!isRegisterableChannel(pattern)) continue;
            DihConfig.PayloadChannelRegistrationRule rule = new DihConfig.PayloadChannelRegistrationRule();
            rule.channel = pattern;
            rule.label = cleanLabel(filter.label, pattern);
            rule.enabled = true;
            rule.source = filter.preset ? SOURCE_PRESET : SOURCE_CUSTOM;
            mergeInto(merged, rule);
        }
    }

    private boolean addOrEnableNoSave(String label, String channel, String source) {
        String normalized = normalizeChannel(channel);
        if (!isRegisterableChannel(normalized)) return false;
        int index = indexOf(normalized);
        if (index >= 0) {
            DihConfig.PayloadChannelRegistrationRule rule = rules.get(index);
            boolean changed = false;
            if (!rule.enabled) {
                rule.enabled = true;
                changed = true;
            }
            if (SOURCE_LEARNED.equals(rule.source)) {
                rule.source = cleanSource(source);
                changed = true;
            }
            return changed;
        }
        DihConfig.PayloadChannelRegistrationRule rule = new DihConfig.PayloadChannelRegistrationRule();
        rule.channel = normalized;
        rule.label = cleanLabel(label, normalized);
        rule.enabled = true;
        rule.source = cleanSource(source);
        rules.add(rule);
        return true;
    }

    private static void mergeInto(Map<String, DihConfig.PayloadChannelRegistrationRule> merged,
                                  DihConfig.PayloadChannelRegistrationRule clean) {
        String key = normalizeChannel(clean.channel);
        DihConfig.PayloadChannelRegistrationRule existing = merged.get(key);
        if (existing == null) {
            merged.put(key, clean);
            return;
        }
        existing.enabled |= clean.enabled;
        if (SOURCE_LEARNED.equals(existing.source) && !SOURCE_LEARNED.equals(clean.source)) existing.source = clean.source;
        if ((existing.label == null || existing.label.isBlank() || existing.label.equals(existing.channel))
            && clean.label != null && !clean.label.isBlank()) {
            existing.label = clean.label;
        }
    }

    private static DihConfig.PayloadChannelRegistrationRule clean(DihConfig.PayloadChannelRegistrationRule rule) {
        if (rule == null) return null;
        String channel = normalizeChannel(rule.channel);
        if (!isRegisterableChannel(channel)) return null;
        String label = rule.label == null ? "" : rule.label.trim();
        if (isPublicProbeChannel(channel) || isPublicProbeChannel(label)) return null;
        DihConfig.PayloadChannelRegistrationRule clean = new DihConfig.PayloadChannelRegistrationRule();
        clean.channel = channel;
        clean.label = cleanLabel(label, channel);
        clean.enabled = rule.enabled;
        clean.source = cleanSource(rule.source);
        return clean;
    }

    private static String cleanLabel(String label, String fallback) {
        String value = label == null ? "" : label.trim();
        return value.isBlank() ? fallback : value;
    }

    private static String cleanSource(String source) {
        String value = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case SOURCE_PRESET, SOURCE_LEARNED -> value;
            default -> SOURCE_CUSTOM;
        };
    }

    private static boolean isPublicProbeChannel(String value) {
        String normalized = normalizeChannel(value);
        if (normalized.isBlank()) return false;
        return normalized.contains("dihtest")
            || normalized.contains("payloadprobe")
            || normalized.contains("payload_probe")
            || normalized.contains("brandlike")
            || normalized.contains("mc_string")
            || normalized.contains("java_utf");
    }

    private void save() {
        commit(true);
    }

    public void commit(boolean writeFile) {
        DihConfig config = DihConfig.getGlobal();
        config.packetLoggerPayloadRegistrations = new ArrayList<>(rules);
        if (writeFile) config.save();
        dihclient.modules.DihModule.get().invalidatePayloadListenerCache(false);
    }
}
