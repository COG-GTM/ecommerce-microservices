package com.ibatulanand.tillservice.pricing.promotion;

import java.util.List;
import java.util.Set;

/** A basket promotion. Each application consumes whole units from the basket and charges a fixed price for them. */
public sealed interface Promotion permits Promotion.MultiBuy, Promotion.MealDeal {

    String id();

    String label();

    int pricePence();

    /** Adjustment code shown on the basket ({@code MULTIBUY} or {@code MEAL_DEAL}). */
    String code();

    /** Each slot is filled by one unit whose barcode is in that slot's set. */
    List<Set<String>> slots();

    /** "N for £X": any {@code quantity} units from {@code barcodes} (mix and match) for {@code pricePence}. */
    record MultiBuy(String id, String label, Set<String> barcodes, int quantity, int pricePence) implements Promotion {
        public MultiBuy {
            barcodes = Set.copyOf(barcodes);
            if (quantity < 2 || pricePence < 0 || barcodes.isEmpty()) {
                throw new IllegalArgumentException("Invalid multi-buy " + id);
            }
        }

        @Override
        public String code() {
            return "MULTIBUY";
        }

        @Override
        public List<Set<String>> slots() {
            return java.util.Collections.nCopies(quantity, barcodes);
        }
    }

    /** Meal deal: one main, one side and one drink for {@code pricePence}. */
    record MealDeal(String id, String label, Set<String> mains, Set<String> sides, Set<String> drinks,
                    int pricePence) implements Promotion {
        public MealDeal {
            mains = Set.copyOf(mains);
            sides = Set.copyOf(sides);
            drinks = Set.copyOf(drinks);
            if (pricePence < 0 || mains.isEmpty() || sides.isEmpty() || drinks.isEmpty()) {
                throw new IllegalArgumentException("Invalid meal deal " + id);
            }
        }

        @Override
        public String code() {
            return "MEAL_DEAL";
        }

        @Override
        public List<Set<String>> slots() {
            return List.of(mains, sides, drinks);
        }
    }
}
