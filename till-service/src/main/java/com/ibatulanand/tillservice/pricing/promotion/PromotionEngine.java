package com.ibatulanand.tillservice.pricing.promotion;

import com.ibatulanand.tillservice.domain.BasketLine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the set of promotion applications that gives the customer the biggest total saving. A unit can count towards
 * at most one application, so when items qualify for several offers every combination is searched (memoised on the
 * remaining unit counts). Very large baskets fall back to a greedy best-saving-first allocation.
 */
public final class PromotionEngine {

    /** One application of a promotion: the units it consumed (barcode to count) and what it saves. */
    public record Application(Promotion promotion, Map<String, Integer> consumed, int savingsPence) {
    }

    static final int MAX_STATES = 20_000;

    private final List<Promotion> promotions;

    public PromotionEngine(List<Promotion> promotions) {
        this.promotions = List.copyOf(promotions);
    }

    public List<Application> bestApplications(List<BasketLine> lines) {
        Map<String, Integer> prices = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BasketLine line : lines) {
            if (line.getQuantity() > 0 && isPromoted(line.getBarcode())) {
                prices.putIfAbsent(line.getBarcode(), line.getUnitPricePence());
                counts.merge(line.getBarcode(), line.getQuantity(), Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return List.of();
        }
        Search search = new Search(List.copyOf(counts.keySet()), prices);
        int[] state = counts.values().stream().mapToInt(Integer::intValue).toArray();
        try {
            return search.best(state).applications;
        } catch (StateBudgetExceeded e) {
            return search.greedy(state);
        }
    }

    private boolean isPromoted(String barcode) {
        return promotions.stream().anyMatch(p -> p.slots().stream().anyMatch(s -> s.contains(barcode)));
    }

    private record Option(Promotion promotion, int[] take, int savingsPence) {
    }

    private record Result(int savingsPence, List<Application> applications) {
        static final Result NONE = new Result(0, List.of());
    }

    private static final class StateBudgetExceeded extends RuntimeException {
        StateBudgetExceeded() {
            super(null, null, false, false);
        }
    }

    private final class Search {
        private final List<String> barcodes;
        private final int[] unitPrices;
        private final Map<String, Result> memo = new HashMap<>();

        Search(List<String> barcodes, Map<String, Integer> prices) {
            this.barcodes = barcodes;
            this.unitPrices = barcodes.stream().mapToInt(prices::get).toArray();
        }

        Result best(int[] state) {
            String key = Arrays.toString(state);
            Result cached = memo.get(key);
            if (cached != null) {
                return cached;
            }
            if (memo.size() >= MAX_STATES) {
                throw new StateBudgetExceeded();
            }
            Result best = Result.NONE;
            for (Option option : options(state)) {
                Result rest = best(minus(state, option.take));
                int total = option.savingsPence + rest.savingsPence;
                if (total > best.savingsPence) {
                    List<Application> applications = new ArrayList<>();
                    applications.add(application(option));
                    applications.addAll(rest.applications);
                    best = new Result(total, List.copyOf(applications));
                }
            }
            memo.put(key, best);
            return best;
        }

        List<Application> greedy(int[] state) {
            List<Application> applications = new ArrayList<>();
            int[] remaining = state.clone();
            while (true) {
                Option top = null;
                for (Option option : options(remaining)) {
                    if (top == null || option.savingsPence > top.savingsPence) {
                        top = option;
                    }
                }
                if (top == null) {
                    return applications;
                }
                applications.add(application(top));
                remaining = minus(remaining, top.take);
            }
        }

        /** Every distinct way each promotion can be applied once to the remaining units, with a positive saving. */
        private List<Option> options(int[] state) {
            List<Option> options = new ArrayList<>();
            for (Promotion promotion : promotions) {
                List<Set<String>> slots = promotion.slots();
                Map<String, int[]> takes = new LinkedHashMap<>();
                fill(slots, 0, -1, new int[state.length], state, takes);
                for (int[] take : takes.values()) {
                    int full = 0;
                    for (int i = 0; i < take.length; i++) {
                        full += take[i] * unitPrices[i];
                    }
                    int saving = full - promotion.pricePence();
                    if (saving > 0) {
                        options.add(new Option(promotion, take, saving));
                    }
                }
            }
            return options;
        }

        private void fill(List<Set<String>> slots, int slot, int previous, int[] take, int[] state,
                          Map<String, int[]> takes) {
            if (slot == slots.size()) {
                takes.putIfAbsent(Arrays.toString(take), take.clone());
                return;
            }
            // Identical consecutive slots (N for £X) are filled in non-decreasing barcode order to skip permutations.
            boolean sameAsPrevious = slot > 0 && slots.get(slot).equals(slots.get(slot - 1));
            for (int i = sameAsPrevious ? previous : 0; i < barcodes.size(); i++) {
                if (take[i] < state[i] && slots.get(slot).contains(barcodes.get(i))) {
                    take[i]++;
                    fill(slots, slot + 1, i, take, state, takes);
                    take[i]--;
                }
            }
        }

        private Application application(Option option) {
            Map<String, Integer> consumed = new LinkedHashMap<>();
            for (int i = 0; i < option.take.length; i++) {
                if (option.take[i] > 0) {
                    consumed.put(barcodes.get(i), option.take[i]);
                }
            }
            return new Application(option.promotion, Map.copyOf(consumed), option.savingsPence);
        }

        private static int[] minus(int[] state, int[] take) {
            int[] next = state.clone();
            for (int i = 0; i < next.length; i++) {
                next[i] -= take[i];
            }
            return next;
        }
    }
}
