package jamiebalfour.balfspell;

/**
 * An immutable ranked correction.
 */
public final class Suggestion {
  private final String word;
  private final int distance;
  private final long frequency;

  Suggestion(String word, int distance, long frequency) {
    this.word = word;
    this.distance = distance;
    this.frequency = frequency;
  }

  public String word() {
    return word;
  }

  public int distance() {
    return distance;
  }

  public long frequency() {
    return frequency;
  }

  @Override
  public String toString() {
    return word;
  }
}
