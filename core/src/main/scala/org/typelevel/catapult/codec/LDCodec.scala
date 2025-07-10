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

import cats.{Defer, Invariant}
import cats.data.*
import cats.syntax.all.*
import com.launchdarkly.sdk.{LDValue, LDValueType}
import org.typelevel.catapult.codec.LDCodec.LDCodecResult
import org.typelevel.catapult.codec.LDReason.{unableToDecodeKey, unableToEncodeKey}

import scala.annotation.tailrec
import scala.collection.Factory
import scala.reflect.ClassTag

/** A type class that provides a way to convert a value of type `A` to and from `LDValue`
  */
trait LDCodec[A] {

  /** Encode a value to `LDValue`
    *
    * This may fail
    */
  def encode(a: A, history: LDCursorHistory): LDCodecResult[LDValue]

  /** Encode a value to `LDValue`
    *
    * This may fail
    */
  def encode(a: A): LDCodecResult[LDValue] = encode(a, LDCursorHistory.root)

  /** Decode the given `LDCursor`
    */
  def decode(c: LDCursor): LDCodecResult[A]

  /** Decode the given `LDValue`
    */
  def decode(ld: LDValue): LDCodecResult[A] = decode(LDCursor.root(ld))

  /** A variant of `imap`` which allows the transformation from `A` to `B`
    * to fail
    *
    * @see [[imapVFull]] if the failure can affect the history
    * @note `imap` is provided by [[cats.Invariant]]
    */
  def imapV[B](
      bToA: B => ValidatedNec[LDReason, A],
      aToB: A => ValidatedNec[LDReason, B],
  ): LDCodec[B] =
    imapVFull(
      (b, history) => bToA(b).leftMap(_.map(LDCodecFailure(_, history))),
      (a, history) => aToB(a).leftMap(_.map(LDCodecFailure(_, history))),
    )

  /** A variant of `imap` which allows the transformation from A` to `B`
    * to fail with an updated history
    *
    * @see [[imapV]] for a simpler API when updating the history is not needed
    * @note `imap` is provided by [[cats.Invariant]]
    */
  def imapVFull[B](
      bToA: (B, LDCursorHistory) => LDCodecResult[A],
      aToB: (A, LDCursorHistory) => LDCodecResult[B],
  ): LDCodec[B] = LDCodec.imapVFull(this, bToA, aToB)
}
object LDCodec {
  def apply[A](implicit LDC: LDCodec[A]): LDC.type = LDC

  type LDCodecResult[A] = ValidatedNec[LDCodecFailure, A]

  def instance[A](
      _encode: (A, LDCursorHistory) => LDCodecResult[LDValue],
      _decode: LDCursor => LDCodecResult[A],
  ): LDCodec[A] =
    new LDCodec[A] {
      override def encode(a: A, history: LDCursorHistory): LDCodecResult[LDValue] =
        _encode(a, history)
      override def decode(c: LDCursor): LDCodecResult[A] = _decode(c)
    }

  def withInfallibleEncode[A](
      encode: A => LDValue,
      decode: LDCursor => A,
  ): LDCodecWithInfallibleEncode[A] =
    LDCodecWithInfallibleEncode.instance(encode, decode)

  def withInfallibleEncodeFull[A](
      encode: A => LDValue,
      decode: LDCursor => LDCodecResult[A],
  ): LDCodecWithInfallibleEncode[A] =
    LDCodecWithInfallibleEncode.instanceFull(encode, decode)

  final class DecodingFailure(val failures: NonEmptyChain[LDCodecFailure])
      extends IllegalArgumentException {
    override def getMessage: String =
      failures.mkString_("Failed to decode from LDValue:\n", "\n", "\n")
  }

  final class EncodingFailure(val failures: NonEmptyChain[LDCodecFailure])
      extends IllegalArgumentException {
    override def getMessage: String =
      failures.mkString_("Failed to encode to LDValue:\n", "\n", "\n")
  }

  implicit def promoteTheSubclass[A](implicit LA: LDCodecWithInfallibleEncode[A]): LDCodec[A] = LA

  private def imapVFull[A, B](
      codec: LDCodec[A],
      bToA: (B, LDCursorHistory) => LDCodecResult[A],
      aToB: (A, LDCursorHistory) => LDCodecResult[B],
  ): LDCodec[B] =
    new LDCodec[B] {
      override def encode(b: B, history: LDCursorHistory): LDCodecResult[LDValue] =
        bToA(b, history).andThen(codec.encode(_, history))

      override def decode(c: LDCursor): LDCodecResult[B] =
        codec.decode(c).andThen(aToB(_, c.history))
    }

  implicit val invariant: Invariant[LDCodec] = new Invariant[LDCodec] {
    override def imap[A, B](codec: LDCodec[A])(aToB: A => B)(bToA: B => A): LDCodec[B] =
      new LDCodec[B] {
        override def encode(b: B, history: LDCursorHistory): LDCodecResult[LDValue] =
          codec.encode(bToA(b), history)
        override def decode(c: LDCursor): LDCodecResult[B] = codec.decode(c).map(aToB)
      }
  }

  private final case class Deferred[A](fa: () => LDCodec[A]) extends LDCodec[A] {
    private lazy val resolved: LDCodec[A] = {
      @tailrec
      def loop(f: () => LDCodec[A]): LDCodec[A] =
        f() match {
          case Deferred(f) => loop(f)
          case next => next
        }

      loop(fa)
    }

    override def encode(a: A, history: LDCursorHistory): LDCodecResult[LDValue] =
      resolved.encode(a, history)

    override def decode(c: LDCursor): LDCodecResult[A] = resolved.decode(c)
  }
  implicit val defer: Defer[LDCodec] = new Defer[LDCodec] {
    override def defer[A](fa: => LDCodec[A]): LDCodec[A] = {
      lazy val cachedFa = fa
      Deferred(() => cachedFa)
    }
  }

  def numericInstance[N](
      typeName: String,
      toDouble: N => Double,
      fromDouble: Double => N,
  ): LDCodec[N] = new LDCodec[N] {
    override def encode(n: N, history: LDCursorHistory): LDCodecResult[LDValue] = {
      val d = toDouble(n)
      Validated.condNec(
        toDouble(n) == d,
        LDValue.of(d),
        LDCodecFailure(LDReason.undecodableValue(LDValueType.NUMBER, typeName), history)
      )
    }

    override def decode(c: LDCursor): LDCodecResult[N] =
      c.checkType(LDValueType.NUMBER).map(_.value.doubleValue()).andThen { d =>
        val n = fromDouble(d)
        Validated.condNec(
          toDouble(n) == d,
          n,
          c.fail(LDReason.unencodableValue(LDValueType.NUMBER, typeName)),
        )
      }
  }

  implicit val floatInstance: LDCodec[Float] = numericInstance("Float", _.toDouble, _.toFloat)

  implicit val intInstance: LDCodec[Int] = numericInstance("Int", _.toDouble, _.toInt)

  implicit val longInstance: LDCodec[Long] = numericInstance("Long", _.toDouble, _.toLong)

  implicit val noneInstance: LDCodecWithInfallibleEncode[None.type] = withInfallibleEncodeFull(
    _ => LDValue.ofNull(),
    _.checkType(LDValueType.NULL).as(None),
  )

  implicit def decodeSome[A, C[_] <: LDCodec[?]](implicit CA: C[A], I: Invariant[C]): C[Some[A]] =
    CA.imap[Some[A]](Some(_))(_.value)

  implicit def decodeOption[A: LDCodec]: LDCodec[Option[A]] =
    LDCodec.instance[Option[A]](
      (opt, history) =>
        opt.fold(LDValue.ofNull().validNec[LDCodecFailure])(LDCodec[A].encode(_, history)),
      c => noneInstance.decode(c).findValid(LDCodec[A].decode(c).map(_.some)),
    )

  private def decodeIterableShaped[CC, A](factory: Factory[A, CC])(cursor: LDCursor)(implicit
      CA: LDCodec[A]
  ): LDCodecResult[CC] = cursor.checkType(LDValueType.ARRAY).andThen { c =>
    val builder = factory.newBuilder
    val failures = Vector.newBuilder[LDCodecFailure]
    builder.sizeHint(c.value.size())
    var idx = 0
    c.value.values().forEach { ldValue =>
      CA.decode(LDCursor.of(LDValue.normalize(ldValue), c.history.at(idx))) match {
        case Validated.Invalid(e) => failures.addAll(e.iterator)
        case Validated.Valid(value) => builder.addOne(value)
      }
      idx = idx + 1
    }
    NonEmptyChain
      .fromChain(Chain.fromSeq(failures.result()))
      .toInvalid(builder.result())
  }

  def makeIterableWithInfallibleEncodeInstance[F[_], A](
      toIterator: F[A] => Iterator[A],
      factory: Factory[A, F[A]],
  )(implicit CA: LDCodecWithInfallibleEncode[A]): LDCodecWithInfallibleEncode[F[A]] =
    LDCodecWithInfallibleEncode.instanceFull[F[A]](
      fa => {
        val builder = LDValue.buildArray()
        toIterator(fa).foreach { elem =>
          builder.add(CA.safeEncode(elem))
        }
        builder.build()
      },
      decodeIterableShaped(factory),
    )

  def makeIterableInstance[F[_], A](toIterator: F[A] => Iterator[A], factory: Factory[A, F[A]])(
      implicit CA: LDCodec[A]
  ): LDCodec[F[A]] =
    LDCodec.instance[F[A]](
      (fa, history) => {
        val builder = LDValue.buildArray()
        val failures = Vector.newBuilder[LDCodecFailure]
        toIterator(fa).zipWithIndex.foreach { case (elem, index) =>
          LDCodec[A].encode(elem, history.at(index)) match {
            case Validated.Valid(a) => builder.add(a)
            case Validated.Invalid(e) => failures.addAll(e.iterator)
          }
        }
        NonEmptyChain
          .fromChain(Chain.fromSeq(failures.result()))
          .toInvalid(builder.build())
      },
      decodeIterableShaped(factory),
    )

  implicit def iterableWithInfallibleEncodeInstance[A: LDCodecWithInfallibleEncode]
      : LDCodecWithInfallibleEncode[Iterable[A]] =
    makeIterableWithInfallibleEncodeInstance[Iterable, A](_.iterator, Iterable)

  implicit def iterableInstance[A: LDCodec]: LDCodec[Iterable[A]] =
    makeIterableInstance[Iterable, A](_.iterator, Iterable)

  implicit def arrayWithInfallibleEncodeInstance[A: ClassTag: LDCodecWithInfallibleEncode]
      : LDCodecWithInfallibleEncode[Array[A]] =
    makeIterableWithInfallibleEncodeInstance[Array, A](_.iterator, Array)

  implicit def arrayInstance[A: ClassTag: LDCodec]: LDCodec[Array[A]] =
    makeIterableInstance[Array, A](_.iterator, Array)

  implicit def vectorWithInfallibleEncodeInstance[A: LDCodecWithInfallibleEncode]
      : LDCodecWithInfallibleEncode[Vector[A]] =
    makeIterableWithInfallibleEncodeInstance[Vector, A](_.iterator, Vector)

  implicit def vectorInstance[A: LDCodec]: LDCodec[Vector[A]] =
    makeIterableInstance[Vector, A](_.iterator, Vector)

  implicit def listWithInfallibleEncodeInstance[A: LDCodecWithInfallibleEncode]
      : LDCodecWithInfallibleEncode[List[A]] =
    makeIterableWithInfallibleEncodeInstance[List, A](_.iterator, List)

  implicit def listInstance[A: LDCodec]: LDCodec[List[A]] =
    makeIterableInstance[List, A](_.iterator, List)

  implicit def chainInstance[A, C[_] <: LDCodec[?]](implicit
      CVA: C[Vector[A]],
      I: Invariant[C],
  ): C[Chain[A]] =
    CVA.imap[Chain[A]](Chain.fromSeq)(_.toVector)

  implicit def nonEmptyListInstance[A](implicit CLA: LDCodec[List[A]]): LDCodec[NonEmptyList[A]] =
    CLA.imapVFull[NonEmptyList[A]](
      (nel, _) => nel.toList.valid,
      (list, history) =>
        NonEmptyList.fromList(list).toValidNec {
          LDCodecFailure(LDReason.IndexOutOfBounds, history.at(0))
        },
    )

  implicit def nonEmptyVectorInstance[A](implicit
      CVA: LDCodec[Vector[A]]
  ): LDCodec[NonEmptyVector[A]] =
    CVA.imapVFull[NonEmptyVector[A]](
      (nev, _) => nev.toVector.valid,
      (vec, history) =>
        NonEmptyVector.fromVector(vec).toValidNec {
          LDCodecFailure(LDReason.IndexOutOfBounds, history.at(0))
        },
    )

  implicit def nonEmptyChainInstance[A](implicit
      CCA: LDCodec[Chain[A]]
  ): LDCodec[NonEmptyChain[A]] =
    CCA.imapVFull[NonEmptyChain[A]](
      (nec, _) => nec.toChain.valid,
      (chain, history) =>
        NonEmptyChain.fromChain(chain).toValidNec {
          LDCodecFailure(LDReason.IndexOutOfBounds, history.at(0))
        },
    )

  private def decodeObjectShaped[CC, K, V](factory: Factory[(K, V), CC])(cursor: LDCursor)(implicit
      CK: LDKeyCodec[K],
      CV: LDCodec[V],
  ): LDCodecResult[CC] = cursor.checkType(LDValueType.OBJECT).andThen { c =>
    val builder = factory.newBuilder
    val failures = Vector.newBuilder[LDCodecFailure]
    builder.sizeHint(c.value.size())
    c.value.keys().forEach { field =>
      val updatedHistory = c.history.at(field)
      LDKeyCodec[K].decode(field) match {
        case Validated.Invalid(reasons) =>
          failures.addAll {
            reasons.map { reason =>
              LDCodecFailure(unableToDecodeKey(reason), updatedHistory)
            }.iterator
          }
        case Validated.Valid(key) =>
          LDCodec[V].decode(
            LDCursor.of(LDValue.normalize(c.value.get(field)), updatedHistory)
          ) match {
            case Validated.Invalid(e) => failures.addAll(e.iterator)
            case Validated.Valid(value) => builder.addOne(key -> value)
          }
      }
    }
    NonEmptyChain
      .fromChain(Chain.fromSeq(failures.result()))
      .toInvalid(builder.result())
  }

  def makeObjectShapedWithInfallibleEncodeInstance[CC, K, V](
      toIterator: CC => Iterator[(K, V)],
      factory: Factory[(K, V), CC],
  )(implicit
      CK: LDKeyCodec.WithInfallibleEncode[K],
      CV: LDCodecWithInfallibleEncode[V],
  ): LDCodecWithInfallibleEncode[CC] =
    LDCodecWithInfallibleEncode.instanceFull[CC](
      cc => {
        val builder = LDValue.buildObject()
        toIterator(cc).foreach { case (k, v) =>
          builder.put(CK.safeEncode(k), CV.safeEncode(v))
        }
        builder.build()
      },
      decodeObjectShaped(factory),
    )

  def makeObjectShapedInstance[CC, K, V](
      toIterator: CC => Iterator[(K, V)],
      factory: Factory[(K, V), CC],
  )(implicit
      CK: LDKeyCodec[K],
      CV: LDCodec[V],
  ): LDCodec[CC] =
    LDCodec.instance[CC](
      (cc, history) => {
        val builder = LDValue.buildObject()
        val failures = Vector.newBuilder[LDCodecFailure]
        toIterator(cc).foreach { case (k, v) =>
          LDKeyCodec[K].encode(k) match {
            case Validated.Invalid(reasons) =>
              failures.addAll {
                reasons.map { reason =>
                  LDCodecFailure(unableToEncodeKey(reason), history)
                }.iterator
              }
            case Validated.Valid(key) =>
              CV.encode(v, history.at(key)) match {
                case Validated.Invalid(e) => failures.addAll(e.iterator)
                case Validated.Valid(value) => builder.put(key, value)
              }
          }
        }
        NonEmptyChain
          .fromChain(Chain.fromSeq(failures.result()))
          .toInvalid(builder.build())
      },
      decodeObjectShaped(factory),
    )

  implicit def mapWithInfallibleEncodeInstance[
      K: LDKeyCodec.WithInfallibleEncode,
      V: LDCodecWithInfallibleEncode,
  ]: LDCodecWithInfallibleEncode[Map[K, V]] =
    makeObjectShapedWithInfallibleEncodeInstance[Map[K, V], K, V](_.iterator, Map)

  implicit def mapInstance[K: LDKeyCodec, V: LDCodec]: LDCodec[Map[K, V]] =
    makeObjectShapedInstance[Map[K, V], K, V](_.iterator, Map)

  implicit def iterablePairsWithInfallibleEncodeInstance[
      K: LDKeyCodec.WithInfallibleEncode,
      V: LDCodecWithInfallibleEncode,
  ]: LDCodecWithInfallibleEncode[Iterable[(K, V)]] =
    makeObjectShapedWithInfallibleEncodeInstance[Iterable[(K, V)], K, V](_.iterator, Iterable)

  implicit def iterablePairsInstance[K: LDKeyCodec, V: LDCodec]: LDCodec[Iterable[(K, V)]] =
    makeObjectShapedInstance[Iterable[(K, V)], K, V](_.iterator, Iterable)

  implicit def arrayPairsWithInfallibleEncodeInstance[
      K: LDKeyCodec.WithInfallibleEncode,
      V: LDCodecWithInfallibleEncode,
  ](implicit ct: ClassTag[(K, V)]): LDCodecWithInfallibleEncode[Array[(K, V)]] =
    makeObjectShapedWithInfallibleEncodeInstance[Array[(K, V)], K, V](_.iterator, Array)

  implicit def arrayPairsInstance[K: LDKeyCodec, V: LDCodec](implicit
      ct: ClassTag[(K, V)]
  ): LDCodec[Array[(K, V)]] =
    makeObjectShapedInstance[Array[(K, V)], K, V](_.iterator, Array)

  implicit def vectorPairsWithInfallibleEncodeInstance[
      K: LDKeyCodec.WithInfallibleEncode,
      V: LDCodecWithInfallibleEncode,
  ]: LDCodecWithInfallibleEncode[Vector[(K, V)]] =
    makeObjectShapedWithInfallibleEncodeInstance[Vector[(K, V)], K, V](_.iterator, Vector)

  implicit def vectorPairsInstance[K: LDKeyCodec, V: LDCodec]: LDCodec[Vector[(K, V)]] =
    makeObjectShapedInstance[Vector[(K, V)], K, V](_.iterator, Vector)

  implicit def listPairsWithInfallibleEncodeInstance[
      K: LDKeyCodec.WithInfallibleEncode,
      V: LDCodecWithInfallibleEncode,
  ]: LDCodecWithInfallibleEncode[List[(K, V)]] =
    makeObjectShapedWithInfallibleEncodeInstance[List[(K, V)], K, V](_.iterator, List)

  implicit def listPairsInstance[K: LDKeyCodec, V: LDCodec]: LDCodec[List[(K, V)]] =
    makeObjectShapedInstance[List[(K, V)], K, V](_.iterator, List)
}
