package dihclient.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DihBlockNbtCaptureTest {
    @Test
    void shortcutRequiresBothModifiers() {
        assertTrue(DihBlockNbtCapture.shortcutDown(true, true));
        assertFalse(DihBlockNbtCapture.shortcutDown(true, false));
        assertFalse(DihBlockNbtCapture.shortcutDown(false, true));
        assertFalse(DihBlockNbtCapture.shortcutDown(false, false));
    }

    @Test
    void payloadPreservesCustomStateAndNestedNbt() {
        CompoundTag state = new CompoundTag();
        state.putString("Name", "example:custom_block");
        CompoundTag properties = new CompoundTag();
        properties.putString("variant", "charged");
        state.put("Properties", properties);

        CompoundTag entity = new CompoundTag();
        entity.putString("id", "example:custom_block_entity");
        CompoundTag custom = new CompoundTag();
        custom.putString("plugin:key", "full-value");
        entity.put("components", custom);

        DihBlockNbtCapture.Snapshot snapshot = new DihBlockNbtCapture.Snapshot(
            "example:custom_dimension", new BlockPos(12, 34, -56),
            "example:custom_block[variant=charged]", state);
        CompoundTag payload = DihBlockNbtCapture.payload(snapshot, entity, "client");

        assertEquals(DihBlockNbtCapture.FORMAT, payload.getStringOr("format", ""));
        assertEquals("example:custom_dimension", payload.getStringOr("dimension", ""));
        assertArrayEquals(new int[]{12, 34, -56}, payload.getIntArray("position").orElseThrow());
        assertEquals("example:custom_block", payload.getCompound("state").orElseThrow().getStringOr("Name", ""));
        assertEquals("full-value", payload.getCompound("block_entity").orElseThrow()
            .getCompound("components").orElseThrow().getStringOr("plugin:key", ""));
    }

    @Test
    void authoritativeNbtOverridesLocalAndKeepsMetadata() {
        CompoundTag local = new CompoundTag();
        local.putString("id", "example:machine");
        local.putString("secret", "old");
        CompoundTag server = new CompoundTag();
        server.putString("secret", "authoritative");

        CompoundTag merged = DihBlockNbtCapture.mergeAuthoritative(local, server, new BlockPos(7, 8, 9));

        assertEquals("example:machine", merged.getStringOr("id", ""));
        assertEquals("authoritative", merged.getStringOr("secret", ""));
        assertEquals(7, merged.getIntOr("x", 0));
        assertEquals(8, merged.getIntOr("y", 0));
        assertEquals(9, merged.getIntOr("z", 0));
    }
}
