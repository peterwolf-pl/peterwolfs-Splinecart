package io.github.foundationgames.splinecart.test;

import io.github.foundationgames.splinecart.block.TrackTiesBlock;
import io.github.foundationgames.splinecart.util.TrackPlacement;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TrackOrientationTest {
    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void testGroundHorizontalAlignments() {
        // Test East (+X)
        Vector3d east = new Vector3d(1, 0, 0);
        int pointingEast = TrackPlacement.bestPointingFor(POS, Direction.UP, east);
        Vector3d fwdEast = TrackTiesBlock.getPose(POS, Direction.UP, pointingEast).basis().getColumn(2, new Vector3d()).normalize();
        assertTrue(fwdEast.dot(east) > 0.99, "Expected forward to point East, dot was " + fwdEast.dot(east));

        // Test West (-X)
        Vector3d west = new Vector3d(-1, 0, 0);
        int pointingWest = TrackPlacement.bestPointingFor(POS, Direction.UP, west);
        Vector3d fwdWest = TrackTiesBlock.getPose(POS, Direction.UP, pointingWest).basis().getColumn(2, new Vector3d()).normalize();
        assertTrue(fwdWest.dot(west) > 0.99, "Expected forward to point West, dot was " + fwdWest.dot(west));

        // Test South (+Z)
        Vector3d south = new Vector3d(0, 0, 1);
        int pointingSouth = TrackPlacement.bestPointingFor(POS, Direction.UP, south);
        Vector3d fwdSouth = TrackTiesBlock.getPose(POS, Direction.UP, pointingSouth).basis().getColumn(2, new Vector3d()).normalize();
        assertTrue(fwdSouth.dot(south) > 0.99, "Expected forward to point South, dot was " + fwdSouth.dot(south));

        // Test North (-Z)
        Vector3d north = new Vector3d(0, 0, -1);
        int pointingNorth = TrackPlacement.bestPointingFor(POS, Direction.UP, north);
        Vector3d fwdNorth = TrackTiesBlock.getPose(POS, Direction.UP, pointingNorth).basis().getColumn(2, new Vector3d()).normalize();
        assertTrue(fwdNorth.dot(north) > 0.99, "Expected forward to point North, dot was " + fwdNorth.dot(north));
    }

    @Test
    public void testAngledAlignments() {
        // 20 degrees off East: (10, 0, 3) -> should still snap to East pointing
        Vector3d eastBiased = new Vector3d(10, 0, 3).normalize();
        int pointing = TrackPlacement.bestPointingFor(POS, Direction.UP, eastBiased);
        Vector3d fwd = TrackTiesBlock.getPose(POS, Direction.UP, pointing).basis().getColumn(2, new Vector3d()).normalize();
        assertTrue(fwd.dot(new Vector3d(1, 0, 0)) > 0.99, "Expected forward to snap to East");

        // 70 degrees off East: (3, 0, 10) -> should snap to South pointing
        Vector3d southBiased = new Vector3d(3, 0, 10).normalize();
        pointing = TrackPlacement.bestPointingFor(POS, Direction.UP, southBiased);
        fwd = TrackTiesBlock.getPose(POS, Direction.UP, pointing).basis().getColumn(2, new Vector3d()).normalize();
        assertTrue(fwd.dot(new Vector3d(0, 0, 1)) > 0.99, "Expected forward to snap to South");
    }

    @Test
    public void testSlopeAlignments() {
        // Track going up a hill to the East: (10, 5, 0)
        Vector3d uphill = new Vector3d(10, 5, 0).normalize();
        int pointing = TrackPlacement.bestPointingFor(POS, Direction.UP, uphill);
        Vector3d fwd = TrackTiesBlock.getPose(POS, Direction.UP, pointing).basis().getColumn(2, new Vector3d()).normalize();
        assertTrue(fwd.dot(new Vector3d(1, 0, 0)) > 0.99, "Expected sloped track to face uphill direction (East)");
    }

    @Test
    public void testWallAndCeilingAlignments() {
        // Ceiling: track going East
        Vector3d ceilingEast = new Vector3d(1, 0, 0);
        int pointingCeil = TrackPlacement.bestPointingFor(POS, Direction.DOWN, ceilingEast);
        Vector3d fwdCeil = TrackTiesBlock.getPose(POS, Direction.DOWN, pointingCeil).basis().getColumn(2, new Vector3d()).normalize();
        assertTrue(fwdCeil.dot(ceilingEast) > 0.99, "Expected ceiling forward to point East");

        // Wall: North wall (facing South), track climbing UP
        Vector3d wallUp = new Vector3d(0, 1, 0);
        int pointingWall = TrackPlacement.bestPointingFor(POS, Direction.NORTH, wallUp);
        Vector3d fwdWall = TrackTiesBlock.getPose(POS, Direction.NORTH, pointingWall).basis().getColumn(2, new Vector3d()).normalize();
        assertTrue(fwdWall.dot(wallUp) > 0.99, "Expected wall forward to point UP");
    }

    @Test
    public void testCurveIntermediateTieOrientation() {
        // Tie A at (-10, 64, 0), Tie B at (0, 64, 0), Tie C at (0, 64, 10)
        // Track arrives from West (A -> B: heading East +X) and leaves to South (B -> C: heading South +Z)
        Vector3d dirIn = new Vector3d(1, 0, 0);  // current - prev = (0 - (-10), 0, 0) -> +X
        Vector3d dirOut = new Vector3d(0, 0, 1); // next - current = (0, 0, 10 - 0) -> +Z
        Vector3d tangent = new Vector3d(dirIn).add(dirOut).normalize(); // South-East (45 deg)

        int pointing = TrackPlacement.bestPointingFor(POS, Direction.UP, tangent);
        Vector3d fwd = TrackTiesBlock.getPose(POS, Direction.UP, pointing).basis().getColumn(2, new Vector3d()).normalize();

        // The chosen cardinal orientation must align with the curve tangent (dot >= cos(45°))
        assertTrue(fwd.dot(tangent) > 0.70, "Curve tie should align with tangent (45 deg)");
        // And must not point backwards
        assertTrue(fwd.dot(dirIn) >= 0.0 && fwd.dot(dirOut) >= 0.0, "Curve tie should not point backward");
    }
}
