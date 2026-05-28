package me.serenityline.api.finance.calendar;

public interface CurrencyBusinessCalendarResolver {

    BusinessCalendar resolveByCurrency(String currencyCode);

    int adjustmentWindowDays();
}