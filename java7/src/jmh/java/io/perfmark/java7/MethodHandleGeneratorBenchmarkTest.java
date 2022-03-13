/*
 * Copyright 2021 Carl Mastrangelo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.perfmark.java7;

import io.perfmark.impl.Generator;
import io.perfmark.testing.GeneratorBenchmark;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

@RunWith(JUnit4.class)
public class MethodHandleGeneratorBenchmarkTest {

  @Test
  public void generatorBenchmark() throws Exception {
    Options options = new OptionsBuilder()
        .include(MethodHandleGeneratorBenchmark.class.getCanonicalName())
        .measurementIterations(5)
        .warmupIterations(10)
        .forks(1)
        .verbosity(VerboseMode.EXTRA)
        .warmupTime(TimeValue.seconds(1))
        .measurementTime(TimeValue.seconds(1))
        .shouldFailOnError(true)
        // This is necessary to run in the IDE, otherwise it would inherit the VM args.
        .jvmArgs("-da")
        .build();

    new Runner(options).run();
  }

  @State(Scope.Benchmark)
  public static class MethodHandleGeneratorBenchmark extends GeneratorBenchmark {
    @Override
    protected Generator getGenerator() {
      return new SecretMethodHandleGenerator.MethodHandleGenerator();
    }
  }

  @Test
  public void stringtableBench() throws Exception {
    Options options = new OptionsBuilder()
        .include(StringTableBenchMark.class.getCanonicalName())
        .measurementIterations(5)
        .warmupIterations(10)
        .forks(1)
        .addProfiler("gc")
        .verbosity(VerboseMode.EXTRA)
        .warmupTime(TimeValue.seconds(1))
        .measurementTime(TimeValue.seconds(1))
        .shouldFailOnError(true)
        // This is necessary to run in the IDE, otherwise it would inherit the VM args.
        .jvmArgs("-da")
        .build();

    new Runner(options).run();
  }

  @State(Scope.Thread)
  public static class StringTableBenchMark {

    @State(Scope.Thread)
    @AuxCounters
    public static class HitRate {
      public long hitRate;
    }

    private final RandomGenerator rng = RandomGenerator.of("SplittableRandom");

    private StringTableEncoder enc;
    private StringTableDecoder dec;

    @Param({"65536",  "1048576", "33554432"})
    public int tableByteSize;

    @Param({"16", "256",  "49152", "1048576"})
    public int tableSize;

    @Param({".01", ".05", ".5", "1"})
    public double reusePercentage;

    @Param({"32"})
    public double averageStringLength;

    private final List<String> toCode = new ArrayList<>();

    @Setup
    public  void setUp() throws Exception {
      enc = new StringTableEncoder(tableByteSize, tableSize);
      dec = new StringTableDecoder(tableByteSize, tableSize);

      double iters = tableSize + 1;
      if (reusePercentage != 0) {
        iters = enc.tableSize() / reusePercentage;
      }
      var epsilon = Math.nextUp(0d);
      for (int i = 0; i < iters; i++) {
        int len =
            Math.toIntExact(Math.round(-Math.log(Math.max(rng.nextDouble(), epsilon)) * averageStringLength));
        String rand = rng.ints(32, 127)
            .limit(len)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
        toCode.add(rand);
        //System.err.println(rand);
      }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public Object add(HitRate hitRate) {
      String toAdd = toCode.get(rng.nextInt(toCode.size()));
      if (enc.get(toAdd) != -1) {
        hitRate.hitRate++;
      } else {
        enc.add(toAdd, toAdd.length());
      }

      return enc;
    }

  }
}
