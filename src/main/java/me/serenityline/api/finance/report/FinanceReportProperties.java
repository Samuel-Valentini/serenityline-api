package me.serenityline.api.finance.report;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "serenityline.finance.report")
public class FinanceReportProperties {

    private int extremesPastMonths = 36;
    private int extremesFutureMonths = 36;
    private int yearEndForecastYears = 10;
    private int trendMinDays = 90;

    public int getExtremesPastMonths() {
        return extremesPastMonths;
    }

    public void setExtremesPastMonths(int extremesPastMonths) {
        if (extremesPastMonths < 0) {
            throw new IllegalArgumentException("finance.report.extremesPastMonthsInvalid");
        }

        this.extremesPastMonths = extremesPastMonths;
    }

    public int getExtremesFutureMonths() {
        return extremesFutureMonths;
    }

    public void setExtremesFutureMonths(int extremesFutureMonths) {
        if (extremesFutureMonths < 0) {
            throw new IllegalArgumentException("finance.report.extremesFutureMonthsInvalid");
        }

        this.extremesFutureMonths = extremesFutureMonths;
    }

    public int getYearEndForecastYears() {
        return yearEndForecastYears;
    }

    public void setYearEndForecastYears(int yearEndForecastYears) {
        if (yearEndForecastYears < 0 || yearEndForecastYears > 30) {
            throw new IllegalArgumentException("finance.report.yearEndForecastYearsInvalid");
        }

        this.yearEndForecastYears = yearEndForecastYears;
    }

    public int getTrendMinDays() {
        return trendMinDays;
    }

    public void setTrendMinDays(int trendMinDays) {
        if (trendMinDays <= 0) {
            throw new IllegalArgumentException("finance.report.trendMinDaysInvalid");
        }

        this.trendMinDays = trendMinDays;
    }
}