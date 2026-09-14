package com.cobbletowers.structure;

import java.util.List;

/** Stable ordered IDs for canonical production Tower structure templates. */
public enum TowerStructureSection {
    FOUNDATION("foundation"),
    CORE("core"),
    FLOOR_01("floor_01"),
    FLOOR_02("floor_02"),
    FLOOR_03("floor_03"),
    FLOOR_04("floor_04"),
    FLOOR_05_BOSS("floor_05_boss"),
    FLOOR_06("floor_06"),
    FLOOR_07("floor_07"),
    FLOOR_08("floor_08"),
    FLOOR_09("floor_09"),
    FLOOR_10_CHAMPION("floor_10_champion"),
    DETAILS("details");

    private static final List<TowerStructureSection> ORDERED = List.of(values());

    private final String id;

    TowerStructureSection(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public int index() {
        return ordinal();
    }

    public static List<TowerStructureSection> ordered() {
        return ORDERED;
    }

    public static TowerStructureSection atIndex(int index) {
        if (index < 0 || index >= ORDERED.size()) {
            throw new IllegalArgumentException("structure section index out of bounds: " + index);
        }
        return ORDERED.get(index);
    }
}
