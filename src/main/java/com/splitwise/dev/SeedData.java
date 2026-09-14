package com.splitwise.dev;

import com.splitwise.split.SplitType;

import java.util.List;

/**
 * The shape of the dev dataset, kept separate from the seeding mechanics so
 * the numbers are easy to read and adjust.
 */
final class SeedData {

    static final String GROUP_NAME = "Palawan barkada trip";

    /** Dev-profile only. Never used outside the dev seeder. */
    static final String DEV_PASSWORD = "password123";
    static final String CURRENCY = "PHP";

    /** Indexes into {@link #PEOPLE}. */
    record Person(String email, String name) {
    }

    record SeedExpense(int daysAgo,
                       String description,
                       String amount,
                       SplitType splitType,
                       int payer,
                       List<Integer> participants,
                       List<String> values) {

        static SeedExpense equalSplit(int daysAgo, String description, String amount,
                                      int payer, List<Integer> participants) {
            return new SeedExpense(daysAgo, description, amount, SplitType.EQUAL,
                    payer, participants, null);
        }

        static SeedExpense exact(int daysAgo, String description, String amount,
                                 int payer, List<Integer> participants, List<String> values) {
            return new SeedExpense(daysAgo, description, amount, SplitType.EXACT,
                    payer, participants, values);
        }

        static SeedExpense percentage(int daysAgo, String description, String amount,
                                      int payer, List<Integer> participants, List<String> values) {
            return new SeedExpense(daysAgo, description, amount, SplitType.PERCENTAGE,
                    payer, participants, values);
        }
    }

    record SeedSettlement(int daysAgo, int from, int to, String amount) {
    }

    static final List<Person> PEOPLE = List.of(
            new Person("dev@splitwise.local", "Dev User"),
            new Person("ana@splitwise.local", "Ana Reyes"),
            new Person("miguel@splitwise.local", "Miguel Cruz"),
            new Person("joy@splitwise.local", "Joy Santos"));

    private static final List<Integer> EVERYONE = List.of(0, 1, 2, 3);

    static final List<SeedExpense> EXPENSES = List.of(
            SeedExpense.equalSplit(28, "Airbnb in El Nido", "7200.00", 0, EVERYONE),
            SeedExpense.equalSplit(26, "Grocery run", "2450.50", 1, EVERYONE),
            SeedExpense.exact(24, "Gas and tolls", "1800.00", 2,
                    List.of(0, 1, 2), List.of("600.00", "600.00", "600.00")),
            SeedExpense.percentage(22, "Dinner at Antonio's", "4380.00", 3,
                    EVERYONE, List.of("25", "25", "25", "25")),
            SeedExpense.equalSplit(20, "Coffee run", "560.00", 0, List.of(0, 1, 3)),
            SeedExpense.equalSplit(18, "Island hopping boat", "3000.00", 1, EVERYONE),
            SeedExpense.exact(16, "Snorkel gear rental", "1250.00", 2,
                    List.of(1, 2, 3), List.of("400.00", "425.00", "425.00")),
            SeedExpense.equalSplit(13, "Lunch at the market", "890.00", 3, EVERYONE),
            SeedExpense.percentage(10, "Souvenirs", "1675.00", 0,
                    List.of(0, 1, 2), List.of("40", "30", "30")),
            SeedExpense.equalSplit(7, "Tricycle fares", "340.00", 1, List.of(0, 1, 2)),
            SeedExpense.percentage(4, "Farewell dinner", "5200.00", 2,
                    EVERYONE, List.of("30", "30", "20", "20")),
            SeedExpense.equalSplit(1, "Airport transfer", "1500.00", 3, EVERYONE));

    static final List<SeedSettlement> SETTLEMENTS = List.of(
            new SeedSettlement(6, 1, 0, "1500.00"),
            new SeedSettlement(2, 3, 2, "900.00"));

    private SeedData() {
    }
}
