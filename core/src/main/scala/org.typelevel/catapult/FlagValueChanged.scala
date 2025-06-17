/*
 * Copyright 2022 Typelevel
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

package org.typelevel.catapult

import cats.Show
import cats.kernel.Hash
import cats.syntax.all.*
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent
import org.typelevel.catapult.codec.LDCodec
import org.typelevel.catapult.codec.LDCodec.LDCodecResult

trait FlagValueChanged[A] {
  def oldValue: A
  def newValue: A
}
object FlagValueChanged {
  def apply[A](oldValue: A, newValue: A): FlagValueChanged[A] =
    new Impl[A](oldValue = oldValue, newValue = newValue)

  def apply[A](
      event: FlagValueChangeEvent
  )(implicit codec: LDCodec[A]): LDCodecResult[FlagValueChanged[A]] =
    (
      codec.decode(event.getOldValue),
      codec.decode(event.getNewValue),
    ).mapN(apply[A])

  private final class Impl[A](override val oldValue: A, override val newValue: A)
      extends FlagValueChanged[A] {
    override def toString: String = s"FlagValueChanged($oldValue, $newValue)"
  }

  implicit def show[A: Show]: Show[FlagValueChanged[A]] = Show.show { fvc =>
    show"FlagValueChanged(${fvc.oldValue}, ${fvc.newValue})"
  }
  implicit def hash[A: Hash]: Hash[FlagValueChanged[A]] =
    Hash.by(fvc => (fvc.oldValue, fvc.newValue))
}
