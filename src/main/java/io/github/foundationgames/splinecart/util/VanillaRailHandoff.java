package io.github.foundationgames.splinecart.util;

import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.entity.TrackFollowerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Hands a minecart between a spline endpoint and an adjacent vanilla rail
 * without snapping position or running off-rail braking.
 */
public final class VanillaRailHandoff {
    private static final int LEAVE_TICKS = 8;
    private static final Map<Integer, Integer> LEAVING = new HashMap<>();

    private VanillaRailHandoff() {
    }

    public static boolean transferToRail(Entity cart, Vec3 velocity) {
        BlockPos rail = findRailAhead(cart.level(), cart.position(), velocity);
        Vec3 axisVelocity = velocity;
        if (rail != null) {
            axisVelocity = alignRailAndProjectVelocity(cart.level(), rail, velocity);
            double railY = rail.getY() + 0.1;
            if (Math.abs(cart.getY() - railY) < 0.5) {
                cart.setPos(cart.getX(), railY, cart.getZ());
            }
        }
        cart.setDeltaMovement(axisVelocity);
        markLeaving(cart);
        return rail != null;
    }

    public static void markLeaving(Entity cart) {
        LEAVING.put(cart.getId(), LEAVE_TICKS);
    }

    public static boolean isLeaving(Entity cart) {
        Integer remaining = LEAVING.get(cart.getId());
        return remaining != null && remaining > 0;
    }

    public static void tick(Entity cart) {
        Integer remaining = LEAVING.get(cart.getId());
        if (remaining == null) {
            return;
        }
        if (remaining <= 1) {
            LEAVING.remove(cart.getId());
        } else {
            LEAVING.put(cart.getId(), remaining - 1);
        }
    }

    public static boolean transferToSpline(AbstractMinecart cart) {
        if (cart.getVehicle() instanceof TrackFollowerEntity || isLeaving(cart)) {
            return cart.getVehicle() instanceof TrackFollowerEntity;
        }
        if (BaseRailBlock.isRail(cart.level().getBlockState(cart.blockPosition()))
                || BaseRailBlock.isRail(cart.level().getBlockState(cart.blockPosition().below()))) {
            return false;
        }

        Vec3 velocity = cart.getDeltaMovement();
        BlockPos origin = cart.blockPosition();
        if (tryMount(cart, origin) || tryMount(cart, origin.below())) {
            return true;
        }
        if (velocity.horizontalDistanceSqr() <= 1.0e-8) {
            return false;
        }

        Direction facing = Direction.getApproximateNearest(velocity.x, 0.0, velocity.z);
        BlockPos ahead = origin.relative(facing);
        return tryMount(cart, ahead) || tryMount(cart, ahead.below()) || tryMount(cart, ahead.above());
    }

    public static @Nullable BlockPos findRailAhead(Level world, Vec3 position, Vec3 velocity) {
        BlockPos origin = BlockPos.containing(position);
        Vec3 dir = velocity.horizontalDistanceSqr() > 1.0e-8 ? velocity.horizontal().normalize() : Vec3.ZERO;
        BlockPos best = null;
        double bestScore = -Double.MAX_VALUE;

        for (BlockPos pos : railCandidates(origin, dir)) {
            if (!BaseRailBlock.isRail(world.getBlockState(pos))) {
                continue;
            }
            Vec3 toRail = Vec3.atBottomCenterOf(pos).subtract(position).horizontal();
            double score = dir.lengthSqr() > 0.0 ? toRail.dot(dir) - toRail.length() * 0.01 : -toRail.length();
            if (score > bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return best;
    }

    private static Vec3 alignRailAndProjectVelocity(Level world, BlockPos rail, Vec3 velocity) {
        BlockState state = world.getBlockState(rail);
        if (!(state.getBlock() instanceof BaseRailBlock block)) {
            return velocity;
        }

        var property = block.getShapeProperty();
        if (!state.hasProperty(property)) {
            return velocity;
        }

        RailShape shape = state.getValue(property);
        if (!shape.isSlope()) {
            boolean eastWest = Math.abs(velocity.x) >= Math.abs(velocity.z);
            RailShape desired = eastWest ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
            if (shape != desired && canRotateStraight(world, rail, desired)) {
                world.setBlock(rail, state.setValue(property, desired), 2);
                shape = desired;
            }
        }

        return projectOntoRail(velocity, shape);
    }

    private static boolean canRotateStraight(Level world, BlockPos rail, RailShape desired) {
        boolean wantEastWest = desired == RailShape.EAST_WEST;
        Direction a = wantEastWest ? Direction.EAST : Direction.NORTH;
        Direction b = wantEastWest ? Direction.WEST : Direction.SOUTH;
        Direction c = wantEastWest ? Direction.NORTH : Direction.EAST;
        Direction d = wantEastWest ? Direction.SOUTH : Direction.WEST;
        boolean along = BaseRailBlock.isRail(world.getBlockState(rail.relative(a)))
                || BaseRailBlock.isRail(world.getBlockState(rail.relative(b)));
        boolean across = BaseRailBlock.isRail(world.getBlockState(rail.relative(c)))
                || BaseRailBlock.isRail(world.getBlockState(rail.relative(d)));
        return along || !across;
    }

    private static Vec3 projectOntoRail(Vec3 velocity, RailShape shape) {
        return switch (shape) {
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new Vec3(velocity.x, 0.0, 0.0);
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new Vec3(0.0, 0.0, velocity.z);
            default -> velocity.horizontal();
        };
    }

    private static BlockPos[] railCandidates(BlockPos origin, Vec3 dir) {
        BlockPos ahead = dir.lengthSqr() > 0.0
                ? origin.relative(Direction.getApproximateNearest(dir.x, 0.0, dir.z), 2)
                : origin;
        return new BlockPos[] {
                origin.north(), origin.south(), origin.east(), origin.west(),
                origin.north().below(), origin.south().below(), origin.east().below(), origin.west().below(),
                origin.north().above(), origin.south().above(), origin.east().above(), origin.west().above(),
                ahead, origin, origin.below(), origin.above()
        };
    }

    private static boolean tryMount(AbstractMinecart cart, BlockPos pos) {
        Level world = cart.level();
        if (!(world.getBlockEntity(pos) instanceof TrackTiesBlockEntity)) {
            return false;
        }

        TrackFollowerEntity follower = TrackFollowerEntity.create(world, cart.position(), pos, cart.getDeltaMovement());
        if (follower == null) {
            return false;
        }

        world.addFreshEntity(follower);
        cart.startRiding(follower, true, false);
        return true;
    }
}
