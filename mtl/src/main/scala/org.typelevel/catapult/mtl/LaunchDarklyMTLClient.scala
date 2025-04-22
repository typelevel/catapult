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
package mtl

import cats.effect.{Async, Resource}
import cats.~>
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent
import com.launchdarkly.sdk.server.LDConfig
import com.launchdarkly.sdk.LDContext
import com.launchdarkly.sdk.LDValue
import fs2.*
import cats.*
import cats.implicits.*
import cats.mtl.*

trait LaunchDarklyMTLClient[F[_]] {

  /** @param featureKey the key of the flag to be evaluated
    * @param defaultValue the value to use if evaluation fails for any reason
    * @return the flag value, suspended in the `F` effect. If evaluation fails for any reason, or the evaluated value is not of type Boolean, returns the default value.
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#boolVariation(java.lang.String,com.launchdarkly.sdk.LDContext,boolean) LDClientInterface#boolVariation]]
    */
  def boolVariation(
      featureKey: String,
      defaultValue: Boolean,
  ): F[Boolean]

  /** @param featureKey   the key of the flag to be evaluated
    * @param defaultValue the value to use if evaluation fails for any reason
    * @return the flag value, suspended in the `F` effect. If evaluation fails for any reason, or the evaluated value is not of type String, returns the default value.
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#stringVariation(java.lang.String,com.launchdarkly.sdk.LDContext,string) LDClientInterface#stringVariation]]
    */
  def stringVariation(
      featureKey: String,
      defaultValue: String,
  ): F[String]

  /** @param featureKey   the key of the flag to be evaluated
    * @param defaultValue the value to use if evaluation fails for any reason
    * @return the flag value, suspended in the `F` effect. If evaluation fails for any reason, or the evaluated value cannot be represented as type Int, returns the default value.
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#intVariation(java.lang.String,com.launchdarkly.sdk.LDContext,int) LDClientInterface#intVariation]]
    */
  def intVariation(featureKey: String, defaultValue: Int): F[Int]

  /** @param featureKey   the key of the flag to be evaluated
    * @param defaultValue the value to use if evaluation fails for any reason
    * @return the flag value, suspended in the `F` effect. If evaluation fails for any reason, or the evaluated value cannot be represented as type Double, returns the default value.
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#doubleVariation(java.lang.String,com.launchdarkly.sdk.LDContext,double) LDClientInterface#doubleVariation]]
    */
  def doubleVariation(
      featureKey: String,
      defaultValue: Double,
  ): F[Double]

  /** @param featureKey   the key of the flag to be evaluated
    * @param defaultValue the value to use if evaluation fails for any reason
    * @return the flag value, suspended in the `F` effect. If evaluation fails for any reason, returns the default value.
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#jsonValueVariation(java.lang.String,com.launchdarkly.sdk.LDContext,com.launchdarkly.sdk.LDValue) LDClientInterface#jsonValueVariation]]
    */
  def jsonValueVariation(
      featureKey: String,
      defaultValue: LDValue,
  ): F[LDValue]

  /** Retrieve the flag value, suspended in the `F` effect
    *
    * @param featureKey
    *   Defines the key, type, and default value
    * @see
    *   [[FeatureKey]]
    * @see
    *   [[boolVariation]]
    * @see
    *   [[stringVariation]]
    * @see
    *   [[doubleVariation]]
    * @see
    *   [[jsonValueVariation]]
    */
  def variation(featureKey: FeatureKey): F[featureKey.Type]

  /** @param featureKey   the key of the flag to be evaluated
    * @return A `Stream` of [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/FlagValueChangeEvent.html FlagValueChangeEvent]] instances representing changes to the value of the flag in the provided context. Note: if the flag value changes multiple times in quick succession, some intermediate values may be missed; for example, a change from 1` to `2` to `3` may be represented only as a change from `1` to `3`
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/FlagTracker.html FlagTracker]]
    */
  def trackFlagValueChanges(
      featureKey: String
  ): Stream[F, FlagValueChangeEvent]

  /** @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#flush() LDClientInterface#flush]]
    */
  def flush: F[Unit]

  def mapK[G[_]](fk: F ~> G): LaunchDarklyMTLClient[G] = LaunchDarklyMTLClient.mapK(this)(fk)
}

object LaunchDarklyMTLClient {

  /** @return a Catapult [[LaunchDarklyMTLClient]] wrapped in [[cats.effect.Resource]], created using the given SDK key and config
    */
  def resource[F[_]: Async](sdkKey: String, config: LDConfig)(implicit
      contextAsk: Ask[F, LDContext]
  ): Resource[F, LaunchDarklyMTLClient[F]] =
    LaunchDarklyClient.resource[F](sdkKey, config).map(fromLaunchDarklyClient[F](_))

  /** @return a Catapult [[LaunchDarklyMTLClient]] wrapped in [[cats.effect.Resource]], created using the given SDK key and default config
    */
  def resource[F[_]: Async](sdkKey: String)(implicit
      contextAsk: Ask[F, LDContext]
  ): Resource[F, LaunchDarklyMTLClient[F]] =
    LaunchDarklyClient.resource[F](sdkKey).map(fromLaunchDarklyClient[F](_))

  /** @return a Catapult [[LaunchDarklyMTLClient]] from an [[LaunchDarklyClient]].
    */
  def fromLaunchDarklyClient[F[_]](
      launchDarklyClient: LaunchDarklyClient[F]
  )(implicit F: Monad[F], contextAsk: Ask[F, LDContext]): LaunchDarklyMTLClient[F] =
    new LaunchDarklyMTLClient[F] {
      override def flush: F[Unit] = launchDarklyClient.flush

      override def boolVariation(featureKey: String, defaultValue: Boolean): F[Boolean] =
        contextAsk.ask.flatMap(launchDarklyClient.boolVariation(featureKey, _, defaultValue))

      override def intVariation(featureKey: String, defaultValue: Int): F[Int] =
        contextAsk.ask.flatMap(launchDarklyClient.intVariation(featureKey, _, defaultValue))

      override def doubleVariation(featureKey: String, defaultValue: Double): F[Double] =
        contextAsk.ask.flatMap(launchDarklyClient.doubleVariation(featureKey, _, defaultValue))

      override def jsonValueVariation(
          featureKey: String,
          defaultValue: com.launchdarkly.sdk.LDValue,
      ): F[com.launchdarkly.sdk.LDValue] =
        contextAsk.ask.flatMap(launchDarklyClient.jsonValueVariation(featureKey, _, defaultValue))

      override def stringVariation(featureKey: String, defaultValue: String): F[String] =
        contextAsk.ask.flatMap(launchDarklyClient.stringVariation(featureKey, _, defaultValue))

      override def variation(featureKey: FeatureKey): F[featureKey.Type] =
        contextAsk.ask.flatMap(launchDarklyClient.variation(featureKey, _))

      override def trackFlagValueChanges(
          featureKey: String
      ): fs2.Stream[F, com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent] =
        Stream.eval(contextAsk.ask).flatMap(launchDarklyClient.trackFlagValueChanges(featureKey, _))
    }

  def noop[F[_]](implicit F: Applicative[F]): LaunchDarklyMTLClient[F] =
    new LaunchDarklyMTLClient[F] {
      override def boolVariation(featureKey: String, defaultValue: Boolean): F[Boolean] =
        F.pure(defaultValue)

      override def stringVariation(featureKey: String, defaultValue: String): F[String] =
        F.pure(defaultValue)

      override def intVariation(featureKey: String, defaultValue: Int): F[Int] =
        F.pure(defaultValue)

      override def doubleVariation(featureKey: String, defaultValue: Double): F[Double] =
        F.pure(defaultValue)

      override def jsonValueVariation(featureKey: String, defaultValue: LDValue): F[LDValue] =
        F.pure(defaultValue)

      override def trackFlagValueChanges(featureKey: String): Stream[F, FlagValueChangeEvent] =
        Stream.empty

      override def variation(featureKey: FeatureKey): F[featureKey.Type] =
        F.pure(featureKey.default)

      override val flush: F[Unit] = F.unit
    }

  def mapK[F[_], G[_]](ldc: LaunchDarklyMTLClient[F])(fk: F ~> G): LaunchDarklyMTLClient[G] =
    new LaunchDarklyMTLClient[G] {
      override def boolVariation(featureKey: String, defaultValue: Boolean): G[Boolean] =
        fk(ldc.boolVariation(featureKey, defaultValue))

      override def stringVariation(featureKey: String, defaultValue: String): G[String] =
        fk(ldc.stringVariation(featureKey, defaultValue))

      override def intVariation(featureKey: String, defaultValue: Int): G[Int] =
        fk(ldc.intVariation(featureKey, defaultValue))

      override def doubleVariation(featureKey: String, defaultValue: Double): G[Double] =
        fk(ldc.doubleVariation(featureKey, defaultValue))

      override def jsonValueVariation(featureKey: String, defaultValue: LDValue): G[LDValue] =
        fk(ldc.jsonValueVariation(featureKey, defaultValue))

      override def variation(featureKey: FeatureKey): G[featureKey.Type] =
        fk(ldc.variation(featureKey))

      override def trackFlagValueChanges(featureKey: String): Stream[G, FlagValueChangeEvent] =
        ldc.trackFlagValueChanges(featureKey).translate(fk)

      override val flush: G[Unit] = fk(ldc.flush)
    }
}
