package com.cobbletowers.instance;

/**
 * Stable V1 spatial contract for the shared Tower dimension.
 *
 * <p>These values describe layout only. No world/chunk work occurs here. The 192-block stride was
 * selected after measuring the supplied tower at 49 x 55 x 131 blocks, leaving substantial horizontal
 * isolation between private instances.
 */
public final class TowerDimensionContract {
    public static final String DIMENSION_ID = "cobbletowers:tower";
    public static final int DEFAULT_INSTANCE_STRIDE = 192;
    public static final int DEFAULT_MAX_INSTANCES = 20;
    public static final int STRUCTURE_BASE_Y = 32;
    public static final int TOWER_MIN_RELATIVE_X = -24;
    public static final int TOWER_MAX_RELATIVE_X = 24;
    public static final int TOWER_MIN_RELATIVE_Z = -30;
    public static final int TOWER_MAX_RELATIVE_Z = 24;
    public static final int TOWER_MIN_RELATIVE_Y = 0;
    public static final int TOWER_MAX_RELATIVE_Y = 130;

    private TowerDimensionContract() {}
}
