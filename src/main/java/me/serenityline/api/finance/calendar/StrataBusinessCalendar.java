package me.serenityline.api.finance.calendar;

import com.opengamma.strata.basics.date.HolidayCalendar;

import java.time.LocalDate;
import java.util.Objects;

public final class StrataBusinessCalendar implements BusinessCalendar {

    private final HolidayCalendar holidayCalendar;

    public StrataBusinessCalendar(HolidayCalendar holidayCalendar) {
        this.holidayCalendar = Objects.requireNonNull(holidayCalendar, "holidayCalendar");
    }

    @Override
    public LocalDate previousOrSame(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return holidayCalendar.previousOrSame(date);
    }

    @Override
    public LocalDate nextOrSame(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return holidayCalendar.nextOrSame(date);
    }
}