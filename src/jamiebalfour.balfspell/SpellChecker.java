package jamiebalfour.balfspell;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast, immutable-after-build spell checker. Build with {@link Builder}.
 */
public final class SpellChecker {
  private static final String DICTIONARY_BASE_URL = "https://raw.githubusercontent.com/jamiebalfour/balfspell/main/binary-dictionaries/";
  private final Set<String> words;
  private final Map<String, List<String>> deletes;
  private final Map<String, Long> frequencies;
  private final int maxDistance;
  private final boolean preserveCase;

  private SpellChecker(Builder b) {
    this.maxDistance = b.maxDistance;
    this.preserveCase = b.preserveCase;
    Map<String, Long> f = new HashMap<>();
    for (DictionaryEntry e : b.entries) {
      String w = norm(e.word());
      if (!w.isEmpty()) f.merge(w, e.frequency(), Math::max);
    }
    this.frequencies = Collections.unmodifiableMap(f);
    this.words = Collections.unmodifiableSet(new HashSet<>(f.keySet()));
    Map<String, List<String>> d = new HashMap<>();
    for (String w : words)
      for (String x : deletionVariants(w, maxDistance)) d.computeIfAbsent(x, k -> new ArrayList<>()).add(w);
    Map<String, List<String>> frozen = new HashMap<>();
    for (Map.Entry<String, List<String>> e : d.entrySet()) {
      e.getValue().sort(String::compareTo);
      frozen.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
    }
    this.deletes = Collections.unmodifiableMap(frozen);
  }

  private SpellChecker(Map<String, Long> f, Map<String, List<String>> d, int max, boolean preserve) {
    maxDistance = max;
    preserveCase = preserve;
    frequencies = Collections.unmodifiableMap(f);
    words = Collections.unmodifiableSet(new HashSet<>(f.keySet()));
    deletes = Collections.unmodifiableMap(d);
  }

  /**
   * Loads a binary cache without rebuilding its deletion index.
   */
  public static SpellChecker loadBinary(Path path) throws IOException {
    try (InputStream input = Files.newInputStream(path)) {
      return loadBinary(input);
    }
  }

  /**
   * Loads a binary dictionary packaged in src/main/resources or another classpath location.
   */
  public static SpellChecker loadBinaryResource(String resource) throws IOException {
    InputStream input = SpellChecker.class.getClassLoader().getResourceAsStream(resource);
    if (input == null) throw new FileNotFoundException("Dictionary resource not found: " + resource);
    try (InputStream in = input) {
      return loadBinary(in);
    }
  }

  /** Loads dictionaries/&lt;name&gt;.balf from the classpath, for example "en-us". */
  public static SpellChecker loadDictionary(String name) throws IOException {
    if (name == null || !name.matches("[A-Za-z0-9_-]+")) {
      throw new IllegalArgumentException("Invalid dictionary name: " + name);
    }
    Path cached = dictionaryDirectory().resolve(name + ".dic");
    if (Files.isRegularFile(cached)) return loadBinary(cached);
    throw new FileNotFoundException("Dictionary not installed: " + name + ". Download it with downloadDictionary(\"" + name + "\")");
  }

  /** Downloads and caches dictionaries/&lt;name&gt;.balf from GitHub. */
  public static SpellChecker downloadDictionary(String name) throws IOException {
    validateDictionaryName(name);
    Path directory = dictionaryDirectory();
    Path target = directory.resolve(name + ".dic");
    Path temporary = Files.createTempFile(directory, name + "-", ".download");
    HttpURLConnection connection = (HttpURLConnection) new URL(DICTIONARY_BASE_URL + name + ".dic").openConnection();
    connection.setConnectTimeout(15000); connection.setReadTimeout(120000); connection.setInstanceFollowRedirects(true);
    connection.setRequestProperty("User-Agent", "BalfSpell/1.0");
    try {
      if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("Dictionary download failed: HTTP " + connection.getResponseCode());
      try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(temporary)) {
        byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
      }
      try { Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
      catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
    } finally { connection.disconnect(); Files.deleteIfExists(temporary); }
    return loadBinary(target);
  }

  /** Returns the writable per-user dictionary directory, falling back to ~/.balfspell. */
  public static Path dictionaryDirectory() throws IOException {
    String home = System.getProperty("user.home");
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    Path preferred;
    if (os.contains("mac")) preferred = Paths.get(home, "Library", "Application Support", "jamiebalfour", "balfspell", "dictionaries");
    else if (os.contains("win")) preferred = Paths.get(System.getenv().getOrDefault("APPDATA", home), "jamiebalfour", "balfspell", "dictionaries");
    else preferred = Paths.get(System.getenv().getOrDefault("XDG_DATA_HOME", Paths.get(home, ".local", "share").toString()), "jamiebalfour", "balfspell", "dictionaries");
    try { Files.createDirectories(preferred); if (Files.isWritable(preferred)) return preferred; } catch (IOException ignored) { }
    Path fallback = Paths.get(home, ".balfspell", "dictionaries"); Files.createDirectories(fallback); return fallback;
  }

  private static void validateDictionaryName(String name) {
    if (name == null || !name.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid dictionary name: " + name);
  }

  public static SpellChecker loadBinary(InputStream input) throws IOException {
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(input))) {
      if (in.readInt() != 0x42465331) throw new IOException("Unsupported BalfSpell binary dictionary");
      int version = in.readInt();
      if (version < 1 || version > 2) throw new IOException("Unsupported BalfSpell binary dictionary version");
      int max = in.readInt();
      boolean preserve = in.readBoolean();
      Map<String, Long> f = new HashMap<>();
      int wc = in.readInt();
      for (int i = 0; i < wc; i++) f.put(in.readUTF(), in.readLong());
      if (version == 1) {
        int dc = in.readInt();
        for (int i = 0; i < dc; i++) {
          in.readUTF();
          int n = in.readInt();
          for (int j = 0; j < n; j++) in.readUTF();
        }
      }
      return new SpellChecker(f, Collections.emptyMap(), max, preserve);
    }
  }

  private static String norm(String s) {
    return s.trim().toLowerCase(Locale.ROOT);
  }

  private static Set<String> deletionVariants(String s, int max) {
    Set<String> out = new HashSet<>();
    Set<String> level = new HashSet<>();
    level.add(s);
    for (int i = 0; i < max; i++) {
      Set<String> next = new HashSet<>();
      for (String x : level)
        for (int j = 0; j < x.length(); j++) {
          String y = x.substring(0, j) + x.substring(j + 1);
          if (out.add(y)) next.add(y);
        }
      level = next;
    }
    return out;
  }

  private static int distance(String a, String b, int max) {
    if (Math.abs(a.length() - b.length()) > max) return max + 1;
    int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1], prev2 = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) prev[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      cur[0] = i;
      int row = max + 1;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
        if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
          cur[j] = Math.min(cur[j], prev2[j - 2] + 1);
        row = Math.min(row, cur[j]);
      }
      if (row > max) return max + 1;
      int[] t = prev2;
      prev2 = prev;
      prev = cur;
      cur = t;
    }
    return prev[b.length()];
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isCorrect(String word) {
    return word != null && words.contains(norm(word));
  }

  /**
   * Writes the dictionary and prebuilt deletion index to a binary cache.
   */
  public void writeBinary(Path path) throws IOException {
    try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
      out.writeInt(0x42465331);
      out.writeInt(2);
      out.writeInt(maxDistance);
      out.writeBoolean(preserveCase);
      out.writeInt(words.size());
      for (String w : words) {
        out.writeUTF(w);
        out.writeLong(frequencies.get(w));
      }
    }
  }

  public List<Suggestion> suggest(String word, int limit) {
    return suggest(word, limit, maxDistance);
  }

  public List<Suggestion> suggest(String input, int limit, int distance) {
    if (input == null || limit <= 0) return Collections.emptyList();
    String q = norm(input);
    if (q.isEmpty()) return Collections.emptyList();
    if (words.contains(q)) return Collections.singletonList(new Suggestion(format(q, input), 0, frequencies.get(q)));
    Set<String> candidates = new HashSet<>();
    if (deletes.isEmpty()) candidates.addAll(words);
    else candidates.addAll(deletes.getOrDefault(q, Collections.emptyList()));
    for (String d : deletionVariants(q, Math.max(0, distance)))
      candidates.addAll(deletes.getOrDefault(d, Collections.emptyList()));
    List<Suggestion> result = new ArrayList<>();
    for (String c : candidates) {
      if (Math.abs(c.length() - q.length()) > distance) continue;
      int n = distance(q, c, distance);
      if (n <= distance) result.add(new Suggestion(format(c, input), n, frequencies.get(c)));
    }
    result.sort(Comparator.comparingInt(Suggestion::distance).thenComparing(Suggestion::frequency, Comparator.reverseOrder()).thenComparing(Suggestion::word));
    return Collections.unmodifiableList(result.subList(0, Math.min(limit, result.size())));
  }

  public List<String> wordsIn(String text) {
    if (text == null || text.isEmpty()) return Collections.emptyList();
    List<String> out = new ArrayList<>();
    Matcher m = Pattern.compile("[\\p{L}\\p{Nd}]+(?:['’-][\\p{L}\\p{Nd}]+)*").matcher(text);
    while (m.find()) out.add(m.group());
    return Collections.unmodifiableList(out);
  }

  public List<String> splitIdentifier(String id) {
    if (id == null || id.isEmpty()) return Collections.emptyList();
    String s = id.replace('_', ' ');
    s = s.replaceAll("([a-z\\d])([A-Z])", "$1 $2");
    s = s.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
    return Arrays.asList(s.trim().split("\\s+"));
  }

  public List<String> misspelledWords(String text) {
    List<String> out = new ArrayList<>();
    for (String w : wordsIn(text)) if (!isCorrect(w)) out.add(w);
    return Collections.unmodifiableList(out);
  }

  private String format(String w, String original) {
    if (!preserveCase) return w;
    if (original.equals(original.toUpperCase(Locale.ROOT))) return w.toUpperCase(Locale.ROOT);
    if (Character.isUpperCase(original.charAt(0))) return Character.toUpperCase(w.charAt(0)) + w.substring(1);
    return w;
  }

  public static final class Builder {
    private final List<DictionaryEntry> entries = new ArrayList<>();
    private int maxDistance = 2;
    private boolean preserveCase = true;

    public Builder add(String word) {
      return add(word, 0);
    }

    public Builder add(String word, long frequency) {
      if (word != null) entries.add(new DictionaryEntry(word, frequency));
      return this;
    }

    public Builder addAll(Collection<String> words) {
      for (String w : words) add(w);
      return this;
    }

    public Builder load(Path path) throws IOException {
      return load(path, StandardCharsets.UTF_8);
    }

    public Builder load(Path path, java.nio.charset.Charset cs) throws IOException {
      try (BufferedReader r = Files.newBufferedReader(path, cs)) {
        String line;
        while ((line = r.readLine()) != null) {
          String[] p = line.trim().split("\\s+");
          if (p.length > 0 && !p[0].isEmpty()) add(p[0], p.length > 1 ? Long.parseLong(p[1]) : 0);
        }
      }
      return this;
    }

    public Builder maxDistance(int n) {
      if (n < 0 || n > 4) throw new IllegalArgumentException("maxDistance must be 0..4");
      maxDistance = n;
      return this;
    }

    public Builder preserveCase(boolean v) {
      preserveCase = v;
      return this;
    }

    public SpellChecker build() {
      return new SpellChecker(this);
    }
  }
}
