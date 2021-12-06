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
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import scala.Function1;
import scala.util.control.NonFatal;
import scala.util.Failure;
import scala.util.Try;

final class UnsafeRun<A> extends RootNode {

  @Child private FungIO<A> fa;
  @Child private IndirectCallNode indirect;

  UnsafeRun(FungIO<A> fa) {
    super(null);
    this.fa = fa;
    this.indirect = Truffle.getRuntime().createIndirectCallNode();
  }

  @Override
  public Try<A> execute(VirtualFrame frame) {
    int maxStackDepth = (int) frame.getArguments()[0];
    FungIO<A> fa = this.fa;
    while (true) {
      try {
        return (Try<A>) indirect.call(Truffle.getRuntime().createCallTarget(fa), maxStackDepth);
      } catch (UnrollStack<A> unroll) {
        fa = unroll.fa();
      }
    }
  }
}
