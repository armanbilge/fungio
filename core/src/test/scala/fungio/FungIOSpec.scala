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

import cats.Id
import cats.Show
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Outcome
import cats.effect.kernel.Sync
import cats.effect.kernel.testkit.SyncGenerators
import cats.effect.laws.SyncTests
import cats.effect.testkit.TestInstances
import cats.kernel.Eq
import cats.syntax.all._
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Prop
import org.scalacheck.Prop.forAll
import org.specs2.matcher.Matcher
import org.specs2.mutable.Specification
import org.typelevel.discipline.specs2.mutable.Discipline

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class FungIOSpec extends Specification with Discipline with TestInstances {

  "FungIO" should {
    "produce a pure value when run" in {
      FungIO.pure(42) must completeAs(42)
    }

    "suspend a side-effect without memoizing" in {
      var i = 42

      val ioa = FungIO {
        i += 1
        i
      }

      ioa must completeAs(43)
      ioa must completeAs(44)
    }

    "capture errors in suspensions" in {
      case object TestException extends RuntimeException
      FungIO(throw TestException) must failAsSync(TestException)
    }

    "map results to a new type" in {
      FungIO.pure(42).map(_.toString) must completeAs("42")
    }

    "flatMap results sequencing both effects" in {
      var i = 0
      FungIO.pure(42).flatMap(i2 => FungIO { i = i2 }) must completeAs(())
      i mustEqual 42
    }

    "raiseError propagates out" in {
      case object TestException extends RuntimeException
      FungIO.raiseError(TestException).void.flatMap(_ => FungIO.pure(())) must failAsSync(
        TestException)
    }

    "errors can be handled" in {
      case object TestException extends RuntimeException
      FungIO.raiseError[Unit](TestException).attempt must completeAs(Left(TestException))
    }

    "attempt is redeem with Left(_) for recover and Right(_) for map" in {
      forAll { (io: FungIO[Int]) => io.attempt eqv io.redeem(Left(_), Right(_)) }
    }

    "attempt is flattened redeemWith" in {
      forAll {
        (io: FungIO[Int], recover: Throwable => FungIO[String], bind: Int => FungIO[String]) =>
          io.attempt.flatMap(_.fold(recover, bind)) eqv io.redeemWith(recover, bind)
      }
    }

    "redeem is flattened redeemWith" in {
      forAll {
        (io: FungIO[Int], recover: Throwable => FungIO[String], bind: Int => FungIO[String]) =>
          io.redeem(recover, bind).flatMap(identity) eqv io.redeemWith(recover, bind)
      }
    }

    "redeem subsumes handleError" in {
      forAll { (io: FungIO[Int], recover: Throwable => Int) =>
        io.redeem(recover, identity) eqv io.handleError(recover)
      }
    }

    "redeemWith subsumes handleErrorWith" in {
      forAll { (io: FungIO[Int], recover: Throwable => FungIO[Int]) =>
        io.redeemWith(recover, FungIO.pure) eqv io.handleErrorWith(recover)
      }
    }

    "redeem correctly recovers from errors" in {
      case object TestException extends RuntimeException
      FungIO.raiseError[Unit](TestException).redeem(_ => 42, _ => 43) must completeAs(42)
    }

    "redeem maps successful results" in {
      FungIO.unit.redeem(_ => 41, _ => 42) must completeAs(42)
    }

    "redeem catches exceptions thrown in recovery function" in {
      case object TestException extends RuntimeException
      case object ThrownException extends RuntimeException
      FungIO
        .raiseError[Unit](TestException)
        .redeem(_ => throw ThrownException, _ => 42)
        .attempt must completeAs(Left(ThrownException))
    }

    "redeem catches exceptions thrown in map function" in {
      case object ThrownException extends RuntimeException
      FungIO.unit.redeem(_ => 41, _ => throw ThrownException).attempt must completeAs(
        Left(ThrownException))
    }

    "redeemWith correctly recovers from errors" in {
      case object TestException extends RuntimeException
      FungIO
        .raiseError[Unit](TestException)
        .redeemWith(_ => FungIO.pure(42), _ => FungIO.pure(43)) must completeAs(42)
    }

    "redeemWith binds successful results" in {
      FungIO.unit.redeemWith(_ => FungIO.pure(41), _ => FungIO.pure(42)) must completeAs(42)
    }

    "redeemWith catches exceptions throw in recovery function" in {
      case object TestException extends RuntimeException
      case object ThrownException extends RuntimeException
      FungIO
        .raiseError[Unit](TestException)
        .redeemWith(_ => throw ThrownException, _ => FungIO.pure(42))
        .attempt must completeAs(Left(ThrownException))
    }

    "redeemWith catches exceptions thrown in bind function" in {
      case object ThrownException extends RuntimeException
      FungIO
        .unit
        .redeem(_ => FungIO.pure(41), _ => throw ThrownException)
        .attempt must completeAs(Left(ThrownException))
    }

    "evaluate 10,000 consecutive map continuations" in {
      def loop(i: Int): FungIO[Unit] =
        if (i < 10000)
          FungIO.unit.flatMap(_ => loop(i + 1)).map(u => u)
        else
          FungIO.unit

      loop(0) must completeAs(())
    }

    "evaluate 10,000 consecutive handleErrorWith continuations" in {
      def loop(i: Int): FungIO[Unit] =
        if (i < 10000)
          FungIO.unit.flatMap(_ => loop(i + 1)).handleErrorWith(FungIO.raiseError(_))
        else
          FungIO.unit

      loop(0) must completeAs(())
    }

    "catch exceptions thrown in map functions" in {
      case object TestException extends RuntimeException
      FungIO.unit.map(_ => (throw TestException): Unit).attempt must completeAs(
        Left(TestException))
    }

    "catch exceptions thrown in flatMap functions" in {
      case object TestException extends RuntimeException
      FungIO.unit.flatMap(_ => (throw TestException): FungIO[Unit]).attempt must completeAs(
        Left(TestException))
    }

    "catch exceptions thrown in handleErrorWith functions" in {
      case object TestException extends RuntimeException
      case object WrongException extends RuntimeException
      FungIO
        .raiseError[Unit](WrongException)
        .handleErrorWith(_ => (throw TestException): FungIO[Unit])
        .attempt must completeAs(Left(TestException))
    }

    "preserve monad right identity on uncancelable" in {
      val fa = MonadCancel[FungIO].uncancelable(_ => MonadCancel[FungIO].canceled)
      fa.flatMap(FungIO.pure(_)) must completeAs(())
      fa must completeAs(())
    }

    "cancel flatMap continuations following a canceled uncancelable block" in {
      MonadCancel[FungIO]
        .uncancelable(_ => MonadCancel[FungIO].canceled)
        .flatMap(_ => FungIO.pure(())) must completeAs(())
    }

    "cancel map continuations following a canceled uncancelable block" in {
      MonadCancel[FungIO]
        .uncancelable(_ => MonadCancel[FungIO].canceled)
        .map(_ => ()) must completeAs(())
    }
  }

  checkAll("FungIO", SyncTests[FungIO].sync[Int, Int, Int])

  def unsafeRunSync[A](ioa: FungIO[A]): Outcome[Id, Throwable, A] =
    try Outcome.succeeded[Id, Throwable, A](ioa.unsafeRunSync())
    catch {
      case t: Throwable => Outcome.errored(t)
    }

  def completeAs[A: Eq: Show](expected: A): Matcher[FungIO[A]] = { (fioa: FungIO[A]) =>
    val a = fioa.unsafeRunSync()
    (a eqv expected, s"${a.show} !== ${expected.show}")
  }

  def failAsSync[A](expected: Throwable): Matcher[FungIO[A]] = { (fioa: FungIO[A]) =>
    val t =
      (try fioa.unsafeRunSync()
      catch {
        case t: Throwable => t
      }).asInstanceOf[Throwable]
    (t eqv expected, s"${t.show} !== ${expected.show}")
  }

  implicit def fungIOToProp(fb: FungIO[Boolean]): Prop =
    Prop(Try(fb.unsafeRunSync()).getOrElse(false))

  implicit def eqFungIO[A: Eq]: Eq[FungIO[A]] = Eq.by(unsafeRunSync)

  implicit def arbitraryFungIO[A: Arbitrary: Cogen]: Arbitrary[FungIO[A]] = {
    val generators = new SyncGenerators[FungIO] {
      override val arbitraryE: Arbitrary[Throwable] = arbitraryThrowable
      override val cogenE: Cogen[Throwable] = Cogen[Throwable]
      override protected val arbitraryFD: Arbitrary[FiniteDuration] = arbitraryFiniteDuration
      override val F: Sync[FungIO] = FungIO
    }
    Arbitrary(generators.generators[A])
  }
}
