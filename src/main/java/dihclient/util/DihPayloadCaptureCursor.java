package dihclient.util;

/**
 * Per-thread byte positions DihCustomPayloadCodecMixin uses while capturing payloads. Outside the mixin package:
 * a mixin can't have a named nested class on Forge.
 */
public final class DihPayloadCaptureCursor {
    public int encodeStartIndex = -1;
    public int decodeStartIndex = -1;
    public long encodeState;
    public long decodeState;
}
