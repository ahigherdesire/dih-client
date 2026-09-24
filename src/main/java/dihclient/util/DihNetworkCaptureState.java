package dihclient.util;

import dihclient.modules.DihModule;
import dihclient.modules.PackHideState;

public final class DihNetworkCaptureState {
    public static final int PLAINTEXT = 1;
    public static final int PAYLOAD = 1 << 1;
    public static final byte[] EMPTY_BYTES = new byte[0];

    private static final int MODE_MASK = PLAINTEXT | PAYLOAD;
    private static volatile long state;

    private static volatile long expirationDeadlineMs;

    private static final ThreadLocal<int[]> CODEC_SUPPRESSION_DEPTH = new ThreadLocal<>();

    private DihNetworkCaptureState() {
    }

    public static long state() {
        return state;
    }

    public static long codecState() {
        long current = state;
        if (!capturesPayloads(current)) return current;
        int[] depth = CODEC_SUPPRESSION_DEPTH.get();
        return depth != null && depth[0] > 0 ? current & ~PAYLOAD : current;
    }

    public static void beginMultiCodecSuppression() {
        int[] depth = CODEC_SUPPRESSION_DEPTH.get();
        if (depth == null) {
            depth = new int[1];
            CODEC_SUPPRESSION_DEPTH.set(depth);
        }
        depth[0]++;
    }

    public static void endMultiCodecSuppression() {
        int[] depth = CODEC_SUPPRESSION_DEPTH.get();
        if (depth == null) return;
        if (--depth[0] <= 0) CODEC_SUPPRESSION_DEPTH.remove();
    }

    public static void clearCodecSuppression() {
        CODEC_SUPPRESSION_DEPTH.remove();
    }

    public static int mode(long capturedState) {
        return (int) capturedState & MODE_MASK;
    }

    public static boolean capturesPlaintext(long capturedState) {
        return (mode(capturedState) & PLAINTEXT) != 0;
    }

    public static boolean capturesPayloads(long capturedState) {
        return (mode(capturedState) & PAYLOAD) != 0;
    }

    public static boolean capturesPlaintext() {
        return capturesPlaintext(state);
    }

    public static boolean capturesPayloads() {
        return capturesPayloads(state);
    }

    public static void refreshCurrent() {
        refresh(DihModule.get());
    }

    public static void refreshIfDue(DihModule module) {
        long deadline = expirationDeadlineMs;
        if (deadline <= 0L || System.currentTimeMillis() <= deadline) return;
        expirationDeadlineMs = 0L;
        refresh(module);
    }

    public static void refresh(DihModule module) {
        int next = 0;
        if (module != null && !PackHideState.isHardLocked()) {
            if (module.shouldCapturePacketPlaintext()) next |= PLAINTEXT;
            if (module.shouldCapturePayloadBytes()) next |= PAYLOAD;
            long deadline = module.passivePayloadCaptureDeadlineMs();
            expirationDeadlineMs = deadline > System.currentTimeMillis() ? deadline : 0L;
        } else {
            expirationDeadlineMs = 0L;
        }
        publish(next);
    }

    public static void disable() {
        expirationDeadlineMs = 0L;
        publish(0);
    }

    private static void publish(int nextMode) {
        long previous = state;
        if (mode(previous) == nextMode) return;
        synchronized (DihNetworkCaptureState.class) {
            previous = state;
            if (mode(previous) == nextMode) return;
            long nextEpoch = (previous >>> 2) + 1L;
            state = (nextEpoch << 2) | (nextMode & MODE_MASK);
        }
        DihRuntimeActivity.publish(DihRuntimeActivity.PACKET_CAPTURE, nextMode != 0);
    }
}
