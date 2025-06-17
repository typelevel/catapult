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
import cats.data.ValidatedNec
import cats.syntax.all.*

/** A type class that provides a conversion from a `String` to used as an `LDValue` object key to and from a value of type `A`
  */
trait LDKeyCodec[A] { self =>

  /** Encode a given `A` to a `String` to use as an `LDValue` object key
    *
    * @note If more than one `A` maps to the same `String` the behavior of the duplication
    *       is determined by the `LValue` implementation.
    */
  def encode(a: A): ValidatedNec[LDReason, String]

  /** Attempt to decode a `String` from an `LValue` object key as an `A`
    *
    * @note If more than one `String` maps to the same `A`, the resulting value may have
    *       fewer entries than the original `LDValue`
    */
  def decode(str: String): ValidatedNec[LDReason, A]

  /** Transform a `LDKeyCodec[A]` into an `LDKeyCodec[B]` by providing a transformation
    * from `A` to `B` and one from `B` to `A`
    */
  def imapV[B](
      bToA: B => ValidatedNec[LDReason, A],
      aToB: A => ValidatedNec[LDReason, B],
  ): LDKeyCodec[B] =
    new LDKeyCodec[B] {
      override def encode(b: B): ValidatedNec[LDReason, String] = bToA(b).andThen(self.encode)

      override def decode(str: String): ValidatedNec[LDReason, B] =
        self.decode(str).andThen(aToB(_))
    }
}
object LDKeyCodec {
  def apply[A](implicit KC: LDKeyCodec[A]): KC.type = KC

  implicit def promoteTheSubclass[A](implicit KC: WithInfallibleEncode[A]): LDKeyCodec[A] = KC

  implicit val invariant: Invariant[LDKeyCodec] = new Invariant[LDKeyCodec] {
    override def imap[A, B](codec: LDKeyCodec[A])(aToB: A => B)(bToA: B => A): LDKeyCodec[B] =
      new LDKeyCodec[B] {
        override def encode(a: B): ValidatedNec[LDReason, String] = codec.encode(bToA(a))
        override def decode(str: String): ValidatedNec[LDReason, B] = codec.decode(str).map(aToB)
      }
  }

  trait WithInfallibleEncode[A] extends LDKeyCodec[A] {
    def safeEncode(a: A): String
    override def encode(a: A): ValidatedNec[LDReason, String] = safeEncode(a).valid
  }
  object WithInfallibleEncode {
    implicit val invariant: Invariant[WithInfallibleEncode] = new Invariant[WithInfallibleEncode] {
      override def imap[A, B](
          codec: WithInfallibleEncode[A]
      )(aToB: A => B)(bToA: B => A): WithInfallibleEncode[B] =
        new WithInfallibleEncode[B] {
          override def safeEncode(b: B): String = codec.safeEncode(bToA(b))
          override def decode(str: String): ValidatedNec[LDReason, B] = codec.decode(str).map(aToB)
        }
    }

    implicit val stringInstance: WithInfallibleEncode[String] = new WithInfallibleEncode[String] {
      override def safeEncode(a: String): String = a
      override def decode(str: String): ValidatedNec[LDReason, String] = str.valid
    }
  }
}
