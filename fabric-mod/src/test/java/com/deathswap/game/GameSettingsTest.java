package com.deathswap.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameSettingsTest {

    @Test
    void defaultSettingsAreValid() {
        assertDoesNotThrow(() -> new GameSettings().validate());
    }

    @Test
    void maxLivesBelowMinimumFails() {
        GameSettings s = new GameSettings();
        s.maxLives = 0;
        assertThrows(IllegalStateException.class, s::validate);
    }

    @Test
    void maxLivesAboveMaximumFails() {
        GameSettings s = new GameSettings();
        s.maxLives = 7;
        assertThrows(IllegalStateException.class, s::validate);
    }

    @Test
    void maxLivesBoundariesAreAccepted() {
        GameSettings s = new GameSettings();
        s.maxLives = 1;
        assertDoesNotThrow(s::validate);
        s.maxLives = 6;
        assertDoesNotThrow(s::validate);
    }

    @Test
    void swapIntervalBelowMinimumFails() {
        GameSettings s = new GameSettings();
        s.swapIntervalSeconds = 29;
        assertThrows(IllegalStateException.class, s::validate);
    }

    @Test
    void swapIntervalAboveMaximumFails() {
        GameSettings s = new GameSettings();
        s.swapIntervalSeconds = 301;
        assertThrows(IllegalStateException.class, s::validate);
    }

    @Test
    void firstSwapBelowMinimumFails() {
        GameSettings s = new GameSettings();
        s.firstSwapSeconds = 29;
        assertThrows(IllegalStateException.class, s::validate);
    }

    @Test
    void firstSwapAboveMaximumFails() {
        GameSettings s = new GameSettings();
        s.firstSwapSeconds = 601;
        assertThrows(IllegalStateException.class, s::validate);
    }

    @Test
    void nullSwapWarningFails() {
        GameSettings s = new GameSettings();
        s.swapWarning = null;
        assertThrows(IllegalStateException.class, s::validate);
    }

    @Test
    void swapWarningLevelMatchesOrdinal() {
        assertEquals(1, GameSettings.SwapWarning.FIVE_SECONDS.level());
        assertEquals(2, GameSettings.SwapWarning.TEN_SECONDS.level());
        assertEquals(3, GameSettings.SwapWarning.THIRTY_SECONDS.level());
        assertEquals(4, GameSettings.SwapWarning.ONE_MINUTE.level());
    }
}
