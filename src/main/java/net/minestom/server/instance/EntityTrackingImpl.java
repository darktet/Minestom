package net.minestom.server.instance;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.utils.chunk.ChunkUtils;

import java.util.ArrayList;
import java.util.List;

final class EntityTrackingImpl {

    /**
     * Default tracking implementation storing entities per-chunk.
     */
    static final class PerChunk implements EntityTracking {
        private final Long2ObjectMap<List<Entity>> chunkEntities = new Long2ObjectOpenHashMap<>();

        @Override
        public void register(Entity entity, Point spawnPoint) {
            get(spawnPoint).add(entity);
        }

        @Override
        public void unregister(Entity entity, Point point) {
            get(point).remove(entity);
        }

        @Override
        public void move(Entity entity, Point oldPoint, Point newPoint, UpdateCallback callback) {
            if (!oldPoint.sameChunk(newPoint)) {
                get(oldPoint).remove(entity);
                get(newPoint).add(entity);
                difference(oldPoint, newPoint, callback);
            }
        }

        @Override
        public void difference(Point p1, Point p2, UpdateCallback callback) {
            final int range = MinecraftServer.getEntityViewDistance();
            // Remove
            ChunkUtils.forDifferingChunksInRange(p2.chunkX(), p2.chunkZ(), range, p1.chunkX(), p1.chunkZ(), range, chunkIndex -> {
                final List<Entity> entities = getOptional(chunkIndex);
                if (entities == null) return;
                for (Entity entity : entities) {
                    callback.remove(entity);
                }
            });
            // Add
            ChunkUtils.forDifferingChunksInRange(p1.chunkX(), p1.chunkZ(), range, p2.chunkX(), p2.chunkZ(), range, chunkIndex -> {
                final List<Entity> entities = getOptional(chunkIndex);
                if (entities == null) return;
                for (Entity entity : entities) {
                    callback.add(entity);
                }
            });
        }

        @Override
        public void nearbyEntities(Point point, double range, Query query) {
            final int minX = ChunkUtils.getChunkCoordinate(point.x() - range);
            final int maxX = ChunkUtils.getChunkCoordinate(point.x() + range);
            final int minZ = ChunkUtils.getChunkCoordinate(point.z() - range);
            final int maxZ = ChunkUtils.getChunkCoordinate(point.z() + range);
            // Cache squared range to prevent sqrt operations
            final double squaredRange = range * range;

            for (int x = minX; x <= maxX; ++x) {
                for (int z = minZ; z <= maxZ; ++z) {
                    final List<Entity> chunkEntities = getOptional(x, z);
                    if (chunkEntities == null || chunkEntities.isEmpty()) continue;
                    // Filter all entities out of range
                    for (Entity chunkEntity : chunkEntities) {
                        if (point.distanceSquared(chunkEntity.getPosition()) < squaredRange) {
                            query.consume(chunkEntity);
                        }
                    }
                }
            }
        }

        @Override
        public void chunkEntities(Point chunkPoint, Query query) {
            final List<Entity> entities = getOptional(chunkPoint);
            if (entities != null && !entities.isEmpty()) {
                for (Entity entity : entities) {
                    query.consume(entity);
                }
            }
        }

        @Override
        public void chunkRangeEntities(Point chunkPoint, int range, Query query) {
            final long[] chunksInRange = ChunkUtils.getChunksInRange(chunkPoint, range);
            for (long chunkIndex : chunksInRange) {
                final List<Entity> entities = getOptional(chunkIndex);
                if (entities == null || entities.isEmpty()) continue;
                for (Entity entity : entities) {
                    query.consume(entity);
                }
            }
        }

        private List<Entity> get(long index) {
            return chunkEntities.computeIfAbsent(index, l -> new ArrayList<>());
        }

        private List<Entity> get(int chunkX, int chunkZ) {
            return get(ChunkUtils.getChunkIndex(chunkX, chunkZ));
        }

        private List<Entity> get(Point point) {
            return get(point.chunkX(), point.chunkZ());
        }

        private List<Entity> getOptional(long index) {
            return chunkEntities.get(index);
        }

        private List<Entity> getOptional(int chunkX, int chunkZ) {
            return getOptional(ChunkUtils.getChunkIndex(chunkX, chunkZ));
        }

        private List<Entity> getOptional(Point point) {
            return getOptional(point.chunkX(), point.chunkZ());
        }
    }

}