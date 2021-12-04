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
import com.oracle.truffle.api.nodes.IndirectCallNode;
import scala.Function1;
import scala.util.Try;

final class RedeemWith<A, B> extends FungIO<B> {

  @Child private FungIO<A> fa;
  @Child private IndirectCallNode call;
  private Function1<A, FungIO<B>> f;
  private Function1<Throwable, FungIO<B>> g;

  RedeemWith(FungIO<A> fa, Function1<A, FungIO<B>> f, Function1<Throwable, FungIO<B>> g) {
    this.fa = fa;
    this.f = f;
    this.g = g;
    this.call = Truffle.getRuntime().createIndirectCallNode();
  }

  @Override
  public Try<B> execute(VirtualFrame frame) {
    Try<A> tryA = fa.execute(frame);
    FungIO<B> fb;
    if (tryA.isSuccess()) {
      A a = tryA.get();
      fb = f.apply(a);
    } else {
      Throwable ex = tryA.failed().get();
      fb = g.apply(ex);
    }
    @SuppressWarnings("unchecked")
    Try<B> tryB = (Try<B>) call.call(fb.getCallTarget());
    return tryB;
  }
}