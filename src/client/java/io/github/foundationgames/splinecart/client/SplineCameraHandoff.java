package io.github.foundationgames.splinecart.client;

import io.github.foundationgames.splinecart.SplinecartClient;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Bakes spline camera orientation into the player's yaw/pitch at the moment
 * they leave a track follower, so first-person view does not twitch.
 *
 * <p>Must live outside {@code mixin.client} — Mixin forbids loading non-mixin
 * classes from a declared mixin package (IllegalClassLoadError).
 */
public final class SplineCameraHandoff {
	private static boolean riding;
	private static final Quaternionf TRACK_ROT = new Quaternionf();
	private static float minecartYaw;

	private SplineCameraHandoff() {
	}

	public static void capture(TrackFollowerEntity follower, float vehicleYaw, float tickDelta) {
		follower.getClientOrientation(TRACK_ROT, tickDelta);
		minecartYaw = vehicleYaw;
		riding = true;
	}

	public static void applyIfLeaving(Entity cameraEntity) {
		if (!riding || cameraEntity == null || onFollower(cameraEntity)) {
			return;
		}
		riding = false;
		if (!SplinecartClient.CFG_ROTATE_CAMERA.get()) {
			return;
		}

		Quaternionf playerCam = new Quaternionf().rotationYXZ(
			(float) Math.PI - cameraEntity.getYRot() * Mth.DEG_TO_RAD,
			-cameraEntity.getXRot() * Mth.DEG_TO_RAD,
			0.0F);
		Quaternionf worldCam = new Quaternionf(TRACK_ROT)
			.mul(new Quaternionf().rotationY(Mth.DEG_TO_RAD * (90.0F + minecartYaw)))
			.mul(playerCam);

		Vector3f euler = new Vector3f();
		worldCam.getEulerAnglesYXZ(euler);
		float yaw = (Mth.PI - euler.y) * Mth.RAD_TO_DEG;
		float pitch = -euler.x * Mth.RAD_TO_DEG;
		cameraEntity.setYRot(yaw);
		cameraEntity.setXRot(pitch);
		cameraEntity.setYHeadRot(yaw);
		cameraEntity.yRotO = yaw;
		cameraEntity.xRotO = pitch;
	}

	private static boolean onFollower(Entity entity) {
		var vehicle = entity.getVehicle();
		return vehicle != null && vehicle.getVehicle() instanceof TrackFollowerEntity;
	}
}
