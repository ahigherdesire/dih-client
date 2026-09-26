package dihclient.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
//? if >=26.3 {
/*import net.minecraft.core.component.TypedDataComponent;
*///?}

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Item data whose API differs between Minecraft 26.2 and 26.3. */
public final class DihItems {

    private DihItems() {
    }

    /**
     * A component patch as type to value: present for a set component, empty for a removed one (26.2's
     * {@code entrySet()}; 26.3 only exposes the patch through {@code split()}).
     */
    public static Map<DataComponentType<?>, Optional<?>> patchEntries(DataComponentPatch patch) {
        Map<DataComponentType<?>, Optional<?>> out = new LinkedHashMap<>();
        //? if >=26.3 {
        /*DataComponentPatch.SplitResult split = patch.split();
        for (TypedDataComponent<?> component : split.added()) out.put(component.type(), Optional.of(component.value()));
        for (DataComponentType<?> removed : split.removed()) out.put(removed, Optional.empty());
        *///?} else {
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) out.put(entry.getKey(), entry.getValue());
        //?}
        return out;
    }
}
