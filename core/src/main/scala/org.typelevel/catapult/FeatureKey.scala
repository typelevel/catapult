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
sealed trait FeatureKey {
  type Type
  def key: String
  def default: Type

  def variation[F[_], Ctx: ContextEncoder](client: LaunchDarklyClient[F], ctx: Ctx): F[Type]
}
object FeatureKey {
  type Aux[T] = FeatureKey {
    type Type = T
  }

  /** Define a feature key that is expected to return a boolean value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def bool(key: String, default: Boolean): FeatureKey.Aux[Boolean] =
    new Impl[Boolean](key, default, "Boolean") {
      override def variation[F[_], Ctx: ContextEncoder](
          client: LaunchDarklyClient[F],
          ctx: Ctx,
      ): F[Boolean] =
        client.boolVariation(key, ctx, default)
    }

  /** Define a feature key that is expected to return a string value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def string(key: String, default: String): FeatureKey.Aux[String] =
    new Impl[String](key, default, "String") {
      override def variation[F[_], Ctx: ContextEncoder](
          client: LaunchDarklyClient[F],
          ctx: Ctx,
      ): F[String] =
        client.stringVariation(key, ctx, default)
    }

  /** Define a feature key that is expected to return a integer value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def int(key: String, default: Int): FeatureKey.Aux[Int] =
    new Impl[Int](key, default, "Int") {
      override def variation[F[_], Ctx: ContextEncoder](
          client: LaunchDarklyClient[F],
          ctx: Ctx,
      ): F[Int] =
        client.intVariation(key, ctx, default)
    }

  /** Define a feature key that is expected to return a double value.
    * @param key
    *   the key of the flag
    * @param default
    *   a value to return if the retrieval fails or the type is not expected
    */
  def double(key: String, default: Double): FeatureKey.Aux[Double] =
    new Impl[Double](key, default, "Double") {
      override def variation[F[_], Ctx: ContextEncoder](
          client: LaunchDarklyClient[F],
          ctx: Ctx,
      ): F[Double] =
        client.doubleVariation(key, ctx, default)
    }

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
    new Impl[LDValue](key, default, "LDValue")(ldValueShow) {
      override def variation[F[_], Ctx: ContextEncoder](
          client: LaunchDarklyClient[F],
          ctx: Ctx,
      ): F[LDValue] =
        client.jsonValueVariation(key, ctx, default)
    }

  private val ldValueShow: Show[LDValue] = Show.show(_.toJsonString)
  private abstract class Impl[A: Show](_key: String, _default: A, typeName: String)
      extends FeatureKey {
    override type Type = A
    override val key: String = _key
    override val default: A = _default

    override def toString: String = s"FeatureKey($typeName, $key, ${default.show})"

    override def equals(obj: Any): Boolean = obj match {
      case other: FeatureKey => key == other.key && default == other.default
      case _ => false
    }

    override def hashCode(): Int = ("FeatureKey", key.##, default.##).##
  }
}
