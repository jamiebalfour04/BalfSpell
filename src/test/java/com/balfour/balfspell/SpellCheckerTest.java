package com.balfour.balfspell;

import java.util.Arrays;
import java.util.Collections;

public final class SpellCheckerTest {
  private static int n;

  public static void main(String[] a) {
    SpellChecker s = SpellChecker.builder().add("hello", 100).add("help", 50).add("colour", 90).add("receive", 80).add("their", 1).add("there", 100).add("yesterday", 1).build();
    check(s.isCorrect("COLOUR"));
    check(!s.isCorrect("helo"));
    check(s.suggest("helo", 3).get(0).word().equals("hello"));
    check(s.suggest("recieve", 3).get(0).word().equals("receive"));
    check(s.suggest("Helo", 3).get(0).word().equals("Hello"));
    check(s.wordsIn("Hello, John's colour.").size() == 3);
    check(s.splitIdentifier("getHTTPResponse").equals(Arrays.asList("get", "HTTP", "Response")));
    check(s.misspelledWords("helo colour").equals(Collections.singletonList("helo")));
    System.out.println("PASS: " + n + " checks");
  }

  private static void check(boolean ok) {
    n++;
    if (!ok) throw new AssertionError("check " + n + " failed");
  }
}
