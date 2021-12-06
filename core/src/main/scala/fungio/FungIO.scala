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

import cats.Show
import cats.StackSafeMonad
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Sync
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ControlFlowException
import com.oracle.truffle.api.nodes.RootNode

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import java.util.ArrayDeque

import FungIOConstants._

abstract class FungIO[+A] private[fungio] extends RootNode(null) {

  def execute(frame: VirtualFrame): Try[A]

  final def flatMap[B](f: A => FungIO[B]): FungIO[B] =
    FungIO.flatMap(this)(f)

  final def unsafeRunSync(): A = {
    val unsafeRun = new UnsafeRun(this.asInstanceOf[FungIO[AnyRef]])
    Truffle
      .getRuntime()
      .createCallTarget(unsafeRun)
      .call(Integer.valueOf(MaxStackDepth))
      .asInstanceOf[Try[A]]
      .get
  }

}

object FungIO
    extends Sync[FungIO]
    with StackSafeMonad[FungIO]
    with MonadCancel.Uncancelable[FungIO, Throwable] {

  def apply[A](thunk: => A): FungIO[A] = delay(thunk)

  implicit def syncForFungIO: Sync[FungIO] = this
  implicit def showForFungIO[A]: Show[FungIO[A]] = _ => "FungIO(...)"

  override def pure[A](x: A): FungIO[A] = PureOrError(Success(x))

  override def raiseError[A](e: Throwable): FungIO[A] = PureOrError(Failure(e))

  override def handleErrorWith[A](fa: FungIO[A])(f: Throwable => FungIO[A]): FungIO[A] =
    new RedeemWith[A, A](fa, _.fold(f, pure(_)))

  override def redeem[A, B](fa: FungIO[A])(recover: Throwable => B, f: A => B): FungIO[B] =
    redeemWith(fa)(ex => pure(recover(ex)), a => pure(f(a)))

  override def flatMap[A, B](fa: FungIO[A])(f: A => FungIO[B]): FungIO[B] =
    new RedeemWith[A, B](fa, _.fold(raiseError(_), f(_)))

  override def forceR[A, B](fa: FungIO[A])(fb: FungIO[B]): FungIO[B] = productR(attempt(fa))(fb)

  override def monotonic: FungIO[FiniteDuration] = delay(System.nanoTime().nanos)

  override def realTime: FungIO[FiniteDuration] = delay(System.currentTimeMillis.millis)

  override def suspend[A](hint: Sync.Type)(thunk: => A): FungIO[A] =
    Suspend(Thunk.asFunction0(thunk))

}

private final case class PureOrError[A](value: Try[A]) extends FungIO[A] {
  override def execute(frame: VirtualFrame): Try[A] = value
  override protected def isTrivial(): Boolean = true
}

private final case class Suspend[A](thunk: () => A) extends FungIO[A] {
  override def execute(frame: VirtualFrame): Try[A] = Try(thunk())
}

private final class UnrollStack(val fa: FungIO[AnyRef]) extends ControlFlowException {
  val conts = new ArrayDeque[Try[AnyRef] => FungIO[AnyRef]](MaxStackDepth)
  def push[A, B](f: Try[A] => FungIO[B]): Unit =
    conts.addFirst(f.asInstanceOf[Try[AnyRef] => FungIO[AnyRef]])
}
