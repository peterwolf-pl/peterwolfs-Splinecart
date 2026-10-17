package io.github.foundationgames.splinecart.block;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.SupportMode;
import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.item.TrackItem;
import io.github.foundationgames.splinecart.util.Pose;
import io.github.foundationgames.splinecart.util.SUtil;
import io.github.foundationgames.splinecart.util.SplineSegment;
import io.github.foundationgames.splinecart.util.TrackPlacement;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class TrackTiesBlockEntity extends BlockEntity {
    private static final double STEEL_PILLAR_SPACING = 12.0;
    private static final double[] NO_STEEL_PILLARS = new double[0];

    public float clientTime = 0;
    public final TrackGeometry geometry;

    private TrackType nextType = TrackType.DEFAULT;
    private TrackType nextAltType = TrackType.DEFAULT;
    private TrackType prevType = TrackType.DEFAULT;
    private TrackType prevAltType = TrackType.DEFAULT;

    private BlockPos next;
    private BlockPos nextAlt;
    private BlockPos prev;
    private BlockPos prevAlt;
    private Pose basePose;
    private Pose pose;
    /** Snapshot of the previous tie pose for anchor smoothing when prev chunk is unloaded. */
    private @Nullable Pose prevPose;
    private @Nullable Pose prevAltPose;
    /** Snapshot used to render a long segment while the next chunk is unloaded on the client. */
    private @Nullable Pose nextPose;
    private @Nullable Pose nextAltPose;
    private int nextPower = -1;
    private int nextAltPower = -1;
    private boolean nextBanking;
    private boolean nextAltBanking;
    private boolean smoothing = true;
    private SupportMode nextSupports = SupportMode.AUTO;
    private SupportMode nextAltSupports = SupportMode.AUTO;

    private int power = -1;
    private int detectorTicks = 0;

    public TrackTiesBlockEntity(BlockPos pos, BlockState state) {
        super(Splinecart.TRACK_TIES_BE, pos, state);
        updatePose(pos, state);

        this.geometry = TrackGeometry.CONSTRUCTOR.apply(this);
    }

    public void updatePose(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof TrackTiesBlock ties) {
            this.basePose = ties.getPose(state, pos);
        } else {
            this.basePose = new Pose(new Vector3d(), new Matrix3d().identity());
        }

        applySmoothing(false);

        if (getLevel() != null && !getLevel().isClientSide()) {
            pushPoseToIncoming(this.prev);
            pushPoseToIncoming(this.prevAlt);
        }
    }

    public boolean isSmoothing() {
        return this.smoothing;
    }

    public void setSmoothing(boolean smoothing) {
        if (this.smoothing != smoothing) {
            this.smoothing = smoothing;
            if (this.smoothing) {
                adjustOrientationToTracks();
            }
            applySmoothing(true);
            this.geometry.needsRebuild = true;
            setChanged();
            if (getLevel() != null && !getLevel().isClientSide()) {
                sync();
                var prevE = prev();
                if (prevE != null) {
                    if (prevE.isSmoothing()) {
                        prevE.adjustOrientationToTracks();
                    }
                    prevE.applySmoothing(true);
                    prevE.setUpdated();
                }
                var nextE = next();
                if (nextE != null) {
                    if (nextE.isSmoothing()) {
                        nextE.adjustOrientationToTracks();
                    }
                    nextE.applySmoothing(true);
                    nextE.setUpdated();
                }
            }
        }
    }

    public boolean shouldSmoothTrack() {
        return this.smoothing
                && this.prev != null && this.next != null
                && this.prevAlt == null && this.nextAlt == null;
    }

    public void applySmoothing(boolean notifyNeighbors) {
        Pose oldPose = this.pose;
        if (!shouldSmoothTrack()) {
            this.pose = this.basePose;
        } else {
            this.pose = computeSmoothedPose();
        }

        if (notifyNeighbors && (oldPose == null || !oldPose.equals(this.pose))) {
            pushPoseToIncoming(this.prev);
            pushPoseToIncoming(this.prevAlt);
            pushPoseToOutgoing(this.next);
            pushPoseToOutgoing(this.nextAlt);
        }
    }

    private Pose computeSmoothedPose() {
        if (this.basePose == null) {
            return new Pose(new Vector3d(), new Matrix3d().identity());
        }

        var posCurr = this.basePose.translation();

        // Previous anchor position
        Vector3dc posPrev;
        var prevE = prev();
        if (prevE != null) {
            posPrev = prevE.pose().translation();
            this.prevPose = prevE.pose();
        } else if (this.prevPose != null) {
            posPrev = this.prevPose.translation();
        } else if (this.prev != null) {
            posPrev = TrackTiesBlock.getPose(this.prev, Direction.UP, 0).translation();
        } else {
            return this.basePose;
        }

        // Next anchor position
        Vector3dc posNext;
        var nextE = next();
        if (nextE != null) {
            posNext = nextE.pose().translation();
            this.nextPose = nextE.pose();
        } else if (this.nextPose != null) {
            posNext = this.nextPose.translation();
        } else if (this.next != null) {
            posNext = TrackTiesBlock.getPose(this.next, Direction.UP, 0).translation();
        } else {
            return this.basePose;
        }

        Vector3d fallbackForward = this.basePose.basis().getColumn(2, new Vector3d());
        Vector3d dirIn = new Vector3d(posCurr).sub(posPrev);
        Vector3d dirOut = new Vector3d(posNext).sub(posCurr);

        if (dirIn.lengthSquared() > 1.0e-8) {
            dirIn.normalize();
        } else {
            dirIn.set(fallbackForward);
        }

        if (dirOut.lengthSquared() > 1.0e-8) {
            dirOut.normalize();
        } else {
            dirOut.set(fallbackForward);
        }

        Vector3d tangent = new Vector3d(dirIn).add(dirOut);
        if (tangent.lengthSquared() < 1.0e-6) {
            tangent.set(dirOut);
        }
        if (tangent.lengthSquared() < 1.0e-6) {
            tangent.set(fallbackForward);
        }
        tangent.normalize();

        Vector3d baseUp = this.basePose.basis().getColumn(1, new Vector3d());
        Vector3d up = new Vector3d(baseUp).fma(-baseUp.dot(tangent), tangent);
        if (up.lengthSquared() < 1.0e-6) {
            Vector3d alt = new Vector3d(fallbackForward);
            up.set(alt).fma(-alt.dot(tangent), tangent);
            if (up.lengthSquared() < 1.0e-6) {
                Vector3d alt2 = this.basePose.basis().getColumn(0, new Vector3d());
                up.set(alt2).fma(-alt2.dot(tangent), tangent);
                if (up.lengthSquared() < 1.0e-6) {
                    up.set(0, 1, 0);
                }
            }
        }
        up.normalize();

        Vector3d right = new Vector3d();
        up.cross(tangent, right);
        if (right.lengthSquared() < 1.0e-6) {
            right.set(1, 0, 0);
        } else {
            right.normalize();
        }

        tangent.cross(right, up);
        up.normalize();

        Matrix3d basis = new Matrix3d();
        basis.setColumn(0, right);
        basis.setColumn(1, up);
        basis.setColumn(2, tangent);

        return new Pose(posCurr, basis);
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);

        updatePose(this.getBlockPos(), this.getBlockState());
    }

    public static @Nullable TrackTiesBlockEntity of(Level world, @Nullable BlockPos pos) {
        if (pos != null && world.getBlockEntity(pos) instanceof TrackTiesBlockEntity e) {
            return e;
        }

        return null;
    }

    private void pushPoseToIncoming(@Nullable BlockPos pos) {
        if (pos == null) return;
        var tie = of(getLevel(), pos);
        if (tie != null) {
            if (getBlockPos().equals(tie.next)) {
                tie.nextPose = this.pose();
                tie.nextPower = this.power();
                tie.geometry.needsRebuild = true;
                tie.setUpdated();
            } else if (getBlockPos().equals(tie.nextAlt)) {
                tie.nextAltPose = this.pose();
                tie.nextAltPower = this.power();
                tie.geometry.needsRebuild = true;
                tie.setUpdated();
            }
        }
    }

    private void pushPoseToOutgoing(@Nullable BlockPos pos) {
        if (pos == null) return;
        var tie = of(getLevel(), pos);
        if (tie != null) {
            if (getBlockPos().equals(tie.prev)) {
                tie.prevPose = this.pose();
                tie.geometry.needsRebuild = true;
                tie.setUpdated();
            } else if (getBlockPos().equals(tie.prevAlt)) {
                tie.prevAltPose = this.pose();
                tie.geometry.needsRebuild = true;
                tie.setUpdated();
            }
        }
    }

    public boolean isSwitch() {
        return this.nextAlt != null || this.prevAlt != null;
    }

    public @Nullable Vector3d computeConnectedTrackDirection() {
        Vector3d dirIn = null;
        Vector3d dirOut = null;
        BlockPos currentPos = getBlockPos();

        if (this.next != null && this.nextAlt != null) {
            Vector3d d1 = new Vector3d(this.next.getX() - currentPos.getX(), this.next.getY() - currentPos.getY(), this.next.getZ() - currentPos.getZ());
            Vector3d d2 = new Vector3d(this.nextAlt.getX() - currentPos.getX(), this.nextAlt.getY() - currentPos.getY(), this.nextAlt.getZ() - currentPos.getZ());
            if (d1.lengthSquared() > 1.0e-8) d1.normalize();
            if (d2.lengthSquared() > 1.0e-8) d2.normalize();
            Vector3d comb = new Vector3d(d1).add(d2);
            if (comb.lengthSquared() > 1.0e-6) {
                dirOut = comb.normalize();
            } else if (d1.lengthSquared() > 1.0e-8) {
                dirOut = d1;
            }
        } else if (this.next != null) {
            Vector3d d = new Vector3d(this.next.getX() - currentPos.getX(), this.next.getY() - currentPos.getY(), this.next.getZ() - currentPos.getZ());
            if (d.lengthSquared() > 1.0e-8) {
                dirOut = d.normalize();
            }
        } else if (this.nextAlt != null) {
            Vector3d d = new Vector3d(this.nextAlt.getX() - currentPos.getX(), this.nextAlt.getY() - currentPos.getY(), this.nextAlt.getZ() - currentPos.getZ());
            if (d.lengthSquared() > 1.0e-8) {
                dirOut = d.normalize();
            }
        }

        if (this.prev != null && this.prevAlt != null) {
            Vector3d d1 = new Vector3d(currentPos.getX() - this.prev.getX(), currentPos.getY() - this.prev.getY(), currentPos.getZ() - this.prev.getZ());
            Vector3d d2 = new Vector3d(currentPos.getX() - this.prevAlt.getX(), currentPos.getY() - this.prevAlt.getY(), currentPos.getZ() - this.prevAlt.getZ());
            if (d1.lengthSquared() > 1.0e-8) d1.normalize();
            if (d2.lengthSquared() > 1.0e-8) d2.normalize();
            Vector3d comb = new Vector3d(d1).add(d2);
            if (comb.lengthSquared() > 1.0e-6) {
                dirIn = comb.normalize();
            } else if (d1.lengthSquared() > 1.0e-8) {
                dirIn = d1;
            }
        } else if (this.prev != null) {
            Vector3d d = new Vector3d(currentPos.getX() - this.prev.getX(), currentPos.getY() - this.prev.getY(), currentPos.getZ() - this.prev.getZ());
            if (d.lengthSquared() > 1.0e-8) {
                dirIn = d.normalize();
            }
        } else if (this.prevAlt != null) {
            Vector3d d = new Vector3d(currentPos.getX() - this.prevAlt.getX(), currentPos.getY() - this.prevAlt.getY(), currentPos.getZ() - this.prevAlt.getZ());
            if (d.lengthSquared() > 1.0e-8) {
                dirIn = d.normalize();
            }
        }

        if (dirIn != null && dirOut != null) {
            Vector3d tangent = new Vector3d(dirIn).add(dirOut);
            if (tangent.lengthSquared() < 1.0e-6) {
                tangent.set(dirOut);
            }
            if (tangent.lengthSquared() > 1.0e-8) {
                return tangent.normalize();
            }
            return dirOut;
        } else if (dirOut != null) {
            return dirOut;
        } else if (dirIn != null) {
            return dirIn;
        }

        return null;
    }

    public boolean adjustOrientationToTracks() {
        if (!this.smoothing) {
            return false;
        }
        var world = getLevel();
        if (world == null || world.isClientSide()) {
            return false;
        }

        Vector3d targetDir = computeConnectedTrackDirection();
        if (targetDir == null) {
            return false;
        }

        var pos = getBlockPos();
        var state = world.getBlockState(pos);
        if (!state.hasProperty(TrackTiesBlock.FACING) || !state.hasProperty(TrackTiesBlock.POINTING)) {
            return false;
        }

        var facing = state.getValue(TrackTiesBlock.FACING);
        int currentPointing = state.getValue(TrackTiesBlock.POINTING);

        int bestPointing = TrackPlacement.bestPointingFor(pos, facing, targetDir);

        boolean stateChanged = false;
        var newState = state;

        if (bestPointing != currentPointing) {
            newState = newState.setValue(TrackTiesBlock.POINTING, bestPointing);
            stateChanged = true;
        }

        if (newState.hasProperty(TrackTiesBlock.SWITCH)) {
            boolean switchVal = isSwitch();
            if (newState.getValue(TrackTiesBlock.SWITCH) != switchVal) {
                newState = newState.setValue(TrackTiesBlock.SWITCH, switchVal);
                stateChanged = true;
            }
        }

        if (stateChanged) {
            world.setBlock(pos, newState, 3);
            updatePose(pos, newState);
            setChanged();
            sync();
            return true;
        }

        return false;
    }

    public void syncSwitchStateBlock() {
        var world = getLevel();
        if (world != null && !world.isClientSide()) {
            if (this.smoothing) {
                adjustOrientationToTracks();
            } else {
                var pos = getBlockPos();
                var state = world.getBlockState(pos);
                if (state.hasProperty(TrackTiesBlock.SWITCH)) {
                    boolean switchVal = isSwitch();
                    if (state.getValue(TrackTiesBlock.SWITCH) != switchVal) {
                        world.setBlock(pos, state.setValue(TrackTiesBlock.SWITCH, switchVal), 3);
                    }
                }
            }
        }
    }

    public boolean isOutgoingSwitch() {
        return this.next != null && this.nextAlt != null;
    }

    public boolean isIncomingSwitch() {
        return this.prev != null && this.prevAlt != null;
    }

    /**
     * Active branch index based on redstone power:
     * 0 = unpowered (signal == 0) -> primary (next / prev)
     * 1 = powered (signal > 0)    -> alternate (nextAlt / prevAlt)
     */
    public int getActiveSwitchBranch() {
        return power() > 0 ? 1 : 0;
    }

    public @Nullable BlockPos getActiveNextPos() {
        if (this.nextAlt != null && getActiveSwitchBranch() == 1) {
            return this.nextAlt;
        }
        return this.next != null ? this.next : this.nextAlt;
    }

    public @Nullable TrackTiesBlockEntity getActiveNext() {
        return of(this.getLevel(), getActiveNextPos());
    }

    public TrackType getActiveNextType() {
        if (this.nextAlt != null && getActiveSwitchBranch() == 1) {
            return this.nextAltType;
        }
        return this.next != null ? this.nextType : this.nextAltType;
    }

    public boolean getActiveNextBanking() {
        if (this.nextAlt != null && getActiveSwitchBranch() == 1) {
            return this.nextAltBanking;
        }
        return this.next != null ? this.nextBanking : this.nextAltBanking;
    }

    public @Nullable BlockPos getActivePrevPos() {
        if (this.prevAlt != null && getActiveSwitchBranch() == 1) {
            return this.prevAlt;
        }
        return this.prev != null ? this.prev : this.prevAlt;
    }

    public @Nullable TrackTiesBlockEntity getActivePrev() {
        return of(this.getLevel(), getActivePrevPos());
    }

    public TrackType getActivePrevType() {
        if (this.prevAlt != null && getActiveSwitchBranch() == 1) {
            return this.prevAltType;
        }
        return this.prev != null ? this.prevType : this.prevAltType;
    }

    private void dropTrack(TrackType type) {
        var world = getLevel();
        var pos = Vec3.atCenterOf(getBlockPos());
        var item = new ItemEntity(world, pos.x(), pos.y(), pos.z(), new ItemStack(TrackItem.ITEMS_BY_TYPE.get(type)));

        world.addFreshEntity(item);
    }

    public SupportMode getTieSupportMode() {
        if (getBlockState().getBlock() instanceof TrackTiesBlock tieBlock) {
            return tieBlock.getSupportMode();
        }
        return SupportMode.WOOD;
    }

    public void setNext(@Nullable BlockPos pos, @Nullable TrackType type) {
        setNext(pos, type, getTieSupportMode(), false);
    }

    public void setNext(@Nullable BlockPos pos, @Nullable TrackType type, SupportMode supports) {
        setNext(pos, type, supports, false);
    }

    public void setNext(@Nullable BlockPos pos, @Nullable TrackType type, SupportMode supports, boolean banking) {
        if (getLevel() != null && !getLevel().isClientSide()) {
            if (this.next != null) {
                clearSupportsForNext();
            }
        }

        if (pos == null) {
            var oldNextE = next();
            this.next = null;
            this.nextPose = null;
            this.nextPower = -1;
            this.nextBanking = false;
            this.nextSupports = SupportMode.AUTO;
            if (oldNextE != null) {
                if (getBlockPos().equals(oldNextE.prevAlt)) {
                    oldNextE.prevAlt = null;
                    oldNextE.prevAltPose = null;
                } else if (getBlockPos().equals(oldNextE.prev)) {
                    oldNextE.prev = null;
                    oldNextE.prevPose = null;
                }
                oldNextE.syncSwitchStateBlock();
                oldNextE.applySmoothing(true);
                oldNextE.setUpdated();
            }
            syncSwitchStateBlock();
            applySmoothing(true);
            setUpdated();
        } else {
            this.next = pos;
            this.nextPose = null;
            this.nextPower = -1;
            this.nextBanking = banking;
            this.nextSupports = supports;
            if (type != null) {
                this.nextType = type;
            }
            var nextE = next();
            if (nextE != null) {
                this.nextPose = nextE.pose();
                this.nextPower = nextE.power();
                if (nextE.prev == null || getBlockPos().equals(nextE.prev)) {
                    nextE.prev = getBlockPos();
                    nextE.prevPose = this.pose();
                    if (type != null) {
                        nextE.prevType = type;
                    }
                } else if (nextE.prevAlt == null || getBlockPos().equals(nextE.prevAlt)) {
                    nextE.prevAlt = getBlockPos();
                    nextE.prevAltPose = this.pose();
                    if (type != null) {
                        nextE.prevAltType = type;
                    }
                }
                nextE.syncSwitchStateBlock();
                nextE.applySmoothing(true);
                nextE.setUpdated();
            }

            syncSwitchStateBlock();
            applySmoothing(true);
            if (nextE != null) {
                this.nextPose = nextE.pose();
            }

            if (getLevel() != null && !getLevel().isClientSide() && supports != SupportMode.NONE) {
                buildSupportsForNext(supports);
            }
            setUpdated();
        }
    }

    public @Nullable TrackTiesBlockEntity next() {
        return of(this.getLevel(), this.next);
    }

    public @Nullable TrackTiesBlockEntity prev() {
        return of(this.getLevel(), this.prev);
    }

    public @Nullable BlockPos nextPos() {
        return next;
    }

    public @Nullable BlockPos prevPos() {
        return prev;
    }

    public TrackType nextType() {
        return this.nextType;
    }

    public TrackType prevType() {
        return this.prevType;
    }

    public boolean nextBanking() {
        return this.nextBanking;
    }

    public SupportMode nextSupports() {
        return this.nextSupports;
    }

    public boolean canAcceptOutgoing() {
        return this.next == null || this.nextAlt == null;
    }

    public boolean canAcceptIncoming() {
        return this.prev == null || this.prevAlt == null;
    }

    public boolean hasConnectionWith(BlockPos pos) {
        return pos.equals(this.next) || pos.equals(this.nextAlt)
                || pos.equals(this.prev) || pos.equals(this.prevAlt);
    }

    public boolean addOutgoing(BlockPos pos, TrackType type, SupportMode supports, boolean banking) {
        if (hasConnectionWith(pos)) {
            return false;
        }
        if (this.next == null) {
            setNext(pos, type, supports, banking);
            return true;
        } else if (this.nextAlt == null) {
            setNextAlt(pos, type, supports, banking);
            return true;
        }
        return false;
    }

    public void setNextAlt(@Nullable BlockPos pos, @Nullable TrackType type, SupportMode supports, boolean banking) {
        if (getLevel() != null && !getLevel().isClientSide()) {
            if (this.nextAlt != null) {
                clearSupportsForNextAlt();
            }
        }

        if (pos == null) {
            var oldNextAltE = nextAlt();
            this.nextAlt = null;
            this.nextAltPose = null;
            this.nextAltPower = -1;
            this.nextAltBanking = false;
            this.nextAltSupports = SupportMode.AUTO;
            if (oldNextAltE != null) {
                if (getBlockPos().equals(oldNextAltE.prevAlt)) {
                    oldNextAltE.prevAlt = null;
                    oldNextAltE.prevAltPose = null;
                } else if (getBlockPos().equals(oldNextAltE.prev)) {
                    oldNextAltE.prev = null;
                    oldNextAltE.prevPose = null;
                }
                oldNextAltE.syncSwitchStateBlock();
                oldNextAltE.applySmoothing(true);
                oldNextAltE.setUpdated();
            }
            syncSwitchStateBlock();
            applySmoothing(true);
            setUpdated();
        } else {
            this.nextAlt = pos;
            this.nextAltPose = null;
            this.nextAltPower = -1;
            this.nextAltBanking = banking;
            this.nextAltSupports = supports;
            if (type != null) {
                this.nextAltType = type;
            }
            var nextAltE = nextAlt();
            if (nextAltE != null) {
                this.nextAltPose = nextAltE.pose();
                this.nextAltPower = nextAltE.power();
                if (nextAltE.prev == null || getBlockPos().equals(nextAltE.prev)) {
                    nextAltE.prev = getBlockPos();
                    nextAltE.prevPose = this.pose();
                    if (type != null) nextAltE.prevType = type;
                } else if (nextAltE.prevAlt == null || getBlockPos().equals(nextAltE.prevAlt)) {
                    nextAltE.prevAlt = getBlockPos();
                    nextAltE.prevAltPose = this.pose();
                    if (type != null) nextAltE.prevAltType = type;
                }
                nextAltE.syncSwitchStateBlock();
                nextAltE.applySmoothing(true);
                nextAltE.setUpdated();
            }

            syncSwitchStateBlock();
            applySmoothing(true);
            if (nextAltE != null) {
                this.nextAltPose = nextAltE.pose();
            }

            if (getLevel() != null && !getLevel().isClientSide() && supports != SupportMode.NONE) {
                buildSupportsForNextAlt(supports);
            }
            setUpdated();
        }
    }

    public @Nullable TrackTiesBlockEntity nextAlt() {
        return of(this.getLevel(), this.nextAlt);
    }

    public @Nullable TrackTiesBlockEntity prevAlt() {
        return of(this.getLevel(), this.prevAlt);
    }

    public @Nullable BlockPos nextAltPos() {
        return nextAlt;
    }

    public @Nullable BlockPos prevAltPos() {
        return prevAlt;
    }

    public TrackType nextAltType() {
        return this.nextAltType;
    }

    public TrackType prevAltType() {
        return this.prevAltType;
    }

    public boolean nextAltBanking() {
        return this.nextAltBanking;
    }

    public SupportMode nextAltSupports() {
        return this.nextAltSupports;
    }

    public @Nullable RenderEndpoint renderNextAltEndpoint() {
        if (this.nextAlt == null) {
            return null;
        }

        var nextE = nextAlt();
        if (nextE != null) {
            this.nextAltPose = nextE.pose();
            this.nextAltPower = nextE.power();
            return new RenderEndpoint(nextE.getBlockPos(), nextE.pose(), this.nextAltType, nextE.power());
        }

        if (this.nextAltPose == null) {
            return null;
        }

        return new RenderEndpoint(this.nextAlt, this.nextAltPose, this.nextAltType, Math.max(this.nextAltPower, 0));
    }

    public double[] steelPillarAltTs() {
        if (this.nextAltSupports != SupportMode.STEEL) {
            return NO_STEEL_PILLARS;
        }
        var endpoint = renderNextAltEndpoint();
        if (endpoint == null) {
            return NO_STEEL_PILLARS;
        }
        return computeSteelPillarTs(new SplineSegment(this.pose(), endpoint.pose(), this.nextAltBanking));
    }

    public double estimatedAltTrackLength() {
        var nextEndpoint = renderNextAltEndpoint();
        if (nextEndpoint == null) {
            return 0;
        }

        return new Vector3d(this.pose().translation()).distance(new Vector3d(nextEndpoint.pose().translation()));
    }

    public double[] steelPillarTs() {
        if (this.nextSupports != SupportMode.STEEL) {
            return NO_STEEL_PILLARS;
        }
        var endpoint = renderNextEndpoint();
        if (endpoint == null) {
            return NO_STEEL_PILLARS;
        }
        return computeSteelPillarTs(new SplineSegment(this.pose(), endpoint.pose(), this.nextBanking));
    }

    public Pose pose() {
        if (!shouldSmoothTrack() || this.pose == null) {
            return this.basePose != null ? this.basePose : (this.pose != null ? this.pose : new Pose(new Vector3d(), new Matrix3d().identity()));
        }
        return this.pose;
    }

    public void updatePower() {
        var world = getLevel();
        if (world == null) {
            return;
        }

        var state = world.getBlockState(getBlockPos());
        if (!isSwitch() && this.power >= 0 && state.hasProperty(TrackTiesBlock.POWERED) && state.getValue(TrackTiesBlock.POWERED)) {
            return;
        }

        int oldPower = this.power;
        this.power = world.getBestNeighborSignal(getBlockPos());

        if (isSwitch() && state.hasProperty(TrackTiesBlock.POWERED)) {
            boolean shouldBePowered = this.power > 0;
            if (state.getValue(TrackTiesBlock.POWERED) != shouldBePowered) {
                world.setBlock(getBlockPos(), state.setValue(TrackTiesBlock.POWERED, shouldBePowered), 3);
            }
        }

        if (oldPower != this.power) {
            setUpdated();
        }

        var prev = prev();
        if (prev != null) {
            prev.geometry.needsRebuild = true;
            prev.nextPower = this.power;
            prev.setUpdated();
        }
        var prevAlt = prevAlt();
        if (prevAlt != null) {
            prevAlt.geometry.needsRebuild = true;
            prevAlt.nextAltPower = this.power;
            prevAlt.setUpdated();
        }
    }

    public int power() {
        if (this.power < 0) {
            updatePower();
        }

        return this.power;
    }

    public void occupyFromCart() {
        if (isSwitch()) {
            return;
        }

        var world = getLevel();
        if (world == null || world.isClientSide()) {
            return;
        }

        this.detectorTicks = 20;
        var pos = getBlockPos();
        var state = world.getBlockState(pos);
        if (state.hasProperty(TrackTiesBlock.POWERED) && !state.getValue(TrackTiesBlock.POWERED)) {
            updatePower();
            world.setBlock(pos, state.setValue(TrackTiesBlock.POWERED, true), 3);
            world.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    public void tickDetector() {
        var world = getLevel();
        if (world == null || world.isClientSide()) {
            return;
        }

        if (isSwitch()) {
            var pos = getBlockPos();
            var state = world.getBlockState(pos);
            int redstoneIn = world.getBestNeighborSignal(pos);
            boolean shouldBePowered = redstoneIn > 0;
            if (state.hasProperty(TrackTiesBlock.POWERED) && state.getValue(TrackTiesBlock.POWERED) != shouldBePowered) {
                world.setBlock(pos, state.setValue(TrackTiesBlock.POWERED, shouldBePowered), 3);
            }
            if (this.power != redstoneIn) {
                this.power = redstoneIn;
                setUpdated();
            }
            return;
        }

        if (this.detectorTicks > 0) {
            this.detectorTicks--;
            return;
        }

        var pos = getBlockPos();
        var state = world.getBlockState(pos);
        if (state.hasProperty(TrackTiesBlock.POWERED) && state.getValue(TrackTiesBlock.POWERED)) {
            world.setBlock(pos, state.setValue(TrackTiesBlock.POWERED, false), 3);
            world.updateNeighbourForOutputSignal(pos, state.getBlock());
            updatePower();
        }
    }

    public void onDestroy() {
        if (getLevel() != null && !getLevel().isClientSide()) {
            if (this.next != null) {
                clearSupportsForNext();
            }
            if (this.nextAlt != null) {
                clearSupportsForNextAlt();
            }
            var prevE = prev();
            if (prevE != null) {
                prevE.clearSupportsForNext();
            }
            var prevAltE = prevAlt();
            if (prevAltE != null) {
                prevAltE.clearSupportsForNextAlt();
            }
        }

        if (this.prev != null) {
            this.dropTrack(this.prevType);
        }
        if (this.prevAlt != null) {
            this.dropTrack(this.prevAltType);
        }
        if (this.next != null) {
            this.dropTrack(this.nextType);
        }
        if (this.nextAlt != null) {
            this.dropTrack(this.nextAltType);
        }

        var prevE = prev();
        if (prevE != null) {
            if (getBlockPos().equals(prevE.next)) {
                prevE.next = null;
                prevE.nextBanking = false;
                prevE.nextSupports = SupportMode.AUTO;
                prevE.nextPose = null;
            } else if (getBlockPos().equals(prevE.nextAlt)) {
                prevE.nextAlt = null;
                prevE.nextAltBanking = false;
                prevE.nextAltSupports = SupportMode.AUTO;
                prevE.nextAltPose = null;
            }
            prevE.syncSwitchStateBlock();
            prevE.applySmoothing(true);
            prevE.setUpdated();
        }
        var prevAltE = prevAlt();
        if (prevAltE != null) {
            if (getBlockPos().equals(prevAltE.next)) {
                prevAltE.next = null;
                prevAltE.nextBanking = false;
                prevAltE.nextSupports = SupportMode.AUTO;
                prevAltE.nextPose = null;
            } else if (getBlockPos().equals(prevAltE.nextAlt)) {
                prevAltE.nextAlt = null;
                prevAltE.nextAltBanking = false;
                prevAltE.nextAltSupports = SupportMode.AUTO;
                prevAltE.nextAltPose = null;
            }
            prevAltE.syncSwitchStateBlock();
            prevAltE.applySmoothing(true);
            prevAltE.setUpdated();
        }
        var nextE = next();
        if (nextE != null) {
            if (getBlockPos().equals(nextE.prev)) {
                nextE.prev = null;
                nextE.prevPose = null;
            } else if (getBlockPos().equals(nextE.prevAlt)) {
                nextE.prevAlt = null;
                nextE.prevAltPose = null;
            }
            nextE.syncSwitchStateBlock();
            nextE.applySmoothing(true);
            nextE.setUpdated();
        }
        var nextAltE = nextAlt();
        if (nextAltE != null) {
            if (getBlockPos().equals(nextAltE.prev)) {
                nextAltE.prev = null;
                nextAltE.prevPose = null;
            } else if (getBlockPos().equals(nextAltE.prevAlt)) {
                nextAltE.prevAlt = null;
                nextAltE.prevAltPose = null;
            }
            nextAltE.syncSwitchStateBlock();
            nextAltE.applySmoothing(true);
            nextAltE.setUpdated();
        }
        syncSwitchStateBlock();
    }

    private BlockState getSupportBlockState(SplineSegment segment, SupportMode supports) {
        if (supports == SupportMode.STEEL) {
            return Blocks.IRON_BLOCK.defaultBlockState();
        }

        if (supports == SupportMode.METAL) {
            return Blocks.IRON_BARS.defaultBlockState();
        }

        if (supports == SupportMode.WOOD) {
            return woodenSupportState();
        }

        if (!segment.isHorizontal()) {
            return Blocks.IRON_BARS.defaultBlockState();
        }

        return woodenSupportState();
    }

    private boolean isSupportBlock(BlockState state, SupportMode supports) {
        if (supports == SupportMode.STEEL) {
            return state.is(Blocks.IRON_BLOCK);
        }
        return state.getBlock() instanceof FenceBlock || state.is(Blocks.IRON_BARS);
    }

    private BlockState woodenSupportState() {
        var level = getLevel();
        if (level != null) {
            var belowStart = getBlockPos().below();
            var startBelowState = level.getBlockState(belowStart);
            if (startBelowState.getBlock() instanceof FenceBlock) {
                return startBelowState.getBlock().defaultBlockState();
            }
            var nextE = next();
            if (nextE != null) {
                var belowEnd = nextE.getBlockPos().below();
                var endBelowState = level.getBlockState(belowEnd);
                if (endBelowState.getBlock() instanceof FenceBlock) {
                    return endBelowState.getBlock().defaultBlockState();
                }
            }
        }
        return Blocks.OAK_FENCE.defaultBlockState();
    }

    private void buildSupportsForNextAlt(SupportMode supports) {
        var nextAltE = nextAlt();
        if (nextAltE == null) {
            return;
        }
        var level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        var segment = new SplineSegment(this.pose(), nextAltE.pose(), this.nextAltBanking);
        double length = segment.estimatedLength(16);
        int steps = (int) Math.clamp(Math.ceil(length * 4), 1, 1000);
        if (steps == 0) {
            return;
        }

        var trackBlocks = new java.util.HashSet<BlockPos>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            var pos = new Vector3d();
            segment.interpolate(t, pos, new Matrix3d(), new Vector3d());
            trackBlocks.add(BlockPos.containing(pos.x, pos.y, pos.z));
        }

        trackBlocks.add(getBlockPos());
        trackBlocks.add(nextAltE.getBlockPos());

        if (supports == SupportMode.STEEL) {
            return;
        }

        var builtColumns = new java.util.HashSet<String>();
        var supportState = getSupportBlockState(segment, supports);

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            var pos = new Vector3d();
            segment.interpolate(t, pos, new Matrix3d(), new Vector3d());
            var trackPos = BlockPos.containing(pos.x, pos.y, pos.z);

            String columnKey = trackPos.getX() + "," + trackPos.getZ();
            if (builtColumns.contains(columnKey)) {
                continue;
            }
            builtColumns.add(columnKey);

            var checkPos = trackPos.below();
            while (true) {
                if (trackBlocks.contains(checkPos)) {
                    checkPos = checkPos.below();
                    continue;
                }

                var state = level.getBlockState(checkPos);
                if (state.isAir() || state.is(BlockTags.REPLACEABLE)) {
                    level.setBlock(checkPos, supportState, 3);
                    checkPos = checkPos.below();
                } else {
                    break;
                }
            }
        }
    }

    private void clearSupportsForNextAlt() {
        var nextAltE = nextAlt();
        if (nextAltE == null) {
            return;
        }
        var level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        var segment = new SplineSegment(this.pose(), nextAltE.pose(), this.nextAltBanking);
        double length = segment.estimatedLength(16);
        int steps = (int) Math.clamp(Math.ceil(length * 4), 1, 1000);
        if (steps == 0) {
            return;
        }

        var trackBlocks = new java.util.HashSet<BlockPos>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            var pos = new Vector3d();
            segment.interpolate(t, pos, new Matrix3d(), new Vector3d());
            trackBlocks.add(BlockPos.containing(pos.x, pos.y, pos.z));
        }

        trackBlocks.add(getBlockPos());
        trackBlocks.add(nextAltE.getBlockPos());

        var clearedColumns = new java.util.HashSet<String>();

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            var pos = new Vector3d();
            segment.interpolate(t, pos, new Matrix3d(), new Vector3d());
            var trackPos = BlockPos.containing(pos.x, pos.y, pos.z);

            String columnKey = trackPos.getX() + "," + trackPos.getZ();
            if (clearedColumns.contains(columnKey)) {
                continue;
            }
            clearedColumns.add(columnKey);

            var checkPos = trackPos.below();
            while (true) {
                if (trackBlocks.contains(checkPos)) {
                    checkPos = checkPos.below();
                    continue;
                }

                var state = level.getBlockState(checkPos);
                if (isSupportBlock(state, this.nextAltSupports)) {
                    level.setBlock(checkPos, Blocks.AIR.defaultBlockState(), 3);
                    checkPos = checkPos.below();
                } else {
                    break;
                }
            }
        }
    }

    private void buildSupportsForNext(SupportMode supports) {
        var nextE = next();
        if (nextE == null) {
            return;
        }
        var level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        var segment = new SplineSegment(this, nextE);
        double length = segment.estimatedLength(16);
        int steps = (int) Math.clamp(Math.ceil(length * 4), 1, 1000);
        if (steps == 0) {
            return;
        }

        var trackBlocks = new java.util.HashSet<BlockPos>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            var pos = new Vector3d();
            segment.interpolate(t, pos, new Matrix3d(), new Vector3d());
            trackBlocks.add(BlockPos.containing(pos.x, pos.y, pos.z));
        }

        trackBlocks.add(getBlockPos());
        trackBlocks.add(nextE.getBlockPos());

        if (supports == SupportMode.STEEL) {
            return;
        }

        var builtColumns = new java.util.HashSet<String>();
        var supportState = getSupportBlockState(segment, supports);

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            var pos = new Vector3d();
            segment.interpolate(t, pos, new Matrix3d(), new Vector3d());
            var trackPos = BlockPos.containing(pos.x, pos.y, pos.z);

            String columnKey = trackPos.getX() + "," + trackPos.getZ();
            if (builtColumns.contains(columnKey)) {
                continue;
            }
            builtColumns.add(columnKey);

            var checkPos = trackPos.below();
            while (true) {
                if (trackBlocks.contains(checkPos)) {
                    checkPos = checkPos.below();
                    continue;
                }

                var state = level.getBlockState(checkPos);
                if (state.isAir() || state.is(BlockTags.REPLACEABLE)) {
                    level.setBlock(checkPos, supportState, 3);
                    checkPos = checkPos.below();
                } else {
                    break;
                }
            }
        }
    }

    private double steelRunOffset() {
        double offset = 0.0;
        var previous = prev();
        int guard = 0;
        while (previous != null && previous.nextSupports == SupportMode.STEEL && guard++ < 512) {
            offset += previous.estimatedTrackLength();
            previous = previous.prev();
        }
        return offset;
    }

    private double[] computeSteelPillarTs(SplineSegment segment) {
        double length = segment.estimatedLength(24);
        if (length < 0.2) {
            return NO_STEEL_PILLARS;
        }

        double offset = steelRunOffset();
        double first = offset <= 1.0e-6 ? 0.0 : Math.ceil(offset / STEEL_PILLAR_SPACING) * STEEL_PILLAR_SPACING;
        if (first + 1.0e-6 < offset) {
            first += STEEL_PILLAR_SPACING;
        }
        if (first >= offset + length - 0.05) {
            return NO_STEEL_PILLARS;
        }

        int samples = (int) Math.clamp(Math.ceil(length * 4), 8, 256);
        var cumulative = new double[samples + 1];
        var previous = new Vector3d(segment.startPose().translation());
        var position = new Vector3d();
        var basis = new Matrix3d();
        var derivative = new Vector3d();
        for (int i = 1; i <= samples; i++) {
            segment.interpolate((double) i / samples, position, basis, derivative);
            cumulative[i] = cumulative[i - 1] + position.distance(previous);
            previous.set(position);
        }
        length = cumulative[samples];

        var ts = new java.util.ArrayList<Double>();
        int sample = 1;
        for (double global = first; global < offset + length - 0.05; global += STEEL_PILLAR_SPACING) {
            double target = global - offset;
            if (target < -1.0e-6 || target > length) {
                continue;
            }
            while (sample < samples && cumulative[sample] < target) {
                sample++;
            }
            double sectionLength = cumulative[sample] - cumulative[sample - 1];
            double local = sectionLength > 1.0e-8 ? (target - cumulative[sample - 1]) / sectionLength : 0.0;
            ts.add((sample - 1 + local) / samples);
        }
        if (ts.isEmpty()) {
            return NO_STEEL_PILLARS;
        }
        var result = new double[ts.size()];
        for (int i = 0; i < ts.size(); i++) {
            result[i] = ts.get(i);
        }
        return result;
    }

    private void clearSupportsForNext() {
        var nextE = next();
        if (nextE == null) {
            return;
        }
        var level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        var segment = new SplineSegment(this, nextE);
        double length = segment.estimatedLength(16);
        int steps = (int) Math.clamp(Math.ceil(length * 4), 1, 1000);
        if (steps == 0) {
            return;
        }

        var trackBlocks = new java.util.HashSet<BlockPos>();
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            var pos = new Vector3d();
            segment.interpolate(t, pos, new Matrix3d(), new Vector3d());
            trackBlocks.add(BlockPos.containing(pos.x, pos.y, pos.z));
        }

        trackBlocks.add(getBlockPos());
        trackBlocks.add(nextE.getBlockPos());

        var clearedColumns = new java.util.HashSet<String>();

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            var pos = new Vector3d();
            segment.interpolate(t, pos, new Matrix3d(), new Vector3d());
            var trackPos = BlockPos.containing(pos.x, pos.y, pos.z);

            String columnKey = trackPos.getX() + "," + trackPos.getZ();
            if (clearedColumns.contains(columnKey)) {
                continue;
            }
            clearedColumns.add(columnKey);

            var checkPos = trackPos.below();
            while (true) {
                if (trackBlocks.contains(checkPos)) {
                    checkPos = checkPos.below();
                    continue;
                }

                var state = level.getBlockState(checkPos);
                if (isSupportBlock(state, this.nextSupports)) {
                    level.setBlock(checkPos, Blocks.AIR.defaultBlockState(), 3);
                    checkPos = checkPos.below();
                } else {
                    break;
                }
            }
        }
    }

    public double estimatedTrackLength() {
        var nextEndpoint = renderNextEndpoint();
        if (nextEndpoint == null) {
            return 0;
        }

        return new Vector3d(this.pose().translation()).distance(new Vector3d(nextEndpoint.pose().translation()));
    }

    /**
     * Returns the next tie for rendering, falling back to the synchronized
     * endpoint snapshot when that tie's client chunk is not loaded.
     */
    public @Nullable RenderEndpoint renderNextEndpoint() {
        if (this.next == null) {
            return null;
        }

        var nextE = next();
        if (nextE != null) {
            this.nextPose = nextE.pose();
            this.nextPower = nextE.power();
            return new RenderEndpoint(nextE.getBlockPos(), nextE.pose(), this.nextType, nextE.power());
        }

        if (this.nextPose == null) {
            return null;
        }

        return new RenderEndpoint(this.next, this.nextPose, this.nextType, Math.max(this.nextPower, 0));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!getLevel().isClientSide()) {
            this.onDestroy();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();

        this.geometry.close();
    }

    public void setUpdated() {
        sync();
        setChanged();

        if (getLevel().isClientSide()) {
            this.geometry.needsRebuild = true;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        this.prev = input.read("prev", BlockPos.CODEC).orElse(null);
        this.prevAlt = input.read("prev_alt", BlockPos.CODEC).orElse(null);
        this.next = input.read("next", BlockPos.CODEC).orElse(null);
        this.nextAlt = input.read("next_alt", BlockPos.CODEC).orElse(null);
        this.prevPose = readPose(input, "prev_pose");
        this.prevAltPose = readPose(input, "prev_alt_pose");
        this.nextPose = readPose(input, "next_pose");
        this.nextAltPose = readPose(input, "next_alt_pose");
        this.nextPower = input.getIntOr("next_power", -1);
        this.nextAltPower = input.getIntOr("next_alt_power", -1);
        this.nextBanking = input.getBooleanOr("next_banking", false);
        this.nextAltBanking = input.getBooleanOr("next_alt_banking", false);
        this.smoothing = input.getBooleanOr("smoothing", true);
        this.nextSupports = SupportMode.fromSaved(input.getStringOr("next_supports", "auto"));
        this.nextAltSupports = SupportMode.fromSaved(input.getStringOr("next_alt_supports", "auto"));

        this.prevType = TrackType.read(input.getIntOr("prev_id", 0));
        this.prevAltType = TrackType.read(input.getIntOr("prev_alt_id", 0));
        this.nextType = TrackType.read(input.getIntOr("next_id", 0));
        this.nextAltType = TrackType.read(input.getIntOr("next_alt_id", 0));

        if (getBlockState().getBlock() instanceof TrackTiesBlock ties) {
            this.basePose = ties.getPose(getBlockState(), getBlockPos());
        }

        var savedPose = readPose(input, "pose");
        if (shouldSmoothTrack()) {
            this.pose = savedPose != null ? savedPose : computeSmoothedPose();
        } else {
            this.pose = this.basePose;
        }

        this.power = input.getIntOr("power", 0);
        if (getBlockState().hasProperty(TrackTiesBlock.POWERED) && getBlockState().getValue(TrackTiesBlock.POWERED)) {
            this.detectorTicks = 20;
        }

        this.geometry.needsRebuild = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        if (this.prev != null) {
            output.store("prev", BlockPos.CODEC, this.prev);
        }
        if (this.prevAlt != null) {
            output.store("prev_alt", BlockPos.CODEC, this.prevAlt);
        }
        if (this.next != null) {
            output.store("next", BlockPos.CODEC, this.next);
        }
        if (this.nextAlt != null) {
            output.store("next_alt", BlockPos.CODEC, this.nextAlt);
        }
        writePose(output, "prev_pose", this.prevPose);
        writePose(output, "prev_alt_pose", this.prevAltPose);
        writePose(output, "next_pose", this.nextPose);
        writePose(output, "next_alt_pose", this.nextAltPose);
        if (shouldSmoothTrack()) {
            writePose(output, "pose", this.pose());
        }
        output.putInt("next_power", this.nextPower);
        output.putInt("next_alt_power", this.nextAltPower);
        output.putBoolean("next_banking", this.nextBanking);
        output.putBoolean("next_alt_banking", this.nextAltBanking);
        output.putBoolean("smoothing", this.smoothing);
        output.putString("next_supports", this.nextSupports.id());
        output.putString("next_alt_supports", this.nextAltSupports.id());

        output.putInt("prev_id", this.prevType.write());
        output.putInt("prev_alt_id", this.prevAltType.write());
        output.putInt("next_id", this.nextType.write());
        output.putInt("next_alt_id", this.nextAltType.write());

        output.putInt("power", this.power);
    }

    private static @Nullable Pose readPose(ValueInput input, String key) {
        if (!input.getBooleanOr(key + "_present", false)) {
            return null;
        }

        var translation = new Vector3d(
                input.getDoubleOr(key + "_0", 0.0),
                input.getDoubleOr(key + "_1", 0.0),
                input.getDoubleOr(key + "_2", 0.0));
        var basis = new Matrix3d();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                basis.setRowColumn(row, column,
                        input.getDoubleOr(key + "_basis_" + row + "_" + column, row == column ? 1.0 : 0.0));
            }
        }
        return new Pose(translation, basis);
    }

    private static void writePose(ValueOutput output, String key, @Nullable Pose pose) {
        output.putBoolean(key + "_present", pose != null);
        if (pose == null) {
            return;
        }

        for (int axis = 0; axis < 3; axis++) {
            output.putDouble(key + "_" + axis, pose.translation().get(axis));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                output.putDouble(key + "_basis_" + row + "_" + column,
                        pose.basis().getRowColumn(row, column));
            }
        }
    }

    public record RenderEndpoint(BlockPos blockPos, Pose pose, TrackType type, int power) {
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    public void sync() {
        getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }
}
