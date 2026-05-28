package me.serenityline.api.finance.calendar;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "serenityline.finance.calendar")
public class FinanceCalendarProperties {

    private long maxRangeDays = 1830L;
    private int maxRecurringTransactions = 500;
    private int maxAccountIds = 50;

    public long getMaxRangeDays() {
        return maxRangeDays;
    }

    public void setMaxRangeDays(long maxRangeDays) {
        if (maxRangeDays <= 0) {
            throw new IllegalArgumentException("finance.calendar.maxRangeDaysInvalid");
        }

        this.maxRangeDays = maxRangeDays;
    }

    public int getMaxRecurringTransactions() {
        return maxRecurringTransactions;
    }

    public void setMaxRecurringTransactions(int maxRecurringTransactions) {
        if (maxRecurringTransactions <= 0) {
            throw new IllegalArgumentException("finance.calendar.maxRecurringTransactionsInvalid");
        }

        this.maxRecurringTransactions = maxRecurringTransactions;
    }

    public int getMaxAccountIds() {
        return maxAccountIds;
    }

    public void setMaxAccountIds(int maxAccountIds) {
        this.maxAccountIds = maxAccountIds;
    }
}