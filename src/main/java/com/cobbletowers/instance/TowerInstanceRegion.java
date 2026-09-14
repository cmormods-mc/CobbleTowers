package com.cobbletowers.instance;

/**
 * Immutable horizontal ownership bounds for one private Tower cell.
 *
 * <p>The physical tower origin sits inside this region. Gameplay protection, teleport-boundary
 * enforcement, structure ownership, and later chunk preparation can all use the same bounds rather
 * than re-deriving them independently.
 */
public record TowerInstanceRegion(
        TowerInstanceSlot slot,
        int minX,
        int maxX,
        int minZ,
        int maxZ
) {
    public TowerInstanceRegion {
        if (slot == null) throw new NullPointerException("slot");
        if (maxX < minX) throw new IllegalArgumentException("maxX must be >= minX");
        if (maxZ < minZ) throw new IllegalArgumentException("maxZ must be >= minZ");
        if (!contains(slot.originX(), slot.originZ())) {
            throw new IllegalArgumentException("Tower origin must be inside its instance region");
        }
    }

    public boolean contains(double x, double z) {
        return x >= minX && x <= maxX + 1.0 && z >= minZ && z <= maxZ + 1.0;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }
}
