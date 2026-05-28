package me.serenityline.api.finance.calendar;

import java.time.LocalDate;

public interface PaymentBusinessCalendarProvider {

    BusinessCalendar calendarAt(LocalDate logicalDate);

    int adjustmentWindowDays();
}