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
