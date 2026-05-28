package me.serenityline.api.finance.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

public final class WeekendOnlyBusinessCalendar implements BusinessCalendar {

    public static final WeekendOnlyBusinessCalendar INSTANCE = new WeekendOnlyBusinessCalendar();

    private WeekendOnlyBusinessCalendar() {
    }

    @Override
    public LocalDate previousOrSame(LocalDate date) {
        Objects.requireNonNull(date, "date");

        LocalDate adjusted = date;

        while (isWeekend(adjusted)) {
            adjusted = adjusted.minusDays(1);
        }

        return adjusted;
    }

    @Override
    public LocalDate nextOrSame(LocalDate date) {
        Objects.requireNonNull(date, "date");

        LocalDate adjusted = date;

        while (isWeekend(adjusted)) {
            adjusted = adjusted.plusDays(1);
        }

        return adjusted;
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
}