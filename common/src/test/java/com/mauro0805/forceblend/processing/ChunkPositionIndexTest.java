package com.mauro0805.forceblend.processing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ChunkPositionIndexTest {
    private static ChunkPositionIndex filledSquare(int minimumX, int minimumZ, int sideLength) {
        ChunkPositionIndex terrain = new ChunkPositionIndex();
        for (int offsetZ = 0; offsetZ < sideLength; offsetZ++) {
            for (int offsetX = 0; offsetX < sideLength; offsetX++) {
                terrain.add(minimumX + offsetX, minimumZ + offsetZ);
            }
        }
        return terrain;
    }

    @Test
    void selectsASevenChunkBandAndLeavesTheDeepInteriorUnselected() {
        ChunkPositionIndex terrain = filledSquare(0, 0, 32);

        ChunkPositionIndex frontier = terrain.selectSquareFrontier(7);

        assertEquals(700, frontier.size());
        assertTrue(frontier.contains(6, 16));
        assertTrue(frontier.contains(25, 16));
        assertFalse(frontier.contains(7, 7));
        assertFalse(frontier.contains(24, 24));
    }

    @Test
    void selectsEveryChunkOfASmallImportedIsland() {
        ChunkPositionIndex terrain = filledSquare(100, -80, 5);

        ChunkPositionIndex frontier = terrain.selectSquareFrontier(7);

        assertEquals(25, frontier.size());
    }

    @Test
    void selectsTerrainAroundAnInternalDeletedCavity() {
        ChunkPositionIndex terrain = new ChunkPositionIndex();
        for (int chunkZ = 0; chunkZ < 48; chunkZ++) {
            for (int chunkX = 0; chunkX < 48; chunkX++) {
                if (chunkX != 24 || chunkZ != 24) {
                    terrain.add(chunkX, chunkZ);
                }
            }
        }

        ChunkPositionIndex frontier = terrain.selectSquareFrontier(7);

        assertTrue(frontier.contains(17, 17));
        assertTrue(frontier.contains(31, 31));
        assertFalse(frontier.contains(16, 16));
        assertFalse(frontier.contains(32, 32));
    }

    @Test
    void indexesNegativeAndExtremeChunkCoordinates() {
        ChunkPositionIndex terrain = new ChunkPositionIndex();
        terrain.add(-1, -1);
        terrain.add(Integer.MIN_VALUE, Integer.MAX_VALUE);

        assertTrue(terrain.contains(-1, -1));
        assertTrue(terrain.contains(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertFalse(terrain.contains((long) Integer.MIN_VALUE - 1, Integer.MAX_VALUE));
        assertEquals(2, terrain.selectSquareFrontier(7).size());
    }
}
