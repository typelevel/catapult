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

import cats.Show
import cats.syntax.all.*
import com.launchdarkly.sdk.LDValue

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

  /** Define a feature key that is expected to return a value of type `A`
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the value cannot be decoded to an `A`
    */
  def instance[A: Show: LDCodec](key: String, default: A): FeatureKey.Aux[A] =
    new Impl[A](key, default)

  /** Define a feature key that is expected to return a boolean value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def bool(key: String, default: Boolean): FeatureKey.Aux[Boolean] = instance[Boolean](key, default)

  /** Define a feature key that is expected to return a string value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def string(key: String, default: String): FeatureKey.Aux[String] = instance[String](key, default)

  /** Define a feature key that is expected to return a integer value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def int(key: String, default: Int): FeatureKey.Aux[Int] = instance[Int](key, default)

  /** Define a feature key that is expected to return a double value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def double(key: String, default: Double): FeatureKey.Aux[Double] = instance[Double](key, default)

  /** Define a feature key that is expected to return a JSON value.
    *
    * This uses the LaunchDarkly `LDValue` encoding for JSON
    *
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def ldValue(key: String, default: LDValue): FeatureKey.Aux[LDValue] = {
    implicit def ldValueShow: Show[LDValue] = FeatureKey.ldValueShow
    new Impl[LDValue](key, default)
  }

  private val ldValueShow: Show[LDValue] = Show.show(_.toJsonString)
  private final case class Impl[A: Show: LDCodec](_key: String, _default: A) extends FeatureKey {
    override type Type = A
    override val key: String = _key
    override val codec: LDCodec[A] = LDCodec[A]
    override val ldValueDefault: LDValue = codec.encode(_default)
    override def default: A = _default

    override def toString: String = s"FeatureKey($key, ${_default.show})"
  }
}
