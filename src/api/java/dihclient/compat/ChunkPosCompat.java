package dihclient.compat;

//? if <26.1 {
/*import net.minecraft.world.level.ChunkPos;

// Minecraft 1.21.11 only: the record-style accessors 26.1 gave ChunkPos. Loom injects this interface into the 1.21.11
// ChunkPos at compile time (src/compat1211/resources/fabric.mod.json; the dev jars keep it, so unit tests see it too)
// and DihChunkPosCompatMixin adds it at runtime.
public interface ChunkPosCompat {
    default int x() {
        return ((ChunkPos) (Object) this).x;
    }

    default int z() {
        return ((ChunkPos) (Object) this).z;
    }

    default long pack() {
        return ((ChunkPos) (Object) this).toLong();
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface ChunkPosCompat {
}
//?}
