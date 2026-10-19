package com.balfour.balfspell;
/** Tiny repeatable smoke benchmark; reports lookup throughput without external benchmarking dependencies. */
public final class SpellCheckerBenchmark {
  public static void main(String[] args) {
    SpellChecker.Builder b=SpellChecker.builder(); for(int i=0;i<10000;i++) b.add("word"+i,i); SpellChecker s=b.build();
    for(int i=0;i<1000;i++) s.suggest("wrod9999",5); long start=System.nanoTime(); int rounds=10000; for(int i=0;i<rounds;i++) s.suggest("wrod9999",5); long elapsed=System.nanoTime()-start;
    System.out.printf("%d suggestions in %.2f ms (%.0f ops/s)%n",rounds,elapsed/1_000_000.0,rounds*1_000_000_000.0/elapsed);
  }
}
