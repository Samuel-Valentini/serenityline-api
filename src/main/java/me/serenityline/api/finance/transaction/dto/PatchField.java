package me.serenityline.api.finance.transaction.dto;

public record PatchField<T>(
        boolean present,
        T value
) {

    public static <T> PatchField<T> omitted() {
        return new PatchField<>(false, null);
    }

    public static <T> PatchField<T> of(T value) {
        return new PatchField<>(true, value);
    }

    public boolean isPresent() {
        return present;
    }
}