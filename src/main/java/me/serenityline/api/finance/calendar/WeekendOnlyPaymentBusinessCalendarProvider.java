package me.serenityline.api.finance.calendar;

import java.time.LocalDate;
import java.util.Objects;

public final class WeekendOnlyPaymentBusinessCalendarProvider implements PaymentBusinessCalendarProvider {

    public static final WeekendOnlyPaymentBusinessCalendarProvider INSTANCE =
            new WeekendOnlyPaymentBusinessCalendarProvider();

    private static final int WEEKEND_ADJUSTMENT_WINDOW_DAYS = 2;

    private WeekendOnlyPaymentBusinessCalendarProvider() {
    }

    @Override
    public BusinessCalendar calendarAt(LocalDate logicalDate) {
        Objects.requireNonNull(logicalDate, "logicalDate");
        return WeekendOnlyBusinessCalendar.INSTANCE;
    }

    @Override
    public int adjustmentWindowDays() {
        return WEEKEND_ADJUSTMENT_WINDOW_DAYS;
    }
}