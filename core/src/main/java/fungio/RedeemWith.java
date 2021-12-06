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

package fungio;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import scala.Function1;
import scala.util.control.NonFatal;
import scala.util.Failure;
import scala.util.Try;

final class RedeemWith<A, B> extends FungIO<B> {

  @Child private DirectCallNode fa;
  @Child private IndirectCallNode indirect;
  private Function1<Try<A>, FungIO<B>> f;

  RedeemWith(FungIO<A> fa, Function1<Try<A>, FungIO<B>> f) {
    this.f = f;
    this.fa = Truffle.getRuntime().createDirectCallNode(Truffle.getRuntime().createCallTarget(fa));
    this.indirect = Truffle.getRuntime().createIndirectCallNode();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Try<B> execute(VirtualFrame frame) {
    int maxStackDepth = (int) frame.getArguments()[0];
    if (maxStackDepth > 0) {

      Try<A> tryA;
      try {
        tryA = (Try<A>) fa.call(maxStackDepth - 1);
      } catch (UnrollStack stack) {
        stack.enqueue(f);
        throw stack;
      }

      FungIO<B> fb;
      try {
        fb = f.apply(tryA);
      } catch (Throwable ex) {
        if (NonFatal.apply(ex)) {
          fb = new PureOrError<B>(new Failure<B>(ex));
        } else {
          throw ex;
        }
      }

      Try<B> tryB =
          (Try<B>) indirect.call(Truffle.getRuntime().createCallTarget(fb), maxStackDepth - 1);
      return tryB;
    } else {
      throw new UnrollStack((FungIO<Object>) this);
    }
  }
}
