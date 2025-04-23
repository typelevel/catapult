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

import cats.data.ValidatedNec
import cats.syntax.all.*
import org.typelevel.catapult.LDCodec.DecodingFailed.Reason

/** A type class that provides a conversion from a `String` to used as an `LDValue` object key to and from a value of type `A`
  */
trait LDKeyCodec[A] { self =>

  /** Encode a given `A` to a `String` to use as an `LDValue` object key
    *
    * @note If more than one `A` maps to the same `String` the behavior of the duplication
    *       is determined by the `LValue` implementation.
    */
  def encode(a: A): String

  /** Attempt to decode a `String` from an `LValue` object key as an `A`
    *
    * @note If more than one `String` maps to the same `A`, the resulting value may have
    *       fewer entries than the original `LDValue`
    */
  def decode(str: String): ValidatedNec[Reason, A]

  /** Transform a `LDKeyCodec[A]` into an `LDKeyCodec[B]` by providing a transformation
    * from `A` to `B` and one from `B` to `A`
    *
    * @see [[imapV]] if the transformations can fail
    */
  def imap[B](bToA: B => A, aToB: A => B): LDKeyCodec[B] = new LDKeyCodec[B] {
    override def encode(a: B): String = self.encode(bToA(a))

    override def decode(str: String): ValidatedNec[Reason, B] =
      self.decode(str).map(aToB)
  }

  /** A variant of [[imap]] which allows the transformation from `A` to `B`
    * to fail
    */
  def imapV[B](bToA: B => A, aToB: A => ValidatedNec[Reason, B]): LDKeyCodec[B] =
    new LDKeyCodec[B] {
      override def encode(a: B): String = self.encode(bToA(a))

      override def decode(str: String): ValidatedNec[Reason, B] =
        self.decode(str).andThen(aToB(_))
    }
}
object LDKeyCodec {
  def apply[A](implicit KC: LDKeyCodec[A]): KC.type = KC

  implicit val stringInstance: LDKeyCodec[String] = new LDKeyCodec[String] {
    override def encode(a: String): String = a

    override def decode(str: String): ValidatedNec[Reason, String] = str.valid
  }
}
