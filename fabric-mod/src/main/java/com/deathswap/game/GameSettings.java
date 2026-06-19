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
        ENGLISH, CHINESE
    }

    /** Lives each player starts with (datapack supports 1, 3, 5; we allow 1..6). */
    public int maxLives = 3;

    /** Fixed swap interval in seconds (datapack {@code timeCycle}, 60..300). */
    public int swapIntervalSeconds = 120;

    /** When true, swap intervals are randomized between 30s and 4m59s. */
    public boolean randomCycle = true;

    public boolean pvp = true;
    public boolean hunger = true;
    public boolean showSwapTimer = true;
    public boolean startWithBasicTools = true;
    public boolean keepInventory = true;
    public boolean naturalRegen = true;

    public SwapWarning swapWarning = SwapWarning.TEN_SECONDS;
    public Language language = Language.ENGLISH;

    public boolean isChinese() {
        return language == Language.CHINESE;
    }

    public void clampToLegalValues() {
        maxLives = Math.max(1, Math.min(6, maxLives));
        swapIntervalSeconds = Math.max(30, Math.min(300, swapIntervalSeconds));
    }
}
