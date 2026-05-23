package me.serenityline.api.finance.category.service;

import java.util.List;

public final class DefaultCategoryDefinitions {

    private static final List<DefaultCategoryDefinition> DEFINITIONS = List.of(
            definition("rent"),
            definition("food-and-household"),
            definition("other-income"),
            definition("insurance"),
            definition("vehicle"),
            definition("charity"),
            definition("essential-recurring-services"),
            definition("home"),
            definition("family"),
            definition("small-expense-funds"),
            definition("education"),
            definition("investments"),
            definition("work"),
            definition("loans-and-mortgages"),
            definition("supplementary-pension"),
            definition("personal-care"),
            definition("health"),
            definition("direct-taxes"),
            definition("indirect-taxes"),
            definition("holidays")
    );

    private DefaultCategoryDefinitions() {
    }

    public static List<DefaultCategoryDefinition> all() {
        return DEFINITIONS;
    }

    private static DefaultCategoryDefinition definition(String code) {
        return new DefaultCategoryDefinition(
                "finance.default-category." + code + ".name",
                "finance.default-category." + code + ".description"
        );
    }
}