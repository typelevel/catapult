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

import cats.syntax.all.*
import cats.Show
import cats.data.NonEmptyChain
import cats.kernel.Hash
import com.launchdarkly.sdk.LDValue
import org.typelevel.catapult.codec.LDCodec.LDCodecResult
import org.typelevel.catapult.codec.{LDCodec, LDCodecFailure, LDCursorHistory}
import org.typelevel.catapult.instances.*

/** Defines a Launch Darkly key, it's expected type, and a default value
  */
trait FeatureKey {
  type Type
  def key: String
  def default: Type
  def ldValueDefault: LDValue
  def codec: LDCodec[Type]
}
object FeatureKey {
  type Aux[T] = FeatureKey {
    type Type = T
  }

  final case class InvalidDefault[A](
      key: String,
      default: A,
      encodingErrors: NonEmptyChain[LDCodecFailure],
  ) extends IllegalArgumentException(
        s"FeatureKey $key defined with default that cannot be encoded in an LDValue"
      )

  /** Define a feature key that is expected to return a value of type `A`
    *
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the value cannot be decoded to an `A`
    * @return
    *  `LDCodecResult[FeatureKey.Aux[A]]` instead of `FeatureKey.Aux[A]` because many unremarkable
    *  values cannot be encoded in an `LDValue`
    */
  def instance[A: LDCodec](key: String, default: A): LDCodecResult[FeatureKey.Aux[A]] =
    LDCodec[A].encode(default, LDCursorHistory.root).map(new Impl[A](key, default, _))

  /** Define a feature key that is expected to return a value of type `A`
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the value cannot be decoded to an `A`
    * @throws InvalidDefault
    *   when `default` cannot be encoded in an `LDValue`
    */
  def instanceOrDie[A: LDCodec](key: String, default: A): FeatureKey.Aux[A] =
    instance(key, default).valueOr(errors => throw InvalidDefault(key, default, errors))

  /** Define a feature key that is expected to return a boolean value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def bool(key: String, default: Boolean): LDCodecResult[FeatureKey.Aux[Boolean]] =
    instance[Boolean](key, default)

  /** @see [[FeatureKey.bool]]
    * @see [[FeatureKey.instanceOrDie]]
    */
  def boolOrDie(key: String, default: Boolean): FeatureKey.Aux[Boolean] =
    instanceOrDie[Boolean](key, default)

  /** Define a feature key that is expected to return a string value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def string(key: String, default: String): LDCodecResult[FeatureKey.Aux[String]] =
    instance[String](key, default)

  /** @see [[FeatureKey.string]]
    * @see [[FeatureKey.instanceOrDie]]
    */
  def stringOrDie(key: String, default: String): FeatureKey.Aux[String] =
    instanceOrDie[String](key, default)

  /** Define a feature key that is expected to return a integer value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def int(key: String, default: Int): LDCodecResult[FeatureKey.Aux[Int]] =
    instance[Int](key, default)

  /** @see [[FeatureKey.int]]
    * @see [[FeatureKey.instanceOrDie]]
    */
  def intOrDie(key: String, default: Int): FeatureKey.Aux[Int] =
    instanceOrDie[Int](key, default)

  /** Define a feature key that is expected to return a double value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def double(key: String, default: Double): LDCodecResult[FeatureKey.Aux[Double]] =
    instance[Double](key, default)

  /** @see [[FeatureKey.double]]
    * @see [[FeatureKey.instanceOrDie]]
    */
  def doubleOrDie(key: String, default: Double): FeatureKey.Aux[Double] =
    instanceOrDie[Double](key, default)

  /** Define a feature key that is expected to return a JSON value.
    *
    * This uses the LaunchDarkly `LDValue` encoding for JSON
    *
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def ldValue(key: String, default: LDValue): FeatureKey.Aux[LDValue] =
    new Impl[LDValue](key, default, default)

  implicit val show: Show[FeatureKey] = Show.fromToString
  implicit val hash: Hash[FeatureKey] = Hash.by(fk => (fk.key, fk.ldValueDefault))

  private final class Impl[A: LDCodec](_key: String, _default: A, _ldValueDefault: LDValue)
      extends FeatureKey {
    override type Type = A
    override val key: String = _key
    override val codec: LDCodec[A] = LDCodec[A]
    override val ldValueDefault: LDValue = _ldValueDefault
    override def default: A = _default

    override def toString: String = s"FeatureKey($key, ${ldValueDefault.show})"

    override def hashCode(): Int = FeatureKey.hash.hash(this)

    override def equals(obj: Any): Boolean = obj match {
      case that: FeatureKey => FeatureKey.hash.eqv(this, that)
      case _ => false
    }
  }
}
