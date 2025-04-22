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

trait LDKeyCodec[A] { self =>
  def encode(a: A): String
  def decode(str: String): ValidatedNec[Reason, A]

  def imap[B](bToA: B => A, aToB: A => B): LDKeyCodec[B] = new LDKeyCodec[B] {
    override def encode(a: B): String = self.encode(bToA(a))

    override def decode(str: String): ValidatedNec[Reason, B] =
      self.decode(str).map(aToB)
  }

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
