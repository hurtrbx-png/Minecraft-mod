package io.github.hurtrbxpng.giantess.entity.ai;

import io.github.hurtrbxpng.giantess.entity.GiantessEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Reads the ground under her footprint the way her own collision sees it, so trees she walks through
 * do not count as ground. Used to check a straight stroll before she takes it: vanilla A* would test every
 * block of her hitbox at every node, thousands of block reads per node at her size.
 */
public final class Footing {
	/** Deepest a column is scanned below the surface heightmap (covers tall jungle trees and deep water). */
	private static final int MAX_SCAN_DEPTH = 64;

	private final GiantessEntity giantess;
	private final Level level;
	private final CollisionContext context;
	private final Long2ObjectOpenHashMap<Column> columns = new Long2ObjectOpenHashMap<>();
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	/** Top of the ground in one block column, how much water stands on it, and whether there is lava. */
	private record Column(double groundY, int waterDepth, boolean lava) {
	}

	public Footing(GiantessEntity giantess) {
		this.giantess = giantess;
		this.level = giantess.level();
		this.context = CollisionContext.of(giantess);
	}

	/**
	 * Checks that she can walk in a straight line to {@code target}: every step up or down stays within her
	 * step height, the water stays shallow enough to wade, there is no lava and every chunk on the way is loaded and ticking.
	 *
	 * @return where she will stand at the target, or null if the line is not walkable
	 */
	public @Nullable Vec3 checkStraightWalk(Vec3 target, double maxStep, double maxWaterDepth) {
		Vec3 from = giantess.position();
		double dx = target.x - from.x;
		double dz = target.z - from.z;
		int steps = Math.max(1, Mth.ceil(Math.sqrt(dx * dx + dz * dz)));
		double previousY = from.y;
		double y = from.y;
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			Sample sample = sample(from.x + dx * t, from.z + dz * t);
			if (sample == null || sample.lava || sample.waterDepth > maxWaterDepth || Math.abs(sample.standY - previousY) > maxStep) {
				return null;
			}
			previousY = sample.standY;
			y = sample.standY;
		}
		return new Vec3(target.x, y, target.z);
	}

	private record Sample(double standY, int waterDepth, boolean lava) {
	}

	/** Her box rests on the highest ground under it; water depth is read at her center. */
	private @Nullable Sample sample(double x, double z) {
		double half = giantess.getBbWidth() / 2.0 - 0.01;
		int minX = Mth.floor(x - half);
		int maxX = Mth.floor(x + half);
		int minZ = Mth.floor(z - half);
		int maxZ = Mth.floor(z + half);
		double standY = Double.NEGATIVE_INFINITY;
		boolean lava = false;
		for (int bx = minX; bx <= maxX; bx++) {
			for (int bz = minZ; bz <= maxZ; bz++) {
				Column column = column(bx, bz);
				if (column == null) {
					return null;
				}
				standY = Math.max(standY, column.groundY);
				lava |= column.lava;
			}
		}
		Column center = column(Mth.floor(x), Mth.floor(z));
		return center == null ? null : new Sample(standY, center.waterDepth, lava);
	}

	private @Nullable Column column(int x, int z) {
		long key = BlockPos.asLong(x, 0, z);
		Column cached = columns.get(key);
		if (cached != null || columns.containsKey(key)) {
			return cached;
		}
		Column column = scanColumn(x, z);
		columns.put(key, column);
		return column;
	}

	private @Nullable Column scanColumn(int x, int z) {
		if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
			return null;
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
		// Never stroll into chunks where entities are frozen: she would stop mid-stride until a player comes near.
		if (level instanceof ServerLevel serverLevel && !serverLevel.isPositionEntityTicking(cursor.set(x, top, z))) {
			return null;
		}
		int bottom = Math.max(level.getMinY(), top - MAX_SCAN_DEPTH);
		int waterDepth = 0;
		boolean lava = false;
		for (int y = top; y >= bottom; y--) {
			cursor.set(x, y, z);
			BlockState state = level.getBlockState(cursor);
			VoxelShape shape = state.getCollisionShape(level, cursor, context);
			if (!shape.isEmpty()) {
				return new Column(y + shape.max(Direction.Axis.Y), waterDepth, lava);
			}
			FluidState fluid = state.getFluidState();
			if (fluid.is(FluidTags.LAVA)) {
				lava = true;
			} else if (fluid.is(FluidTags.WATER)) {
				waterDepth++;
			}
		}
		// No ground within reach (a bottomless drop or a huge tree): treat as a wall.
		return new Column(Double.POSITIVE_INFINITY, waterDepth, lava);
	}
}
