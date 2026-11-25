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
 * remaining unit counts). Very large baskets (too many states or too deep a search) fall back to a greedy best-saving-first allocation.
 */
public final class PromotionEngine {

    /** A promotion applied {@code times} times; {@code consumed} and {@code savingsPence} are per application. */
    public record Application(Promotion promotion, Map<String, Integer> consumed, int savingsPence, int times) {
    }

    static final int MAX_STATES = 20_000;
    static final int MAX_DEPTH = 500;

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
        List<Application> applications = new ArrayList<>();
        for (List<String> group : independentGroups(counts.keySet())) {
            List<Promotion> relevant = promotions.stream()
                    .filter(p -> p.slots().stream().anyMatch(s -> group.stream().anyMatch(s::contains)))
                    .toList();
            Search search = new Search(group, prices, relevant);
            int[] state = group.stream().mapToInt(counts::get).toArray();
            try {
                applications.addAll(search.best(state, 0).applications);
            } catch (StateBudgetExceeded e) {
                applications.addAll(search.greedy(state));
            }
        }
        return applications;
    }

    /** Barcodes linked by a shared promotion; groups never compete for units, so each is searched on its own. */
    private List<List<String>> independentGroups(Set<String> barcodes) {
        Map<String, String> parent = new LinkedHashMap<>();
        barcodes.forEach(b -> parent.put(b, b));
        for (Promotion promotion : promotions) {
            String first = null;
            for (String barcode : barcodes) {
                if (promotion.slots().stream().anyMatch(s -> s.contains(barcode))) {
                    if (first == null) {
                        first = barcode;
                    } else {
                        parent.put(root(parent, barcode), root(parent, first));
                    }
                }
            }
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String barcode : barcodes) {
            groups.computeIfAbsent(root(parent, barcode), k -> new ArrayList<>()).add(barcode);
        }
        return List.copyOf(groups.values());
    }

    private static String root(Map<String, String> parent, String barcode) {
        while (!parent.get(barcode).equals(barcode)) {
            barcode = parent.get(barcode);
        }
        return barcode;
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
        private final List<Promotion> promotions;
        private final Map<String, Result> memo = new HashMap<>();

        Search(List<String> barcodes, Map<String, Integer> prices, List<Promotion> promotions) {
            this.barcodes = barcodes;
            this.unitPrices = barcodes.stream().mapToInt(prices::get).toArray();
            this.promotions = promotions;
        }

        Result best(int[] state, int depth) {
            String key = Arrays.toString(state);
            Result cached = memo.get(key);
            if (cached != null) {
                return cached;
            }
            if (memo.size() >= MAX_STATES || depth >= MAX_DEPTH) {
                throw new StateBudgetExceeded();
            }
            Result best = Result.NONE;
            for (Option option : options(state)) {
                Result rest = best(minus(state, option.take), depth + 1);
                int total = option.savingsPence + rest.savingsPence;
                if (total > best.savingsPence) {
                    List<Application> applications = new ArrayList<>();
                    applications.add(application(option, 1));
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
                // Options never gain savings as units are used, so the top option stays top until it runs out.
                int times = Integer.MAX_VALUE;
                for (int i = 0; i < top.take.length; i++) {
                    if (top.take[i] > 0) {
                        times = Math.min(times, remaining[i] / top.take[i]);
                    }
                }
                applications.add(application(top, times));
                for (int i = 0; i < remaining.length; i++) {
                    remaining[i] -= top.take[i] * times;
                }
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

        private Application application(Option option, int times) {
            Map<String, Integer> consumed = new LinkedHashMap<>();
            for (int i = 0; i < option.take.length; i++) {
                if (option.take[i] > 0) {
                    consumed.put(barcodes.get(i), option.take[i]);
                }
            }
            return new Application(option.promotion, Map.copyOf(consumed), option.savingsPence, times);
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
