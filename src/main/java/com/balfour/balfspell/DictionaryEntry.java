package com.balfour.balfspell;

/** A normalised dictionary word and its optional frequency. */
public final class DictionaryEntry {
    private final String word; private final long frequency;
    public DictionaryEntry(String word, long frequency) { this.word = word; this.frequency = Math.max(0, frequency); }
    public String word() { return word; } public long frequency() { return frequency; }
}
