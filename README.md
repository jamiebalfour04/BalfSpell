# BalfSpell

Small, dependency-free Java 11 spell-checking library. It is designed for editors, writing tools and code IDEs where fast immutable lookups matter.

## Features

- O(1)-average exact lookup after build.
- SymSpell-style deletion index for small candidate sets.
- Bounded Damerau–Levenshtein ranking, including adjacent transpositions.
- Optional word frequencies for deterministic tie-breaking.
- Custom dictionaries and UTF-8 dictionary files.
- British English-friendly: ship or load a vocabulary containing `colour`, `optimise`, `centre`, etc.; the library does not force American spellings.
- Case-preserving suggestions (`Helo` → `Hello`).
- Tokenisation, misspelling extraction and camelCase/underscore identifier splitting.
- Immutable, thread-safe lookup state after `build()`.
- Java 11 source and bytecode; no runtime dependencies.

## Quick start

```java
SpellChecker checker = SpellChecker.builder()
    .add("hello", 100)
    .add("help", 50)
    .add("colour", 90)
    .maxDistance(2)
    .build();

checker.isCorrect("colour");              // true
checker.suggest("helo", 5);                // ranked Suggestion objects
checker.misspelledWords("Helo, colour!");  // [Helo]
checker.splitIdentifier("getUserDetails"); // [get, User, Details]
```

Dictionary files are UTF-8, one word per line. An optional second column is a non-negative frequency:

```
colour 9000
centre 8500
optimise 7000
```

```java
SpellChecker checker = SpellChecker.builder()
    .load(Paths.get("british-english.txt"))
    .build();
```

## Build and test

With Maven: `mvn test` compiles the library and the dependency-free test runner. To run the runner directly: `mvn -q exec:java`.

Without Maven:

```sh
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out com.balfour.balfspell.SpellCheckerTest
```

The deletion index uses extra memory proportional to generated deletion forms, trading memory for fast candidate retrieval. `suggest` is bounded by the configured edit distance and never scans the full dictionary.

## License

MIT. The library contains no bundled third-party dictionary; supply a dictionary whose licensing matches your application.
