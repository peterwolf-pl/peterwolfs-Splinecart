package io.github.foundationgames.splinecart.util;

import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

/** Read-only geometric view of one saved spline connection. */
public final class SplineSegment {
    private static final int CLASSIFICATION_SAMPLES = 8;
    private static final double HORIZONTAL_EPSILON = 0.035;
    private static final double STRAIGHT_EPSILON = 0.04;

    private final @Nullable TrackTiesBlockEntity start;
    private final @Nullable TrackTiesBlockEntity end;
    private final Pose startPose;
    private final Pose endPose;
    private final boolean banking;

    public SplineSegment(TrackTiesBlockEntity start, TrackTiesBlockEntity end) {
        this.start = start;
        this.end = end;
        this.startPose = start.pose();
        this.endPose = end.pose();
        this.banking = start.nextBanking();
    }

    public SplineSegment(Pose startPose, Pose endPose) {
        this(startPose, endPose, false);
    }

    public SplineSegment(Pose startPose, Pose endPose, boolean banking) {
        this.start = null;
        this.end = null;
        this.startPose = startPose;
        this.endPose = endPose;
        this.banking = banking;
    }

    public @Nullable TrackTiesBlockEntity start() {
        return this.start;
    }

    public @Nullable TrackTiesBlockEntity end() {
        return this.end;
    }

    public Pose startPose() {
        return this.startPose;
    }

    public Pose endPose() {
        return this.endPose;
    }

    public void interpolate(double t, Vector3d position, Matrix3d basis, Vector3d derivative) {
        TrackBanking.interpolate(this.startPose, this.endPose, this.banking, t, position, basis, derivative);
    }

    private void interpolateUnbanked(double t, Vector3d position, Matrix3d basis, Vector3d derivative) {
        this.startPose.interpolate(this.endPose, t, position, basis, derivative);
    }

    public boolean isHorizontal() {
        var position = new Vector3d();
        var basis = new Matrix3d();
        var derivative = new Vector3d();
        var up = new Vector3d();

        for (int i = 0; i <= CLASSIFICATION_SAMPLES; i++) {
            interpolateUnbanked((double) i / CLASSIFICATION_SAMPLES, position, basis, derivative);
            if (derivative.lengthSquared() < 1.0e-8 || Math.abs(derivative.normalize(new Vector3d()).y()) > HORIZONTAL_EPSILON) {
                return false;
            }
            basis.getColumn(1, up).normalize();
            if (up.y() < 0.9) {
                return false;
            }
        }

        return true;
    }

    public boolean isStraightHorizontal() {
        if (!isHorizontal()) {
            return false;
        }

        var a = new Vector3d(this.startPose.translation());
        var chord = new Vector3d(this.endPose.translation()).sub(a);
        double length = chord.length();
        if (length < 0.25) {
            return false;
        }
        chord.div(length);

        var position = new Vector3d();
        var basis = new Matrix3d();
        var derivative = new Vector3d();
        for (int i = 0; i <= CLASSIFICATION_SAMPLES; i++) {
            interpolate((double) i / CLASSIFICATION_SAMPLES, position, basis, derivative);
            double along = new Vector3d(position).sub(a).dot(chord);
            double offLine = new Vector3d(position).sub(a).fma(-along, chord).length();
            if (offLine > STRAIGHT_EPSILON || Math.abs(derivative.normalize().dot(chord)) < 0.995) {
                return false;
            }
        }

        return true;
    }

    public double nearestStraightProgress(Vector3d point) {
        var a = new Vector3d(this.startPose.translation());
        var chord = new Vector3d(this.endPose.translation()).sub(a);
        double lengthSquared = chord.lengthSquared();
        if (lengthSquared < 1.0e-8) {
            return 0.0;
        }
        return Math.clamp(new Vector3d(point).sub(a).dot(chord) / lengthSquared, 0.0, 1.0);
    }

    public double estimatedLength(int samples) {
        var previous = new Vector3d(this.startPose.translation());
        var position = new Vector3d();
        var basis = new Matrix3d();
        var derivative = new Vector3d();
        double length = 0.0;
        for (int i = 1; i <= samples; i++) {
            interpolate((double) i / samples, position, basis, derivative);
            length += position.distance(previous);
            previous.set(position);
        }
        return length;
    }
}
