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
import cats.data.{Chain, NonEmptyChain, ValidatedNec}
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
    * @note This is guaranteed to be non-null, but may be `LDValue.ofNull`
    */
  def value: ValidatedNec[LDCodecFailure, LDValue]

  def valueType: LDValueType = value.fold(_ => LDValueType.NULL, _.getType)

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
    * @see [[asObject]] if the expected type is `OBJECT`
    */
  def checkType(expected: LDValueType): LDCursor

  /** Ensure the type of `value` is `ARRAY` and return an `LDCursor`
    * specialized to working with `LDValue` arrays
    */
  def asArray: LDArrayCursor

  /** Ensure the type of `value` is `OBJECT` and return an `LDCursor`
    * specialized to working with `LDValue` objects
    */
  def asObject: LDObjectCursor

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
  implicit val hash: Hash[LDCursor] = Hash.by { c =>
    (
      c.value.getOrElse(LDValue.ofNull()),
      c.value.fold(_.toChain, _ => Chain.empty),
      c.history,
    )
  }

  /** An [[LDCursor]] that is specialized to work with `LDValue` arrays
    */
  sealed trait LDArrayCursor extends LDCursor {

    /** Descend to the given index
      *
      * @note Bounds checking will be done on `index`
      */
    def at(index: Int): LDCursor

    /** Attempt to decode the value at the given index as an `A`
      *
      * @note Bounds checking will be done on `index`
      */
    def get[A: LDCodec](index: Int): ValidatedNec[LDCodecFailure, A] =
      at(index).as[A]
  }

  /** An [[LDCursor]] that is specialized to work with `LDValue` objects
    */
  sealed trait LDObjectCursor extends LDCursor {

    /** Descend to value at the given field
      */
    def at(field: String): LDCursor

    /** Attempt to decode the value the given field as an `A`
      */
    def get[A: LDCodec](field: String): ValidatedNec[LDCodecFailure, A] =
      at(field).as[A]
  }

  private final class Impl(ldValue: LDValue, override val history: LDCursorHistory)
      extends LDCursor {

    override def value: ValidatedNec[LDCodecFailure, LDValue] = ldValue.valid

    override def as[A: LDCodec]: ValidatedNec[LDCodecFailure, A] = LDCodec[A].decode(ldValue)

    override def checkType(expected: LDValueType): LDCursor =
      ldValue.getType match {
        case actual if actual != expected =>
          FailedCursor.one(wrongType(expected, ldValue.getType), history)
        case LDValueType.ARRAY => new ArrayCursorImpl(ldValue, history)
        case LDValueType.OBJECT => new ObjectCursorImpl(ldValue, history)
        case _ => this
      }

    override def asArray: LDArrayCursor =
      if (ldValue.getType == LDValueType.ARRAY) new ArrayCursorImpl(ldValue, history)
      else
        FailedCursor.one(wrongType(LDValueType.ARRAY, ldValue.getType), history)

    override def asObject: LDObjectCursor =
      if (ldValue.getType == LDValueType.OBJECT) new ObjectCursorImpl(ldValue, history)
      else FailedCursor.one(wrongType(LDValueType.OBJECT, ldValue.getType), history)
  }

  private final class FailedCursor(failures: NonEmptyChain[LDCodecFailure])
      extends LDCursor
      with LDArrayCursor
      with LDObjectCursor {
    override def value: ValidatedNec[LDCodecFailure, LDValue] = failures.invalid

    override def as[A: LDCodec]: ValidatedNec[LDCodecFailure, A] = failures.invalid

    override def checkType(expected: LDValueType): LDCursor = this

    override def asArray: LDArrayCursor = this

    override def asObject: LDObjectCursor = this

    override def at(index: Int): LDCursor = this

    override def at(field: String): LDCursor = this

    override def history: LDCursorHistory = failures.head.history
  }
  private object FailedCursor {
    def one(reason: LDReason, history: LDCursorHistory): FailedCursor =
      new FailedCursor(NonEmptyChain.one(LDCodecFailure(reason, history)))
  }

  private final class ArrayCursorImpl(
      ldValue: LDValue,
      override val history: LDCursorHistory,
  ) extends LDArrayCursor {
    override def value: ValidatedNec[LDCodecFailure, LDValue] = ldValue.valid

    override def as[A: LDCodec]: ValidatedNec[LDCodecFailure, A] = LDCodec[A].decode(ldValue)

    override def checkType(expected: LDValueType): LDCursor =
      if (expected == LDValueType.ARRAY) this
      else FailedCursor.one(wrongType(expected, ldValue.getType), history)

    override def asObject: LDObjectCursor =
      FailedCursor.one(wrongType(LDValueType.OBJECT, ldValue.getType), history)

    override def asArray: LDArrayCursor = this

    override def at(index: Int): LDCursor = {
      val updatedHistory = history.at(index)
      if (index >= 0 && index < ldValue.size())
        new Impl(LDValue.normalize(ldValue.get(index)), updatedHistory)
      else FailedCursor.one(IndexOutOfBounds, updatedHistory)
    }
  }

  private final class ObjectCursorImpl(
      ldValue: LDValue,
      override val history: LDCursorHistory,
  ) extends LDObjectCursor {
    override def value: ValidatedNec[LDCodecFailure, LDValue] = ldValue.valid

    override def as[A: LDCodec]: ValidatedNec[LDCodecFailure, A] = LDCodec[A].decode(ldValue)

    override def checkType(expected: LDValueType): LDCursor =
      if (expected == LDValueType.OBJECT) this
      else FailedCursor.one(wrongType(expected, ldValue.getType), history)

    override def asObject: LDObjectCursor = this

    override def asArray: LDArrayCursor =
      FailedCursor.one(wrongType(LDValueType.ARRAY, ldValue.getType), history)

    override def at(field: String): LDCursor = {
      val updatedHistory = history.at(field)
      val result = LDValue.normalize(ldValue.get(field))
      if (!result.isNull) new Impl(result, updatedHistory)
      else {
        // LDValue.get returns null when a field is missing, we can do better
        var found = false
        ldValue.keys().iterator().forEachRemaining { key =>
          if (key == field) {
            found = true
          }
        }
        if (found) new Impl(result, updatedHistory)
        else FailedCursor.one(missingField, updatedHistory)
      }
    }
  }
}
