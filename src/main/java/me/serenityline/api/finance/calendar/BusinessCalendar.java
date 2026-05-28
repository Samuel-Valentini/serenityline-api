package me.serenityline.api.finance.calendar;

import java.time.LocalDate;

public interface BusinessCalendar {

    LocalDate previousOrSame(LocalDate date);

    LocalDate nextOrSame(LocalDate date);
}