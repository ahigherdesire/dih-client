package dihclient.util;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DihServerRotationViewTest {
    @AfterEach
    void clearTracker() {
        DihServerRotationView.reset();
    }

    @Test
    void tracksOnlyMovementPacketsThatActuallyCarryRotation() {
        DihServerRotationView.onPacketWritten(
            new ServerboundMovePlayerPacket.Rot(135.0F, 42.0F, true, false));
        assertEquals(new DihServerRotationView.Rotation(135.0F, 42.0F),
            DihServerRotationView.currentRotation());

        DihServerRotationView.onPacketWritten(
            new ServerboundMovePlayerPacket.Pos(new Vec3(4.0D, 70.0D, -2.0D), true, false));
        assertEquals(new DihServerRotationView.Rotation(135.0F, 42.0F),
            DihServerRotationView.currentRotation(),
            "position-only packets must preserve the rotation the server already holds");
    }

    @Test
    void killAuraStyleSilentRotationChangesOnlyTheRenderSnapshot() {
        DihServerRotationView.onPacketWritten(
            new ServerboundMovePlayerPacket.Rot(-147.5F, 31.25F, true, false));
        LivingEntityRenderState renderState = new LivingEntityRenderState();
        renderState.bodyRot = 18.0F;
        renderState.yRot = 9.0F;
        renderState.xRot = -4.0F;

        DihServerRotationView.applyRotation(
            renderState, DihServerRotationView.currentRotation());

        assertEquals(-147.5F, renderState.bodyRot, 1.0E-6F);
        assertEquals(0.0F, renderState.yRot, 1.0E-6F);
        assertEquals(31.25F, renderState.xRot, 1.0E-6F);
    }

    @Test
    void sanitizesTheRenderPoseWithoutTouchingAnEntity() {
        DihServerRotationView.update(725.0F, 95.0F);
        DihServerRotationView.Rotation rotation = DihServerRotationView.currentRotation();
        assertNotNull(rotation);
        assertEquals(5.0F, rotation.yaw(), 1.0E-6F);
        assertEquals(90.0F, rotation.pitch(), 1.0E-6F);

        LivingEntityRenderState state = new LivingEntityRenderState();
        state.bodyRot = -30.0F;
        state.yRot = 75.0F;
        state.xRot = -20.0F;
        DihServerRotationView.applyRotation(state, rotation);
        assertEquals(5.0F, state.bodyRot, 1.0E-6F);
        assertEquals(0.0F, state.yRot, 1.0E-6F);
        assertEquals(90.0F, state.xRot, 1.0E-6F);
    }

    @Test
    void ignoresInvalidWireRotations() {
        DihServerRotationView.update(20.0F, 10.0F);
        DihServerRotationView.update(Float.NaN, 50.0F);
        assertEquals(new DihServerRotationView.Rotation(20.0F, 10.0F),
            DihServerRotationView.currentRotation());
    }

    @Test
    void renderGateIsStrictlyThirdPersonLocalCameraOnly() {
        assertTrue(DihServerRotationView.shouldApply(true, true, true, false, false, true));
        assertFalse(DihServerRotationView.shouldApply(true, true, true, false, false, false),
            "without a silent owner vanilla's already-interpolated render state must remain untouched");
        assertFalse(DihServerRotationView.shouldApply(true, true, false, false, false, true));
        assertFalse(DihServerRotationView.shouldApply(false, true, true, false, false, true));
        assertFalse(DihServerRotationView.shouldApply(true, false, true, false, false, true));
        assertFalse(DihServerRotationView.shouldApply(true, true, true, true, false, true));
        assertFalse(DihServerRotationView.shouldApply(true, true, true, false, true, true));
    }

    @Test
    void interpolatesAcrossTheYawWrapByTheShortPath() {
        DihServerRotationView.Rotation halfway = DihServerRotationView.interpolate(
            new DihServerRotationView.Rotation(179.0F, 10.0F),
            new DihServerRotationView.Rotation(-179.0F, 30.0F),
            0.5F);

        assertEquals(-180.0F, halfway.yaw(), 1.0E-4F);
        assertEquals(20.0F, halfway.pitch(), 1.0E-4F);
    }

    @Test
    void completedEndpointDoesNotRewindWhenPartialTickResets() {
        DihServerRotationView.Timeline timeline = new DihServerRotationView.Timeline(
            new DihServerRotationView.Rotation(10.0F, 5.0F),
            new DihServerRotationView.Rotation(70.0F, 25.0F),
            40);

        assertEquals(new DihServerRotationView.Rotation(40.0F, 15.0F),
            DihServerRotationView.interpolatedRotation(timeline, 0.5F, 40));
        assertEquals(new DihServerRotationView.Rotation(70.0F, 25.0F),
            DihServerRotationView.interpolatedRotation(timeline, 0.0F, 41),
            "a tick without another look packet must hold the latest endpoint");
    }

    @Test
    void multiplePacketsInOneTickKeepOneInterpolationInterval() {
        DihServerRotationView.updateAtTick(10.0F, 5.0F, 20);
        DihServerRotationView.updateAtTick(40.0F, 15.0F, 21);
        DihServerRotationView.updateAtTick(70.0F, 25.0F, 21);

        assertEquals(new DihServerRotationView.Rotation(40.0F, 15.0F),
            DihServerRotationView.interpolatedRotation(
                DihServerRotationView.currentTimeline(), 0.5F, 21));
        assertEquals(new DihServerRotationView.Rotation(70.0F, 25.0F),
            DihServerRotationView.currentRotation());
    }
}
