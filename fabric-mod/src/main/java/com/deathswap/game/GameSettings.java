package com.deathswap.game;

/**
 * Configurable game rules, mirroring the datapack's {@code settings/} functions
 * and the {@code Core} scoreboard constants ({@code maxLives}, {@code timeCycle},
 * {@code warnLvl}, {@code randomCycle}, {@code pvp}, {@code noHunger}, ...).
 */
public final class GameSettings {

    /** Warning that precedes a swap, in seconds. Datapack {@code warnLvl} 1..4. */
    public enum SwapWarning {
        FIVE_SECONDS(5),
        TEN_SECONDS(10),
        THIRTY_SECONDS(30),
        ONE_MINUTE(60);

        public final int seconds;

        SwapWarning(int seconds) {
            this.seconds = seconds;
        }

        /** Datapack {@code warnLvl}: FIVE=1, TEN=2, THIRTY=3, ONE_MINUTE=4. */
        public int level() {
            return ordinal() + 1;
        }
    }

    public enum Language {
        ENGLISH, DUTCH
    }

    /** Lives each player starts with (datapack supports 1, 3, 5; we allow 1..6). */
    public int maxLives = 3;

    /** Fixed swap interval in seconds (datapack {@code timeCycle}, 60..300). */
    public int swapIntervalSeconds = 120;

    /**
     * Delay before the very first swap, in seconds. The opening cycle is fixed at
     * this length regardless of {@link #randomCycle}, giving players time to gear
     * up before swaps begin.
     */
    public int firstSwapSeconds = 180;

    /** When true, swap intervals are randomized between 30s and 4m59s. */
    public boolean randomCycle = true;

    public boolean pvp = true;
    public boolean hunger = true;
    public boolean showSwapTimer = true;
    public boolean startWithBasicTools = true;
    public boolean naturalRegen = true;
    public boolean keepInventory = false;

    public SwapWarning swapWarning = SwapWarning.TEN_SECONDS;

    public Language language = Language.ENGLISH;

    public boolean isDutch() {
        return language == Language.DUTCH;
    }

    /**
     * @throws IllegalStateException if any numeric setting is outside its legal range.
     */
    public void validate() {
        requireInRange("lives", maxLives, 1, 6);
        requireInRange("swap interval (seconds)", swapIntervalSeconds, 30, 300);
        requireInRange("first swap (seconds)", firstSwapSeconds, 30, 600);
        if (swapWarning == null) {
            throw new IllegalStateException("swap warning must be set");
        }
    }

    private static void requireInRange(String name, int value, int minInclusive, int maxInclusive) {
        if (value < minInclusive || value > maxInclusive) {
            throw new IllegalStateException(
                    name + " must be between " + minInclusive + " and " + maxInclusive
                            + ", but was " + value);
        }
    }
}
