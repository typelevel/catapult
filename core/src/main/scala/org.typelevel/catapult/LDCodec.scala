package org.typelevel.catapult

import cats.Show
import cats.data.{Chain, NonEmptyChain, NonEmptyList, NonEmptyVector, Validated, ValidatedNec}
import cats.syntax.all.*
import com.launchdarkly.sdk.{LDValue, LDValueType}
import org.typelevel.catapult.LDCodec.DecodingFailed
import org.typelevel.catapult.LDCodec.DecodingFailed.Reason
import org.typelevel.catapult.LDCursor.LDCursorHistory

import scala.collection.Factory

trait LDCodec[A] { self =>
  def encode(a: A): LDValue
  def decode(c: LDCursor): ValidatedNec[DecodingFailed, A]

  def decode(ld: LDValue): ValidatedNec[DecodingFailed, A] = decode(LDCursor.root(ld))

  def imap[B](bToA: B => A, aToB: A => B): LDCodec[B] = new LDCodec[B] {
    override def encode(a: B): LDValue = self.encode(bToA(a))

    override def decode(c: LDCursor): ValidatedNec[DecodingFailed, B] =
      self.decode(c).map(aToB)
  }

  def imapV[B](bToA: B => A, aToB: A => ValidatedNec[Reason, B]): LDCodec[B] = new LDCodec[B] {
    override def encode(a: B): LDValue = self.encode(bToA(a))

    override def decode(c: LDCursor): ValidatedNec[DecodingFailed, B] =
      self.decode(c).andThen { a =>
        aToB(a).leftMap(_.map(DecodingFailed(_, c.history)))
      }
  }

  def imapVFull[B](
      bToA: B => A,
      aToB: (A, LDCursorHistory) => ValidatedNec[DecodingFailed, B],
  ): LDCodec[B] = new LDCodec[B] {
    override def encode(a: B): LDValue = self.encode(bToA(a))

    override def decode(c: LDCursor): ValidatedNec[DecodingFailed, B] =
      self.decode(c).andThen(a => aToB(a, c.history))
  }
}
object LDCodec {
  def apply[A](implicit LDC: LDCodec[A]): LDC.type = LDC

  def instance[A](
      _encode: A => LDValue,
      _decode: LDCursor => ValidatedNec[DecodingFailed, A],
  ): LDCodec[A] =
    new LDCodec[A] {
      override def encode(a: A): LDValue = _encode(a)

      override def decode(c: LDCursor): ValidatedNec[DecodingFailed, A] = _decode(c)
    }

  sealed trait DecodingFailed {
    def reason: DecodingFailed.Reason

    def history: LDCursorHistory
  }

  object DecodingFailed {
    def apply(reason: Reason, history: LDCursorHistory): DecodingFailed = new Impl(reason, history)

    def failed[A](reason: Reason, history: LDCursorHistory): ValidatedNec[DecodingFailed, A] =
      apply(reason, history).invalidNec

    sealed trait Reason {
      def explain: String
      def explain(history: String): String
    }
    object Reason {
      case object MissingField extends Reason {
        override def explain: String = "Missing expected field"
        override def explain(history: String): String = s"Missing expected field at $history"
      }

      case object IndexOutOfBounds extends Reason {
        override def explain: String = "Out of bounds"
        override def explain(history: String): String = s"$history is out of bounds"
      }

      final case class WrongType(expected: LDValueType, actual: LDValueType) extends Reason {
        override def explain: String = s"Expected ${expected.name()}, but was ${actual.name()}"
        override def explain(history: String): String =
          s"Expected ${expected.name()} at $history, but was ${actual.name()}"
      }

      final case class UnableToDecodeKey(reason: Reason) extends Reason {
        override def explain: String = s"Unable to decode key (${reason.explain}"
        override def explain(history: String): String =
          s"Unable to decode key at $history (${reason.explain})"
      }

      final case class Other(reason: String) extends Reason {
        override def explain: String = reason
        override def explain(history: String): String = s"Failure at $history: $reason"
      }
    }

    implicit val show: Show[DecodingFailed] = Show.show { df =>
      df.reason.explain(df.history.show)
    }

    private final class Impl(val reason: Reason, val history: LDCursorHistory)
        extends DecodingFailed {
      override def toString: String = s"DecodingFailed($reason, ${history.show})"

      override def equals(obj: Any): Boolean = obj match {
        case that: DecodingFailed => this.reason == that.reason && this.history == that.history
        case _ => false
      }

      override def hashCode(): Int = ("DecodingFailure", reason, history).##
    }
  }

  implicit val ldValueInstance: LDCodec[LDValue] = new LDCodec[LDValue] {
    override def encode(a: LDValue): LDValue = a

    override def decode(c: LDCursor): ValidatedNec[DecodingFailed, LDValue] = c.value.valid
  }

  implicit val booleanInstance: LDCodec[Boolean] = new LDCodec[Boolean] {
    override def encode(a: Boolean): LDValue = LDValue.of(a)

    override def decode(c: LDCursor): ValidatedNec[DecodingFailed, Boolean] =
      c.checkType(LDValueType.BOOLEAN).map(_.value.booleanValue())
  }

  implicit val stringInstance: LDCodec[String] = new LDCodec[String] {
    override def encode(a: String): LDValue = LDValue.of(a)

    override def decode(c: LDCursor): ValidatedNec[DecodingFailed, String] =
      c.checkType(LDValueType.STRING).map(_.value.stringValue())
  }

  // This is the canonical encoding of numbers in an LDValue, other
  // numerical types are derived from this because of this constraint.
  implicit val doubleInstance: LDCodec[Double] = new LDCodec[Double] {
    override def encode(a: Double): LDValue = LDValue.of(a)

    override def decode(c: LDCursor): ValidatedNec[DecodingFailed, Double] =
      c.checkType(LDValueType.NUMBER).andThen { numCur =>
        val d = numCur.value.doubleValue()
        Validated.condNec(
          !d.isNaN,
          d,
          DecodingFailed(Reason.Other("Value is not a valid double"), numCur.history),
        )
      }
  }

  implicit val intInstance: LDCodec[Int] = LDCodec[Double].imapV[Int](
    _.toDouble,
    d => Validated.condNec(d.isValidInt, d.toInt, Reason.Other("Value is not a valid int")),
  )

  implicit val longInstance: LDCodec[Long] = LDCodec[Double].imapV[Long](
    _.toDouble,
    d => {
      val asLong = d.toLong
      val dByWayOfL = asLong.toDouble
      Validated.condNec(
        // Borrowed from commented out code in RichDouble
        dByWayOfL == dByWayOfL && asLong != Long.MaxValue,
        asLong,
        Reason.Other("Value is not a valid long"),
      )
    },
  )

  implicit val noneInstance: LDCodec[None.type] =
    new LDCodec[None.type] {
      override def encode(a: None.type): LDValue = LDValue.ofNull()

      override def decode(c: LDCursor): ValidatedNec[DecodingFailed, None.type] =
        c.checkType(LDValueType.NULL).as(None)
    }

  implicit def decodeSome[A: LDCodec]: LDCodec[Some[A]] =
    LDCodec[A].imap[Some[A]](_.value, Some(_))

  implicit def decodeOption[A: LDCodec]: LDCodec[Option[A]] = new LDCodec[Option[A]] {
    override def encode(a: Option[A]): LDValue =
      a.fold(LDValue.ofNull())(LDCodec[A].encode)

    override def decode(c: LDCursor): ValidatedNec[DecodingFailed, Option[A]] =
      noneInstance.decode(c).findValid(LDCodec[A].decode(c).map(_.some))
  }

  def ldArrayInstance[F[_], A: LDCodec](
      toIterator: F[A] => Iterator[A],
      factory: Factory[A, F[A]],
  ): LDCodec[F[A]] =
    new LDCodec[F[A]] {
      override def encode(fa: F[A]): LDValue = {
        val builder = LDValue.buildArray()
        toIterator(fa).foreach { elem =>
          builder.add(LDCodec[A].encode(elem))
        }
        builder.build()
      }

      override def decode(c: LDCursor): ValidatedNec[DecodingFailed, F[A]] =
        c.asArray
          .andThen(_.elements[A])
          .map(factory.fromSpecific(_))
    }

  implicit def vectorInstance[A: LDCodec]: LDCodec[Vector[A]] =
    ldArrayInstance[Vector, A](_.iterator, Vector)

  implicit def listInstance[A: LDCodec]: LDCodec[List[A]] =
    ldArrayInstance[List, A](_.iterator, List)

  implicit def chainInstance[A: LDCodec]: LDCodec[Chain[A]] =
    vectorInstance[A].imap[Chain[A]](_.toVector, Chain.fromSeq)

  implicit def nonEmptyListInstance[A: LDCodec]: LDCodec[NonEmptyList[A]] =
    listInstance[A].imapVFull[NonEmptyList[A]](
      _.toList,
      (list, history) =>
        NonEmptyList.fromList(list).toValidNec {
          DecodingFailed(Reason.IndexOutOfBounds, history.at(0))
        },
    )

  implicit def nonEmptyVectorInstance[A: LDCodec]: LDCodec[NonEmptyVector[A]] =
    vectorInstance[A].imapVFull[NonEmptyVector[A]](
      _.toVector,
      (vec, history) =>
        NonEmptyVector.fromVector(vec).toValidNec {
          DecodingFailed(Reason.IndexOutOfBounds, history.at(0))
        },
    )

  implicit def nonEmptyChainInstance[A: LDCodec]: LDCodec[NonEmptyChain[A]] =
    chainInstance[A].imapVFull[NonEmptyChain[A]](
      _.toChain,
      (chain, history) =>
        NonEmptyChain.fromChain(chain).toValidNec {
          DecodingFailed(Reason.IndexOutOfBounds, history.at(0))
        },
    )

  def ldObjectInstance[CC, K: LDKeyCodec, V: LDCodec](
      toIterator: CC => Iterator[(K, V)],
      factory: Factory[(K, V), CC],
  ): LDCodec[CC] =
    new LDCodec[CC] {
      override def encode(cc: CC): LDValue = {
        val builder = LDValue.buildObject()
        toIterator(cc).foreach { case (k, v) =>
          builder.put(LDKeyCodec[K].encode(k), LDCodec[V].encode(v))
        }
        builder.build()
      }

      override def decode(c: LDCursor): ValidatedNec[DecodingFailed, CC] =
        c.asObject
          .andThen(_.entries[K, V])
          .map(factory.fromSpecific(_))
    }

  implicit def mapInstance[K: LDKeyCodec, V: LDCodec]: LDCodec[Map[K, V]] =
    ldObjectInstance[Map[K, V], K, V](_.iterator, Map)
}
