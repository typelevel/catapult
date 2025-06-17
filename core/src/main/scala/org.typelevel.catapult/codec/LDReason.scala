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
import cats.kernel.Hash
import com.launchdarkly.sdk.LDValueType

/** Explains an encoding or decoding failure
  */
sealed trait LDReason {
  def explain: String
  def explain(history: String): String

  override def hashCode(): Int = LDReason.hash.hash(this)

  override def equals(obj: Any): Boolean = obj match {
    case that: LDReason => LDReason.hash.eqv(this, that)
    case _ => false
  }

  override def toString: String = explain
}
object LDReason {
  val missingField: LDReason = new LDReason {
    override val explain: String = "Missing expected field"
    override def explain(history: String): String = s"Missing expected field at $history"
  }
  val IndexOutOfBounds: LDReason = new LDReason {
    override val explain: String = "Out of bounds"
    override def explain(history: String): String = s"$history is out of bounds"
  }

  def wrongType(expected: LDValueType, actual: LDValueType): LDReason =
    wrongType(expected.name(), actual)
  def wrongType(expected: String, actual: LDValueType): LDReason = new LDReason {
    override val explain: String =
      s"Expected value of type $expected, but was of type ${actual.name()}"
    override def explain(history: String): String =
      s"Expected value of type $expected at $history, but was of type ${actual.name()}"
  }

  def unableToDecodeKey(reason: LDReason): LDReason = new LDReason {
    override val explain: String = s"Unable to decode key (${reason.explain})"
    override def explain(history: String): String =
      s"Unable to decode key at $history (${reason.explain})"
  }

  def unableToEncodeKey(reason: LDReason): LDReason = new LDReason {
    override val explain: String = s"Unable to encode value as key (${reason.explain})"
    override def explain(history: String): String =
      s"Unable to encode value as key inside $history (${reason.explain})"
  }

  def other(reason: String): LDReason = new LDReason {
    override val explain: String = reason
    override def explain(history: String): String = s"Failure at $history: $reason"
  }

  def unencodableValue(ldValueType: LDValueType, sourceType: String): LDReason = new LDReason {
    override val explain: String =
      s"Value of type $sourceType cannot be encoded as a LDValueType.${ldValueType.name}"
    override def explain(history: String): String =
      s"Value of type $sourceType at $history cannot be encoded as a LDValueType.${ldValueType.name}"
  }

  def undecodableValue(ldValueType: LDValueType, expectedType: String): LDReason = new LDReason {
    override val explain: String =
      s"Value of type LDValueType.${ldValueType.name} cannot be represented as a $expectedType"
    override def explain(history: String): String =
      s"Value of type LDValueType.${ldValueType.name}  at $history cannot be represented as a $expectedType"
  }

  implicit val hash: Hash[LDReason] = Hash.by(_.explain)
  implicit val show: Show[LDReason] = Show.fromToString
}
