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

import cats.Invariant
import cats.syntax.all.*
import com.launchdarkly.sdk.{LDValue, LDValueType}
import org.typelevel.catapult.codec.LDCodec.{
  LDCodecResult,
  withInfallibleEncode,
  withInfallibleEncodeFull,
}

trait LDCodecWithInfallibleEncode[A] extends LDCodec[A] {

  /** Encode a value to `LDValue`
    */
  def safeEncode(a: A): LDValue

  override def encode(a: A, history: LDCursorHistory): LDCodecResult[LDValue] =
    safeEncode(a).valid

  override def imapVFull[B](
      bToA: (B, LDCursorHistory) => LDCodecResult[A],
      aToB: (A, LDCursorHistory) => LDCodecResult[B],
  ): LDCodec[B] =
    LDCodecWithInfallibleEncode.imap(this, bToA, aToB)
}
object LDCodecWithInfallibleEncode {
  def apply[A](implicit C: LDCodecWithInfallibleEncode[A]): C.type = C

  def instance[A](_encode: A => LDValue, _decode: LDCursor => A): LDCodecWithInfallibleEncode[A] =
    instanceFull(_encode, _decode(_).valid)

  def instanceFull[A](
      _encode: A => LDValue,
      _decode: LDCursor => LDCodecResult[A],
  ): LDCodecWithInfallibleEncode[A] =
    new LDCodecWithInfallibleEncode[A] {
      override def safeEncode(a: A): LDValue = _encode(a)
      override def decode(c: LDCursor): LDCodecResult[A] = _decode(c)
    }

  implicit val ldValueInstance: LDCodecWithInfallibleEncode[LDValue] =
    withInfallibleEncode(identity, _.value)

  implicit val booleanInstance: LDCodecWithInfallibleEncode[Boolean] = withInfallibleEncodeFull(
    LDValue.of,
    _.checkType(LDValueType.BOOLEAN).map(_.value.booleanValue()),
  )

  implicit val stringInstance: LDCodecWithInfallibleEncode[String] = withInfallibleEncodeFull(
    LDValue.of,
    _.checkType(LDValueType.STRING).map(_.value.stringValue()),
  )

  // This is the canonical encoding of numbers in an LDValue, other
  // numerical types are derived from this because of this constraint.
  implicit val doubleInstance: LDCodecWithInfallibleEncode[Double] = withInfallibleEncodeFull(
    LDValue.of,
    _.checkType(LDValueType.NUMBER).map(_.value.doubleValue()),
  )

  implicit val invariant: Invariant[LDCodecWithInfallibleEncode] =
    new Invariant[LDCodecWithInfallibleEncode] {
      override def imap[A, B](
          codec: LDCodecWithInfallibleEncode[A]
      )(aToB: A => B)(bToA: B => A): LDCodecWithInfallibleEncode[B] =
        new LDCodecWithInfallibleEncode[B] {
          override def safeEncode(b: B): LDValue = codec.safeEncode(bToA(b))

          override def decode(c: LDCursor): LDCodecResult[B] = codec.decode(c).map(aToB)
        }
    }

  private def imap[A, B](
      codec: LDCodecWithInfallibleEncode[A],
      bToA: (B, LDCursorHistory) => LDCodecResult[A],
      aToB: (A, LDCursorHistory) => LDCodecResult[B],
  ): LDCodec[B] =
    new LDCodec[B] {
      override def encode(b: B, history: LDCursorHistory): LDCodecResult[LDValue] =
        bToA(b, history).map(codec.safeEncode)

      override def decode(c: LDCursor): LDCodecResult[B] =
        codec.decode(c).andThen(aToB(_, c.history))
    }
}
