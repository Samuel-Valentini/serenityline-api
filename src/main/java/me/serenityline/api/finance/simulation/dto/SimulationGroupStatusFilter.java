package me.serenityline.api.finance.simulation.dto;

import java.util.Locale;

public enum SimulationGroupStatusFilter {

    ACTIVE,
    ARCHIVED,
    ALL;

    public static SimulationGroupStatusFilter from(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }

        try {
            return SimulationGroupStatusFilter.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "finance.simulationGroup.status.invalid",
                    exception
            );
        }
    }

    public boolean includeActive() {
        return this == ACTIVE || this == ALL;
    }

    public boolean includeArchived() {
        return this == ARCHIVED || this == ALL;
    }
}