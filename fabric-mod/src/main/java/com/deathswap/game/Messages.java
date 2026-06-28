package com.deathswap.game;

import com.deathswap.util.Translator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Exact player-facing text from the datapack, in both supported languages
 * ({@code Lang Core} 1 = English, 2 = Dutch). Each method mirrors a specific
 * {@code tellraw}/{@code title} line from the {@code ds:game/*} functions so the
 * mod reproduces the same wording, colours and bold/italic styling.
 *
 * <p>Colours/booleans are taken verbatim from the source JSON. Where a line is
 * not gated on {@code Lang Core} in the datapack (e.g. the "You warped to" text),
 * the same single string is used for both languages.
 */
public final class Messages {

    private Messages() {
    }

    private static MutableComponent lit(String text, ChatFormatting color, boolean bold) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withBold(bold));
    }

    // ---- game/swap.mcfunction ----

    /** {@code >> Swapped! <<} / {@code >> Gewisseld! <<} (gold action bar). */
    public static Component swapActionbar(boolean nl) {
        return lit(Translator.translate(nl, ">> Swapped! <<"), ChatFormatting.GOLD, false);
    }

    /**
     * {@code * You warped to: <name>} — green/bold prefix, non-bold name. This
     * line is not gated on language in the datapack (English text for all).
     */
    public static Component warpedTo(boolean nl, Component targetName) {
        return lit(Translator.translate(nl, "* You warped to: "), ChatFormatting.GREEN, true)
                .append(targetName.copy().withStyle(s -> s.withBold(false)));
    }

    /** Dutch-only extra swap line: {@code * Je bent geteleporteerd naar de locatie van de speler ^^} (green). */
    public static Component warpedToDutch() {
        return lit("* Je bent geteleporteerd naar de locatie van de speler ^^", ChatFormatting.GREEN, false);
    }

    // ---- game/game_start.mcfunction ----

    /** Title: {@code >> D.S. But Way Better! <<} (gold) — same for both languages. */
    public static Component startTitle(boolean nl) {
        return lit(Translator.translate(nl, ">> D.S. But Way Better! <<"), ChatFormatting.GOLD, false);
    }

    /** Subtitle: {@code Created by Jerries!} / Dutch equivalent (yellow). */
    public static Component startSubtitle(boolean nl) {
        return lit(Translator.translate(nl, "Created by Jerries!"), ChatFormatting.YELLOW, false);
    }

    /** {@code Map created by Jerries (Map version 1.0.3)} / Dutch equivalent. */
    public static Component mapCredit(boolean nl) {
        return lit(Translator.translate(nl, "Map created by Jerries"), ChatFormatting.YELLOW, true)
                .append(Component.literal(Translator.translate(nl, " (Map version 1.0.3)"))
                        .withStyle(s -> s.withColor(ChatFormatting.YELLOW).withBold(false).withItalic(true)));
    }

    /** {@code Additional datapack work by TheWorfer27 and Melumi11} / Dutch (green italic). */
    public static Component additionalCredit(boolean nl) {
        return Component.literal(Translator.translate(nl, "Additional datapack work by TheWorfer27 and Melumi11"))
                .withStyle(s -> s.withColor(ChatFormatting.GREEN).withItalic(true));
    }

    // ---- game/warping_all.mcfunction ----

    /** {@code >> Spreading players... <<} / {@code >> Spelers verspreiden... <<} (gold action bar). */
    public static Component spreadingActionbar(boolean nl) {
        return lit(Translator.translate(nl, ">> Spreading players... <<"), ChatFormatting.GOLD, false);
    }

    // ---- game/player_died.mcfunction ----

    /**
     * Broadcast: {@code >> <vanilla death message> > Lost a life!} (dark_red/red,
     * bold). The death message is the vanilla localized line (e.g. "Steve was
     * slain by Zombie"), so the broadcast states how the player died.
     */
    public static Component diedBroadcast(boolean nl, Component deathMessage) {
        return lit(">> ", ChatFormatting.DARK_RED, true)
                .append(deathMessage.copy().withStyle(s -> s.withColor(ChatFormatting.RED).withBold(false)))
                .append(lit(Translator.translate(nl, " > Lost a life!"), ChatFormatting.RED, true));
    }

    /** Title: {@code >> YOU DIED! <<} / {@code >> JE BENT DOOD! <<} (red). */
    public static Component diedTitle(boolean nl) {
        return lit(Translator.translate(nl, ">> YOU DIED! <<"), ChatFormatting.RED, false);
    }

    /** Subtitle: {@code -1 Life!} / {@code -1 Leven!} (gold). */
    public static Component diedSubtitle(boolean nl) {
        return lit(Translator.translate(nl, "-1 Life!"), ChatFormatting.GOLD, false);
    }

    // ---- game/player_eliminated.mcfunction ----

    /** Subtitle: {@code >> ELIMINATED! <<} / {@code >> UITGESCHAKELD! <<} (red). */
    public static Component eliminatedSubtitle(boolean nl) {
        return lit(Translator.translate(nl, ">> ELIMINATED! <<"), ChatFormatting.RED, false);
    }

    // ---- game/winner.mcfunction ----

    /** Title: {@code >> <name> Won! <<} (green), name inherits the surrounding style. */
    public static Component winnerTitle(boolean nl, Component name) {
        return lit(">> ", ChatFormatting.GREEN, false)
                .append(name.copy())
                .append(lit(Translator.translate(nl, " Won! <<"), ChatFormatting.GREEN, false));
    }

    /** Subtitle (aqua): English / Dutch victory line. */
    public static Component winnerSubtitle(boolean nl) {
        return lit(Translator.translate(nl, "They survived the way better death swap!"),
                ChatFormatting.AQUA, false);
    }

    /**
     * Broadcast: {@code \n>>> <name> survived the longest and won the game! <<<\n}
     * (green; bold in English, non-bold in Dutch) / Dutch equivalent.
     */
    public static Component winnerBroadcast(boolean nl, Component name) {
        boolean bold = !nl; // EN line is bold, NL line is not (matches the source JSON)
        return lit("\n>>> ", ChatFormatting.GREEN, bold)
                .append(name.copy().withStyle(s -> s.withBold(false)))
                .append(lit(Translator.translate(nl, " survived the longest and won the game! <<<\n"),
                        ChatFormatting.GREEN, bold));
    }

    // ---- extra/make_newbie_spec.mcfunction (late joiner) ----

    /** Broadcast: {@code >> <name> joined the server mid-game ...} (gray/gold, bold). */
    public static Component joinedMidGame(boolean nl, Component name) {
        return lit(">> ", ChatFormatting.GRAY, true)
                .append(name.copy().withStyle(s -> s.withColor(ChatFormatting.GOLD).withBold(false)))
                .append(lit(Translator.translate(nl, " joined the server mid-game and will spectate until the game is over!"),
                        ChatFormatting.GRAY, true));
    }

    /** Title: {@code You will spectate} / {@code Je zult toeschouwer zijn} (gray). */
    public static Component spectateTitle(boolean nl) {
        return lit(Translator.translate(nl, "You will spectate"), ChatFormatting.GRAY, false);
    }

    /** Subtitle: {@code Until this game finishes!} / Dutch (white). */
    public static Component spectateSubtitle(boolean nl) {
        return lit(Translator.translate(nl, "Until this game finishes!"), ChatFormatting.WHITE, false);
    }

    // ---- settings/lang_dutch.mcfunction & lang_english.mcfunction ----

    /** Switch title: {@code TAAL: Engels!} / {@code LANGUAGE: English!} (light_purple). */
    public static Component langTitle(boolean toDutch) {
        return lit(Translator.translate(toDutch, "LANGUAGE: English!"), ChatFormatting.LIGHT_PURPLE, false);
    }

    /** Switch subtitle (plain white), bilingual either way. */
    public static Component langSubtitle(boolean toDutch) {
        return Component.literal(toDutch
                ? "Het spel wordt in het Nederlands gespeeld! / Game will be in Dutch!"
                : "Game will be in English! / Het spel wordt in het Engels gespeeld!");
    }

    /** Banner: {@code [=-=-=-= Language: English =-=-=-=]} / Dutch, all bold. */
    public static Component langBanner(boolean toDutch) {
        return lit("[=-=-=-= ", ChatFormatting.LIGHT_PURPLE, true)
                .append(lit(Translator.translate(toDutch, "Language: English"), ChatFormatting.WHITE, true))
                .append(lit(" =-=-=-=]", ChatFormatting.LIGHT_PURPLE, true));
    }

    /** Dutch-only translator note shown when switching to Dutch (white). */
    public static Component langTranslatorNote() {
        return Component.literal("Ik ben een moedertaalspreker van het Engels uit Californië. Het grootste deel van de inhoud van deze kaart is door mij naar het Nederlands vertaald. Ik heb alle vertalingen gecontroleerd op nauwkeurigheid, maar er kunnen enkele grammaticafouten zijn!")
                .withStyle(s -> s.withColor(ChatFormatting.WHITE));
    }

    /**
     * Item 72 broadcast: {@code >> <name> --> Switched the game's language ...}.
     * Green prefix/name/arrow, light_purple bilingual tail (order flips by target).
     */
    public static Component langSwitched(boolean toDutch, Component name) {
        String tail = toDutch
                ? "Switched the game's language to Dutch! De taal van het spel is overgeschakeld naar het Nederlands!"
                : "De taal van het spel is overgeschakeld naar het Engels! Switched the game's language to English!";
        return lit(">> ", ChatFormatting.GREEN, false)
                .append(name.copy().withStyle(s -> s.withBold(false)))
                .append(lit(" --> ", ChatFormatting.GREEN, false))
                .append(lit(tail, ChatFormatting.LIGHT_PURPLE, false));
    }

    // ---- items/give_items.mcfunction (new item offer) ----

    /** Subtitle: {@code >> New items! <<} / {@code >> Nieuwe voorwerpen! <<} (green). */
    public static Component newItemsSubtitle(boolean nl) {
        return lit(Translator.translate(nl, ">> New items! <<"), ChatFormatting.GREEN, false);
    }

    /** The "you got a new set of items" tellraw (green, bold), bilingual. */
    public static Component newItemsChat(boolean nl) {
        return lit(Translator.translate(nl,
                "<< You got a new set of items! They will expire in 45 seconds if you don't use one of them! You can only use one! >>"),
                ChatFormatting.GREEN, true);
    }

    /** Round-robin "items blocked" reminder ({@code ** Items blocked!}, red bold). */
    public static Component itemsBlocked(boolean nl) {
        return lit(Translator.translate(nl, "** Items blocked!"), ChatFormatting.RED, true);
    }

    // ---- game/clock.mcfunction (swap countdown) ----

    /** Title: {@code >> Swapping <<} / {@code >> Aan het wisselen <<} (gold). */
    public static Component swappingTitle(boolean nl) {
        return lit(Translator.translate(nl, ">> Swapping <<"), ChatFormatting.GOLD, false);
    }

    /**
     * Countdown subtitle for a given number of whole seconds remaining, matching
     * the exact strings/colours in clock.mcfunction.
     */
    public static Component swappingSubtitle(boolean nl, int seconds) {
        return switch (seconds) {
            case 60 -> lit(Translator.translate(nl, "In 1 minute"), ChatFormatting.YELLOW, false);
            case 30 -> lit(Translator.translate(nl, "In 30 seconds"), ChatFormatting.YELLOW, false);
            case 10 -> lit(Translator.translate(nl, "In 10 seconds"), ChatFormatting.YELLOW, false);
            case 5 -> lit(Translator.translate(nl, "In 5"), ChatFormatting.YELLOW, false);
            case 4 -> lit(Translator.translate(nl, "In 4"), ChatFormatting.YELLOW, false);
            case 3 -> lit(Translator.translate(nl, "In 3"), ChatFormatting.YELLOW, false);
            case 2 -> lit(Translator.translate(nl, "In 2"), ChatFormatting.GOLD, false);
            case 1 -> lit(Translator.translate(nl, "In 1"), ChatFormatting.RED, false);
            default -> lit(Translator.translate(nl, "In ") + seconds, ChatFormatting.YELLOW, false);
        };
    }
}
