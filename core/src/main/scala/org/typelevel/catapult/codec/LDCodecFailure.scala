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

package org.typelevel.catapult.codec

import cats.Show
import cats.data.{NonEmptyChain, ValidatedNec}
import cats.kernel.Hash
import cats.syntax.all.*

/** Encodes a failure while decoding from an `LDValue` or encoding to an `LDValue`
  */
sealed trait LDCodecFailure {

  /** The reason for the decoding failure
    */
  def reason: LDReason

  /** The path to the point where the failure occurred
    */
  def history: LDCursorHistory

  def updateHistory(f: LDCursorHistory => LDCursorHistory): LDCodecFailure
}
object LDCodecFailure {
  def apply(reason: LDReason, history: LDCursorHistory): LDCodecFailure = new Impl(reason, history)

  def apply(history: LDCursorHistory)(
      reasons: NonEmptyChain[LDReason]
  ): NonEmptyChain[LDCodecFailure] =
    reasons.map(apply(_, history))

  def failed[A](reason: LDReason, history: LDCursorHistory): ValidatedNec[LDCodecFailure, A] =
    apply(reason, history).invalidNec

  implicit val hash: Hash[LDCodecFailure] = Hash.by(lcf => (lcf.reason, lcf.history))
  implicit val show: Show[LDCodecFailure] = Show.show { df =>
    df.reason.explain(df.history.show)
  }

  private final class Impl(val reason: LDReason, val history: LDCursorHistory)
      extends LDCodecFailure {
    override def updateHistory(f: LDCursorHistory => LDCursorHistory): LDCodecFailure =
      new Impl(reason, f(history))

    override def toString: String = s"LDCodecFailure($reason, ${history.show})"

    override def equals(obj: Any): Boolean = obj match {
      case that: LDCodecFailure => LDCodecFailure.hash.eqv(this, that)
      case _ => false
    }

    override def hashCode(): Int = LDCodecFailure.hash.hash(this)
  }
}
