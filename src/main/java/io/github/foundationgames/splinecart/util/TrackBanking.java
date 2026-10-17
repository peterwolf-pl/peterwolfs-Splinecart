package io.github.foundationgames.splinecart.util;

import org.joml.Matrix3d;
import org.joml.Vector3d;

/** Adds a smooth roll to curved track without changing the saved spline path. */
public final class TrackBanking {
    private static final double MAX_BANK_RADIANS = Math.toRadians(28.0);
    private static final double BANK_PER_TURN_RADIAN = 0.55;
    private static final double CURVATURE_SAMPLE_WINDOW = 0.16;
    private static final double MIN_HORIZONTAL_LENGTH_SQUARED = 1.0e-6;
    private static final double HALF_TRACK_WIDTH = 0.5;
    private static final double FLAT_HEIGHT_EPSILON = 0.035;
    private static final double FLAT_FORWARD_EPSILON = 0.035;
    private static final double UPRIGHT_EPSILON = 0.9;

    private TrackBanking() {
    }

    public static void interpolate(Pose start, Pose end, boolean banking, double t,
                                   Vector3d position, Matrix3d basis, Vector3d derivative) {
        start.interpolate(end, t, position, basis, derivative);
        if (!banking) {
            return;
        }

        double angle = bankAngle(start, end, t);
        if (Math.abs(angle) < 1.0e-8) {
            return;
        }

        // Banking reaches its maximum in the bend and joins every tie level.
        double envelope = Math.sin(Math.PI * Math.clamp(t, 0.0, 1.0));
        envelope *= envelope;
        double appliedAngle = angle * envelope;

        if (isFlat(start, end)) {
            // Rotate around the lower rail rather than around the centre line:
            // the inside rail stays on the ground and the outside rail rises.
            position.y += HALF_TRACK_WIDTH * Math.abs(Math.sin(appliedAngle));
        }

        basis.rotateZ(appliedAngle);
    }

    public static double bankAngle(Pose start, Pose end, double t) {
        double sampleStart = Math.max(0.0, t - CURVATURE_SAMPLE_WINDOW);
        double sampleEnd = Math.min(1.0, t + CURVATURE_SAMPLE_WINDOW);
        double span = sampleEnd - sampleStart;
        if (span < 1.0e-6) {
            return 0.0;
        }

        var from = new Vector3d();
        var to = new Vector3d();
        start.interpolateTranslation(end, sampleStart, new Vector3d(), from);
        start.interpolateTranslation(end, sampleEnd, new Vector3d(), to);
        from.y = 0.0;
        to.y = 0.0;

        if (from.lengthSquared() < MIN_HORIZONTAL_LENGTH_SQUARED ||
                to.lengthSquared() < MIN_HORIZONTAL_LENGTH_SQUARED) {
            return 0.0;
        }

        from.normalize();
        to.normalize();
        double dot = Math.clamp(from.dot(to), -1.0, 1.0);
        double signedTurnRate = Math.atan2(from.z * to.x - from.x * to.z, dot) / span;

        // Positive Y turn is a right-hand bend in Minecraft's X/Z coordinates;
        // rolling in the opposite direction lowers the inside rail.
        return Math.clamp(-signedTurnRate * BANK_PER_TURN_RADIAN, -MAX_BANK_RADIANS, MAX_BANK_RADIANS);
    }

    private static boolean isFlat(Pose start, Pose end) {
        if (Math.abs(start.translation().y() - end.translation().y()) > FLAT_HEIGHT_EPSILON) {
            return false;
        }

        var startForward = start.basis().getColumn(2, new Vector3d()).normalize();
        var endForward = end.basis().getColumn(2, new Vector3d()).normalize();
        if (Math.abs(startForward.y) > FLAT_FORWARD_EPSILON ||
                Math.abs(endForward.y) > FLAT_FORWARD_EPSILON) {
            return false;
        }

        var startUp = start.basis().getColumn(1, new Vector3d()).normalize();
        var endUp = end.basis().getColumn(1, new Vector3d()).normalize();
        return startUp.y >= UPRIGHT_EPSILON && endUp.y >= UPRIGHT_EPSILON;
    }
}
