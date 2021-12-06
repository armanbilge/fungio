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

private final class FifoQueue[A <: AnyRef](capacity: Int) {
  private[this] var queue = new Array[AnyRef](capacity)
  private[this] var head = 0
  private[this] var tail = 0

  def isEmpty = head == tail
  def size = tail - head

  def enqueue(a: A): Unit = {
    queue(tail) = a
    tail += 1
  }

  def dequeue(): A = {
    val a = queue(head).asInstanceOf[A]
    head += 1
    a
  }

}
