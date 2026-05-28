package me.serenityline.api.finance.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "serenityline.finance")
public class FinanceProperties {

    private int maxSimulationGroupIds = 50;

    public int getMaxSimulationGroupIds() {
        return maxSimulationGroupIds;
    }

    public void setMaxSimulationGroupIds(int maxSimulationGroupIds) {
        if (maxSimulationGroupIds <= 0) {
            throw new IllegalArgumentException("finance.maxSimulationGroupIdsInvalid");
        }

        this.maxSimulationGroupIds = maxSimulationGroupIds;
    }
}