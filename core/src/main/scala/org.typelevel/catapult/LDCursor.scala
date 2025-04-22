package org.typelevel.catapult

import cats.Show
import cats.data.{Chain, ValidatedNec}
import cats.syntax.all.*
import com.launchdarkly.sdk.{LDValue, LDValueType}
import org.typelevel.catapult.LDCodec.DecodingFailed
import org.typelevel.catapult.LDCodec.DecodingFailed.Reason.{
  IndexOutOfBounds,
  MissingField,
  WrongType,
}
import org.typelevel.catapult.LDCursor.LDCursorHistory.Move
import org.typelevel.catapult.LDCursor.{LDArrayCursor, LDCursorHistory, LDObjectCursor}

sealed trait LDCursor {
  def value: LDValue

  def history: LDCursorHistory

  def as[A: LDCodec]: ValidatedNec[DecodingFailed, A]

  def checkType(expected: LDValueType): ValidatedNec[DecodingFailed, LDCursor]

  def asArray: ValidatedNec[DecodingFailed, LDArrayCursor]

  def asObject: ValidatedNec[DecodingFailed, LDObjectCursor]
}

object LDCursor {
  def root(value: LDValue): LDCursor = new Impl(LDValue.normalize(value), LDCursorHistory.root)
  def of(value: LDValue, history: LDCursorHistory): LDCursor =
    new Impl(LDValue.normalize(value), history)

  sealed trait LDArrayCursor extends LDCursor {
    def at(index: Int): ValidatedNec[DecodingFailed, LDCursor]

    def get[A: LDCodec](index: Int): ValidatedNec[DecodingFailed, A] =
      at(index).andThen(_.as[A])

    def elements[A: LDCodec]: ValidatedNec[DecodingFailed, Vector[A]]
  }

  sealed trait LDObjectCursor extends LDCursor {
    def at(field: String): ValidatedNec[DecodingFailed, LDCursor]

    def get[A: LDCodec](field: String): ValidatedNec[DecodingFailed, A] =
      at(field).andThen(_.as[A])

    def entries[K: LDKeyCodec, V: LDCodec]: ValidatedNec[DecodingFailed, Vector[(K, V)]]
  }

  sealed trait LDCursorHistory {
    def moves: Chain[Move]
    def at(field: String): LDCursorHistory
    def at(index: Int): LDCursorHistory
  }
  object LDCursorHistory {
    def root: LDCursorHistory = HistoryImpl(Chain.empty)
    def of(moves: Chain[Move]): LDCursorHistory = HistoryImpl(moves)

    private final case class HistoryImpl(moves: Chain[Move]) extends LDCursorHistory {
      override def at(field: String): LDCursorHistory = HistoryImpl(moves.append(Move.Field(field)))

      override def at(index: Int): LDCursorHistory = HistoryImpl(moves.append(Move.Index(index)))
    }

    sealed trait Move

    object Move {
      final case class Field(name: String) extends Move

      final case class Index(index: Int) extends Move

      implicit val show: Show[Move] = Show.show {
        case Field(name) if name.forall(c => c.isLetterOrDigit || c == '_' || c == '-') => s".$name"
        case Field(name) => s"[$name]"
        case Index(index) => s"[$index]"
      }
    }

    implicit val show: Show[LDCursorHistory] = Show.show(_.moves.mkString_("$", "", ""))
  }

  private final class Impl(override val value: LDValue, override val history: LDCursorHistory)
      extends LDCursor {
    override def as[A: LDCodec]: ValidatedNec[DecodingFailed, A] = LDCodec[A].decode(value)

    override def checkType(expected: LDValueType): ValidatedNec[DecodingFailed, LDCursor] =
      value.getType match {
        case actual if actual != expected =>
          DecodingFailed.failed(WrongType(expected, value.getType), history)
        case LDValueType.ARRAY => new ArrayCursorImpl(value, history).valid
        case LDValueType.OBJECT => new ObjectCursorImpl(value, history).valid
        case _ => new Impl(value, history).valid
      }

    override def asArray: ValidatedNec[DecodingFailed, LDArrayCursor] =
      if (value.getType == LDValueType.ARRAY) new ArrayCursorImpl(value, history).valid
      else DecodingFailed.failed(WrongType(LDValueType.ARRAY, value.getType), history)

    override def asObject: ValidatedNec[DecodingFailed, LDObjectCursor] =
      if (value.getType == LDValueType.OBJECT) new ObjectCursorImpl(value, history).valid
      else DecodingFailed.failed(WrongType(LDValueType.OBJECT, value.getType), history)
  }

  private final class ArrayCursorImpl(
      override val value: LDValue,
      override val history: LDCursorHistory,
  ) extends LDArrayCursor {
    override def as[A: LDCodec]: ValidatedNec[DecodingFailed, A] = LDCodec[A].decode(this)

    override def checkType(expected: LDValueType): ValidatedNec[DecodingFailed, LDCursor] =
      if (expected == LDValueType.ARRAY) this.valid
      else DecodingFailed.failed(WrongType(expected, value.getType), history)

    override def asObject: ValidatedNec[DecodingFailed, LDObjectCursor] =
      DecodingFailed.failed(WrongType(LDValueType.OBJECT, value.getType), history)

    override def asArray: ValidatedNec[DecodingFailed, LDArrayCursor] = this.valid

    override def at(index: Int): ValidatedNec[DecodingFailed, LDCursor] = {
      val updatedHistory = history.at(index)
      if (index >= 0 && index < value.size())
        new Impl(LDValue.normalize(value.get(index)), updatedHistory).valid
      else DecodingFailed.failed(IndexOutOfBounds, updatedHistory)
    }

    override def elements[A: LDCodec]: ValidatedNec[DecodingFailed, Vector[A]] = {
      val builder = Vector.newBuilder[ValidatedNec[DecodingFailed, A]]
      var idx = 0
      value.values().forEach { ldValue =>
        builder.addOne(
          LDCodec[A].decode(
            LDCursor.of(
              LDValue.normalize(ldValue),
              history.at(idx),
            )
          )
        )
        idx = idx + 1
      }
      builder.result().sequence
    }
  }

  private final class ObjectCursorImpl(
      override val value: LDValue,
      override val history: LDCursorHistory,
  ) extends LDObjectCursor {
    override def as[A: LDCodec]: ValidatedNec[DecodingFailed, A] = LDCodec[A].decode(this)

    override def checkType(expected: LDValueType): ValidatedNec[DecodingFailed, LDCursor] =
      if (expected == LDValueType.OBJECT) this.valid
      else DecodingFailed.failed(WrongType(expected, value.getType), history)

    override def asObject: ValidatedNec[DecodingFailed, LDObjectCursor] = this.valid

    override def asArray: ValidatedNec[DecodingFailed, LDArrayCursor] =
      DecodingFailed.failed(WrongType(LDValueType.ARRAY, value.getType), history)

    override def at(field: String): ValidatedNec[DecodingFailed, LDCursor] = {
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
        else DecodingFailed.failed(MissingField, updatedHistory)
      }
    }

    override def entries[K: LDKeyCodec, V: LDCodec]
        : ValidatedNec[DecodingFailed, Vector[(K, V)]] = {
      val builder = Vector.newBuilder[ValidatedNec[DecodingFailed, (K, V)]]
      value.keys().forEach { field =>
        val updatedHistory = history.at(field)
        val decodedEntry = (
          LDKeyCodec[K].decode(field).leftMap(_.map(DecodingFailed(_, updatedHistory))),
          LDCodec[V].decode(LDCursor.of(LDValue.normalize(value.get(field)), updatedHistory)),
        ).tupled
        builder.addOne(decodedEntry)
      }
      builder.result().sequence
    }
  }
}
