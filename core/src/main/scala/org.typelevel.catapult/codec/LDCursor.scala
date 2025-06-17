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
import cats.data.ValidatedNec
import cats.kernel.Hash
import cats.syntax.all.*
import com.launchdarkly.sdk.{LDValue, LDValueType}
import org.typelevel.catapult.codec.LDCursor.{LDArrayCursor, LDObjectCursor}
import org.typelevel.catapult.codec.LDReason.{IndexOutOfBounds, missingField, wrongType}
import org.typelevel.catapult.instances.catapultCatsInstancesForLDValue

/** A lens that represents a position in an `LDValue` that supports one-way navigation
  * and decoding using `LDCodec` instances.
  */
sealed trait LDCursor {

  /** The current value pointed to by the cursor.
    *
    * @note This is guaranteed to be non-null
    */
  def value: LDValue

  def valueType: LDValueType = value.getType

  /** The path to the current value
    */
  def history: LDCursorHistory

  def fail(reason: LDReason): LDCodecFailure = LDCodecFailure(reason, history)

  /** Attempt to decode the current value to an `A`
    */
  def as[A: LDCodec]: ValidatedNec[LDCodecFailure, A]

  /** Ensure the type of `value` matches the expected `LDValueType`
    *
    * @see [[asArray]] if the expected type is `ARRAY`
    */
  def checkType(expected: LDValueType): ValidatedNec[LDCodecFailure, LDCursor]

  /** Ensure the type of `value` is `ARRAY` and return an `LDCursor`
    * specialized to working with `LDValue` arrays
    */
  def asArray: ValidatedNec[LDCodecFailure, LDArrayCursor]

  /** Ensure the type of `value` is `OBJECT` and return an `LDCursor`
    * specialized to working with `LDValue` objects
    */
  def asObject: ValidatedNec[LDCodecFailure, LDObjectCursor]

  override def toString: String = LDCursor.show.show(this)

  override def hashCode(): Int = LDCursor.hash.hash(this)

  override def equals(obj: Any): Boolean = obj match {
    case that: LDCursor => LDCursor.hash.eqv(this, that)
    case _ => false
  }
}

object LDCursor {
  def root(value: LDValue): LDCursor = new Impl(LDValue.normalize(value), LDCursorHistory.root)

  def of(value: LDValue, history: LDCursorHistory): LDCursor =
    new Impl(LDValue.normalize(value), history)

  implicit val show: Show[LDCursor] = Show.show(c => show"LDCursor(${c.value}, ${c.history}")
  implicit val hash: Hash[LDCursor] = Hash.by(c => (c.value, c.history))

  /** An [[LDCursor]] that is specialized to work with `LDValue` arrays
    */
  sealed trait LDArrayCursor extends LDCursor {

    /** Descend to the given index
      *
      * @note Bounds checking will be done on `index`
      */
    def at(index: Int): ValidatedNec[LDCodecFailure, LDCursor]

    /** Attempt to decode the value at the given index as an `A`
      *
      * @note Bounds checking will be done on `index`
      */
    def get[A: LDCodec](index: Int): ValidatedNec[LDCodecFailure, A] =
      at(index).andThen(_.as[A])
  }

  /** An [[LDCursor]] that is specialized to work with `LDValue` objects
    */
  sealed trait LDObjectCursor extends LDCursor {

    /** Descend to value at the given field
      */
    def at(field: String): ValidatedNec[LDCodecFailure, LDCursor]

    /** Attempt to decode the value the given field as an `A`
      */
    def get[A: LDCodec](field: String): ValidatedNec[LDCodecFailure, A] =
      at(field).andThen(_.as[A])
  }

  private final class Impl(override val value: LDValue, override val history: LDCursorHistory)
      extends LDCursor {
    override def as[A: LDCodec]: ValidatedNec[LDCodecFailure, A] = LDCodec[A].decode(value)

    override def checkType(expected: LDValueType): ValidatedNec[LDCodecFailure, LDCursor] =
      value.getType match {
        case actual if actual != expected =>
          LDCodecFailure.failed(wrongType(expected, value.getType), history)
        case LDValueType.ARRAY => new ArrayCursorImpl(value, history).valid
        case LDValueType.OBJECT => new ObjectCursorImpl(value, history).valid
        case _ => new Impl(value, history).valid
      }

    override def asArray: ValidatedNec[LDCodecFailure, LDArrayCursor] =
      if (value.getType == LDValueType.ARRAY) new ArrayCursorImpl(value, history).valid
      else LDCodecFailure.failed(wrongType(LDValueType.ARRAY, value.getType), history)

    override def asObject: ValidatedNec[LDCodecFailure, LDObjectCursor] =
      if (value.getType == LDValueType.OBJECT) new ObjectCursorImpl(value, history).valid
      else LDCodecFailure.failed(wrongType(LDValueType.OBJECT, value.getType), history)
  }

  private final class ArrayCursorImpl(
      override val value: LDValue,
      override val history: LDCursorHistory,
  ) extends LDArrayCursor {
    override def as[A: LDCodec]: ValidatedNec[LDCodecFailure, A] = LDCodec[A].decode(this)

    override def checkType(expected: LDValueType): ValidatedNec[LDCodecFailure, LDCursor] =
      if (expected == LDValueType.ARRAY) this.valid
      else LDCodecFailure.failed(wrongType(expected, value.getType), history)

    override def asObject: ValidatedNec[LDCodecFailure, LDObjectCursor] =
      LDCodecFailure.failed(wrongType(LDValueType.OBJECT, value.getType), history)

    override def asArray: ValidatedNec[LDCodecFailure, LDArrayCursor] = this.valid

    override def at(index: Int): ValidatedNec[LDCodecFailure, LDCursor] = {
      val updatedHistory = history.at(index)
      if (index >= 0 && index < value.size())
        new Impl(LDValue.normalize(value.get(index)), updatedHistory).valid
      else LDCodecFailure.failed(IndexOutOfBounds, updatedHistory)
    }
  }

  private final class ObjectCursorImpl(
      override val value: LDValue,
      override val history: LDCursorHistory,
  ) extends LDObjectCursor {
    override def as[A: LDCodec]: ValidatedNec[LDCodecFailure, A] = LDCodec[A].decode(this)

    override def checkType(expected: LDValueType): ValidatedNec[LDCodecFailure, LDCursor] =
      if (expected == LDValueType.OBJECT) this.valid
      else LDCodecFailure.failed(wrongType(expected, value.getType), history)

    override def asObject: ValidatedNec[LDCodecFailure, LDObjectCursor] = this.valid

    override def asArray: ValidatedNec[LDCodecFailure, LDArrayCursor] =
      LDCodecFailure.failed(wrongType(LDValueType.ARRAY, value.getType), history)

    override def at(field: String): ValidatedNec[LDCodecFailure, LDCursor] = {
      val updatedHistory = history.at(field)
      val result = LDValue.normalize(value.get(field))
      if (!result.isNull) new Impl(result, updatedHistory).valid
      else {
        // LDValue.get returns null when a field is missing, we can do better
        var found = false
        value.keys().iterator().forEachRemaining { key =>
          if (key == field) {
            found = true
          }
        }
        if (found) new Impl(result, updatedHistory).valid
        else LDCodecFailure.failed(missingField, updatedHistory)
      }
    }
  }
}
