/*
 * Copyright 2021 Arman Bilge
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

package fungio
package benchmarks

import cats.effect.SyncIO

import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

/**
 * To run the benchmark from within sbt:
 *
 * jmh:run -i 10 -wi 10 -f 2 -t 1 fungio.benchmarks.DeepBindBenchmark
 *
 * Which means "10 iterations", "10 warm-up iterations", "2 forks", "1 thread". Please note that
 * benchmarks should be usually executed at least in 10 iterations (as a rule of thumb), but
 * more is better.
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class DeepBindBenchmark {

  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def pureFungIO(): Int = {
    def loop(i: Int): FungIO[Int] =
      FungIO.pure(i).flatMap { j =>
        if (j > size)
          FungIO.pure(j)
        else
          loop(j + 1)
      }

    loop(0).unsafeRunSync()
  }

  @Benchmark
  def delayFungIO(): Int = {
    def loop(i: Int): FungIO[Int] =
      FungIO(i).flatMap { j =>
        if (j > size)
          FungIO.pure(j)
        else
          loop(j + 1)
      }

    loop(0).unsafeRunSync()
  }

  @Benchmark
  def pureSyncIO(): Int = {
    def loop(i: Int): SyncIO[Int] =
      SyncIO.pure(i).flatMap { j =>
        if (j > size)
          SyncIO.pure(j)
        else
          loop(j + 1)
      }

    loop(0).unsafeRunSync()
  }

  @Benchmark
  def delaySyncIO(): Int = {
    def loop(i: Int): SyncIO[Int] =
      SyncIO(i).flatMap { j =>
        if (j > size)
          SyncIO.pure(j)
        else
          loop(j + 1)
      }

    loop(0).unsafeRunSync()
  }

}
