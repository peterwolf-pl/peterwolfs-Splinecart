package io.github.foundationgames.splinecart.block;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.SupportMode;
import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.item.TrackItem;
import io.github.foundationgames.splinecart.util.Pose;
import io.github.foundationgames.splinecart.util.SplineSegment;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public class TrackTiesBlockEntity extends BlockEntity {
    private static final double STEEL_PILLAR_SPACING = 12.0;
    private static final double[] NO_STEEL_PILLARS = new double[0];

    public float clientTime = 0;
    public final TrackGeometry geometry;

    private TrackType nextType = TrackType.DEFAULT;
    private TrackType prevType = TrackType.DEFAULT;

    private BlockPos next;
    private BlockPos prev;
    private Pose pose;
    /** Snapshot used to render a long segment while the next chunk is unloaded on the client. */
    private @Nullable Pose nextPose;
    private int nextPower = -1;
    private boolean nextBanking;
    private SupportMode nextSupports = SupportMode.AUTO;

    private int power = -1;
    private int detectorTicks = 0;

    public TrackTiesBlockEntity(BlockPos pos, BlockState state) {
        super(Splinecart.TRACK_TIES_BE.get(), pos, state);
        updatePose(pos, state);

        this.geometry = TrackGeometry.CONSTRUCTOR.apply(this);
    }

    public void updatePose(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof TrackTiesBlock ties) {
            this.pose = ties.getPose(state, pos);
        } else {
            this.pose = new Pose(new Vector3d(), new Matrix3d().identity());
        }

        if (getLevel() != null && !getLevel().isClientSide() && this.prev != null) {
            var previous = prev();
            if (previous != null) {
                previous.nextPose = this.pose;
                previous.nextPower = this.power;
                previous.setUpdated();
            }
        }
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

    private void dropTrack(TrackType type) {
        var world = getLevel();
        var pos = Vec3.atCenterOf(getBlockPos());
        var item = new ItemEntity(world, pos.x(), pos.y(), pos.z(), new ItemStack(TrackItem.ITEMS_BY_TYPE.get(type)));

        world.addFreshEntity(item);
    }

    public void setNext(@Nullable BlockPos pos, @Nullable TrackType type) {
        setNext(pos, type, SupportMode.STEEL, false);
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
                oldNextE.prev = null;
                oldNextE.setUpdated();
            }
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
                nextE.prev = getBlockPos();
                if (type != null) {
                    nextE.prevType = type;
                }
                nextE.setUpdated();
            }

            if (getLevel() != null && !getLevel().isClientSide() && supports != SupportMode.NONE) {
                buildSupportsForNext(supports);
            }
        }

        setUpdated();
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

    public double[] steelPillarTs() {
        if (this.nextSupports != SupportMode.STEEL) {
            return NO_STEEL_PILLARS;
        }
        var endpoint = renderNextEndpoint();
        if (endpoint == null) {
            return NO_STEEL_PILLARS;
        }
        return computeSteelPillarTs(new SplineSegment(this.pose, endpoint.pose(), this.nextBanking));
    }

    public Pose pose() {
        return this.pose;
    }

    public void updatePower() {
        var world = getLevel();
        if (world == null) {
            return;
        }

        var state = world.getBlockState(getBlockPos());
        if (this.power >= 0 && state.hasProperty(TrackTiesBlock.POWERED) && state.getValue(TrackTiesBlock.POWERED)) {
            return;
        }

        int oldPower = this.power;
        this.power = world.getBestNeighborSignal(getBlockPos());

        if (oldPower != this.power) {
            setUpdated();
        }

        var prev = prev();
        if (prev != null) {
            prev.geometry.needsRebuild = true;
            prev.nextPower = this.power;
            prev.setUpdated();
        }
    }

    public int power() {
        if (this.power < 0) {
            updatePower();
        }

        return this.power;
    }

    public void occupyFromCart() {
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
            var prevE = prev();
            if (prevE != null) {
                prevE.clearSupportsForNext();
            }
        }

        if (this.prev != null) {
            this.dropTrack(this.prevType);
        }
        if (this.next != null) {
            this.dropTrack(this.nextType);
        }

        var prevE = prev();
        if (prevE != null) {
            prevE.next = null;
            prevE.nextBanking = false;
            prevE.nextSupports = SupportMode.AUTO;
            prevE.setUpdated();
        }
        var nextE = next();
        if (nextE != null) {
            nextE.prev = null;
            nextE.setUpdated();
        }
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

        return new Vector3d(this.pose.translation()).distance(new Vector3d(nextEndpoint.pose().translation()));
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
        this.next = input.read("next", BlockPos.CODEC).orElse(null);
        this.nextPose = readPose(input, "next_pose");
        this.nextPower = input.getIntOr("next_power", -1);
        this.nextBanking = input.getBooleanOr("next_banking", false);
        this.nextSupports = SupportMode.fromSaved(input.getStringOr("next_supports", "auto"));

        this.prevType = TrackType.read(input.getIntOr("prev_id", 0));
        this.nextType = TrackType.read(input.getIntOr("next_id", 0));

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
        if (this.next != null) {
            output.store("next", BlockPos.CODEC, this.next);
        }
        writePose(output, "next_pose", this.nextPose);
        output.putInt("next_power", this.nextPower);
        output.putBoolean("next_banking", this.nextBanking);
        output.putString("next_supports", this.nextSupports.id());

        output.putInt("prev_id", this.prevType.write());
        output.putInt("next_id", this.nextType.write());

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
                        pose.basis().get(row, column));
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
