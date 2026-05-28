package me.serenityline.api.finance.calendar;

import com.opengamma.strata.basics.ReferenceData;
import com.opengamma.strata.basics.date.HolidayCalendarId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class StrataCurrencyBusinessCalendarResolver implements CurrencyBusinessCalendarResolver {

    private static final int STRATA_ADJUSTMENT_WINDOW_DAYS = 14;

    private static final Map<String, String> CURRENCY_TO_CALENDAR_CODE = Map.of(
            "EUR", "EUTA",
            "USD", "NYFD",
            "GBP", "GBLO",
            "CHF", "CHZU",
            "JPY", "JPTO"
    );

    private final Map<String, BusinessCalendar> cache = new ConcurrentHashMap<>();

    public BusinessCalendar resolveByCurrency(String currencyCode) {
        String calendarCode = calendarCodeForCurrency(currencyCode);

        if (calendarCode == null) {
            return WeekendOnlyBusinessCalendar.INSTANCE;
        }

        return cache.computeIfAbsent(calendarCode, this::resolveByCalendarCode);
    }

    public PaymentBusinessCalendarProvider providerForCurrency(String currencyCode) {
        BusinessCalendar calendar = resolveByCurrency(currencyCode);

        return new PaymentBusinessCalendarProvider() {
            @Override
            public BusinessCalendar calendarAt(LocalDate logicalDate) {
                Objects.requireNonNull(logicalDate, "logicalDate");
                return calendar;
            }

            @Override
            public int adjustmentWindowDays() {
                return StrataCurrencyBusinessCalendarResolver.this.adjustmentWindowDays();
            }
        };
    }

    public PaymentBusinessCalendarProvider providerForCurrencyAt(
            Function<LocalDate, String> currencyAt
    ) {
        Objects.requireNonNull(currencyAt, "currencyAt");

        return new PaymentBusinessCalendarProvider() {
            @Override
            public BusinessCalendar calendarAt(LocalDate logicalDate) {
                Objects.requireNonNull(logicalDate, "logicalDate");
                return resolveByCurrency(currencyAt.apply(logicalDate));
            }

            @Override
            public int adjustmentWindowDays() {
                return StrataCurrencyBusinessCalendarResolver.this.adjustmentWindowDays();
            }
        };
    }

    private String calendarCodeForCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return null;
        }

        String normalizedCurrencyCode = currencyCode
                .trim()
                .toUpperCase(Locale.ROOT);

        return CURRENCY_TO_CALENDAR_CODE.get(normalizedCurrencyCode);
    }

    private BusinessCalendar resolveByCalendarCode(String calendarCode) {
        return new StrataBusinessCalendar(
                HolidayCalendarId.of(calendarCode)
                        .resolve(ReferenceData.standard())
        );
    }

    @Override
    public int adjustmentWindowDays() {
        return STRATA_ADJUSTMENT_WINDOW_DAYS;
    }
}