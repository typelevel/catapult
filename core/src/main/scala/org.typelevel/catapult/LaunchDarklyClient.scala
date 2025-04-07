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

import cats.effect.std.{Dispatcher, Queue}
import cats.effect.{Async, Resource}
import cats.{~>, Applicative}
import com.launchdarkly.sdk.server.interfaces.{FlagValueChangeEvent, FlagValueChangeListener}
import com.launchdarkly.sdk.server.{LDClient, LDConfig}
import com.launchdarkly.sdk.LDValue
import fs2._

trait LaunchDarklyClient[F[_]] {

  /** @param featureKey the key of the flag to be evaluated
    * @param context the context against which the flag is being evaluated
    * @param defaultValue the value to use if evaluation fails for any reason
    * @tparam Ctx the type representing the context; this can be [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]], [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDUser.html LDUser]], or any type with a [[ContextEncoder]] instance in scope.
    * @return the flag value, suspended in the `F` effect. If evaluation fails for any reason, or the evaluated value is not of type Boolean, returns the default value.
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#boolVariation(java.lang.String,com.launchdarkly.sdk.LDContext,boolean) LDClientInterface#boolVariation]]
    */
  def boolVariation[Ctx: ContextEncoder](
      featureKey: String,
      context: Ctx,
      defaultValue: Boolean,
  ): F[Boolean]

  /** @param featureKey   the key of the flag to be evaluated
    * @param context      the context against which the flag is being evaluated
    * @param defaultValue the value to use if evaluation fails for any reason
    * @tparam Ctx the type representing the context; this can be [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]], [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDUser.html LDUser]], or any type with a [[ContextEncoder]] instance in scope.
    * @return the flag value, suspended in the `F` effect. If evaluation fails for any reason, or the evaluated value is not of type String, returns the default value.
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#stringVariation(java.lang.String,com.launchdarkly.sdk.LDContext,string) LDClientInterface#stringVariation]]
    */
  def stringVariation[Ctx: ContextEncoder](
      featureKey: String,
      context: Ctx,
      defaultValue: String,
  ): F[String]

  /** @param featureKey   the key of the flag to be evaluated
    * @param context      the context against which the flag is being evaluated
    * @param defaultValue the value to use if evaluation fails for any reason
    * @tparam Ctx the type representing the context; this can be [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]], [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDUser.html LDUser]], or any type with a [[ContextEncoder]] instance in scope.
    * @return the flag value, suspended in the `F` effect. If evaluation fails for any reason, or the evaluated value cannot be represented as type Int, returns the default value.
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#intVariation(java.lang.String,com.launchdarkly.sdk.LDContext,int) LDClientInterface#intVariation]]
    */
  def intVariation[Ctx: ContextEncoder](featureKey: String, context: Ctx, defaultValue: Int): F[Int]

  /** @param featureKey   the key of the flag to be evaluated
    * @param context      the context against which the flag is being evaluated
    * @param defaultValue the value to use if evaluation fails for any reason
    * @tparam Ctx the type representing the context; this can be [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]], [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDUser.html LDUser]], or any type with a [[ContextEncoder]] instance in scope.
    * @return the flag value, suspended in the `F` effect. If evaluation fails for any reason, or the evaluated value cannot be represented as type Double, returns the default value.
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#doubleVariation(java.lang.String,com.launchdarkly.sdk.LDContext,double) LDClientInterface#doubleVariation]]
    */
  def doubleVariation[Ctx: ContextEncoder](
      featureKey: String,
      context: Ctx,
      defaultValue: Double,
  ): F[Double]

  /** @param featureKey   the key of the flag to be evaluated
    * @param context      the context against which the flag is being evaluated
    * @param defaultValue the value to use if evaluation fails for any reason
    * @tparam Ctx the type representing the context; this can be [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]], [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDUser.html LDUser]], or any type with a [[ContextEncoder]] instance in scope.
    * @return the flag value, suspended in the `F` effect. If evaluation fails for any reason, returns the default value.
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#jsonValueVariation(java.lang.String,com.launchdarkly.sdk.LDContext,com.launchdarkly.sdk.LDValue) LDClientInterface#jsonValueVariation]]
    */
  def jsonValueVariation[Ctx: ContextEncoder](
      featureKey: String,
      context: Ctx,
      defaultValue: LDValue,
  ): F[LDValue]

  @deprecated("use jsonValueVariation", "0.5.0")
  final def jsonVariation[Ctx: ContextEncoder](
      featureKey: String,
      context: Ctx,
      defaultValue: LDValue,
  ): F[LDValue] = jsonValueVariation(featureKey, context, defaultValue)

  /** @param featureKey   the key of the flag to be evaluated
    * @param context      the context against which the flag is being evaluated
    * @tparam Ctx the type representing the context; this can be [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]], [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDUser.html LDUser]], or any type with a [[ContextEncoder]] instance in scope.
    * @return A `Stream` of [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/FlagValueChangeEvent.html FlagValueChangeEvent]] instances representing changes to the value of the flag in the provided context. Note: if the flag value changes multiple times in quick succession, some intermediate values may be missed; for example, a change from 1` to `2` to `3` may be represented only as a change from `1` to `3`
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/FlagTracker.html FlagTracker]]
    */
  def trackFlagValueChanges[Ctx: ContextEncoder](
      featureKey: String,
      context: Ctx,
  ): Stream[F, FlagValueChangeEvent]

  @deprecated("use trackFlagValueChanges", "0.5.0")
  final def listen[Ctx: ContextEncoder](
      featureKey: String,
      context: Ctx,
  ): Stream[F, FlagValueChangeEvent] = trackFlagValueChanges(featureKey, context)

  /** @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#flush() LDClientInterface#flush]]
    */
  def flush: F[Unit]

  def mapK[G[_]](fk: F ~> G): LaunchDarklyClient[G] = LaunchDarklyClient.mapK[F, G](this)(fk)
}

object LaunchDarklyClient {

  /** @return a Catapult [[LaunchDarklyClient]] wrapped in [[cats.effect.Resource]], created using the given SDK key and config
    */
  def resource[F[_]](sdkKey: String, config: LDConfig)(implicit
      F: Async[F]
  ): Resource[F, LaunchDarklyClient[F]] =
    Resource
      .fromAutoCloseable(F.blocking(new LDClient(sdkKey, config)))
      .map(ldClient => unsafeFromJava(ldClient))

  /** @return a Catapult [[LaunchDarklyClient]] wrapped in [[cats.effect.Resource]], created using the given SDK key and default config
    */
  def resource[F[_]](sdkKey: String)(implicit F: Async[F]): Resource[F, LaunchDarklyClient[F]] =
    Resource
      .fromAutoCloseable(F.blocking(new LDClient(sdkKey)))
      .map(ldClient => unsafeFromJava(ldClient))

  /** @return a Catapult [[LaunchDarklyClient]] created using the provided `LDClient`.
    *         It is the caller's responsibility to close the underlying `LDClient`.
    */
  def unsafeFromJava[F[_]](
      ldClient: LDClient
  )(implicit F: Async[F]): LaunchDarklyClient[F] =
    new LaunchDarklyClient.Default[F] {

      override def unsafeWithJavaClient[A](f: LDClient => A): F[A] =
        F.blocking(f(ldClient))

      override def trackFlagValueChanges[Ctx](
          featureKey: String,
          context: Ctx,
      )(implicit ctxEncoder: ContextEncoder[Ctx]): Stream[F, FlagValueChangeEvent] =
        Stream.eval(F.delay(ldClient.getFlagTracker)).flatMap { tracker =>
          Stream.resource(Dispatcher.sequential[F]).flatMap { dispatcher =>
            Stream.eval(Queue.unbounded[F, FlagValueChangeEvent]).flatMap { q =>
              val listener = new FlagValueChangeListener {
                override def onFlagValueChange(event: FlagValueChangeEvent): Unit =
                  dispatcher.unsafeRunSync(q.offer(event))
              }

              Stream.bracket(
                F.delay(
                  tracker.addFlagValueChangeListener(
                    featureKey,
                    ctxEncoder.encode(context),
                    listener,
                  )
                )
              )(listener => F.delay(tracker.removeFlagChangeListener(listener))) >>
                Stream.fromQueueUnterminated(q)
            }
          }
        }
    }

  private trait Default[F[_]] extends LaunchDarklyClient[F] {
    self =>
    protected def unsafeWithJavaClient[A](f: LDClient => A): F[A]

    override def boolVariation[Ctx](
        featureKey: String,
        context: Ctx,
        default: Boolean,
    )(implicit ctxEncoder: ContextEncoder[Ctx]): F[Boolean] =
      unsafeWithJavaClient(_.boolVariation(featureKey, ctxEncoder.encode(context), default))

    override def stringVariation[Ctx](
        featureKey: String,
        context: Ctx,
        default: String,
    )(implicit ctxEncoder: ContextEncoder[Ctx]): F[String] =
      unsafeWithJavaClient(_.stringVariation(featureKey, ctxEncoder.encode(context), default))

    override def intVariation[Ctx](featureKey: String, context: Ctx, default: Int)(implicit
        ctxEncoder: ContextEncoder[Ctx]
    ): F[Int] =
      unsafeWithJavaClient(_.intVariation(featureKey, ctxEncoder.encode(context), default))

    override def doubleVariation[Ctx](
        featureKey: String,
        context: Ctx,
        default: Double,
    )(implicit ctxEncoder: ContextEncoder[Ctx]): F[Double] =
      unsafeWithJavaClient(_.doubleVariation(featureKey, ctxEncoder.encode(context), default))

    override def jsonValueVariation[Ctx](
        featureKey: String,
        context: Ctx,
        default: LDValue,
    )(implicit ctxEncoder: ContextEncoder[Ctx]): F[LDValue] =
      unsafeWithJavaClient(_.jsonValueVariation(featureKey, ctxEncoder.encode(context), default))

    override def flush: F[Unit] = unsafeWithJavaClient(_.flush())

    // TODO: Remove on next bin changing release, had to keep this for bin-compat.
    override def mapK[G[_]](fk: F ~> G): LaunchDarklyClient[G] = LaunchDarklyClient.mapK(this)(fk)
  }

  def noop[F[_]](implicit F: Applicative[F]): LaunchDarklyClient[F] = new LaunchDarklyClient[F] {
    override def boolVariation[Ctx: ContextEncoder](
        featureKey: String,
        context: Ctx,
        defaultValue: Boolean,
    ): F[Boolean] = F.pure(defaultValue)
    override def doubleVariation[Ctx: ContextEncoder](
        featureKey: String,
        context: Ctx,
        defaultValue: Double,
    ): F[Double] = F.pure(defaultValue)
    override def intVariation[Ctx: ContextEncoder](
        featureKey: String,
        context: Ctx,
        defaultValue: Int,
    ): F[Int] = F.pure(defaultValue)
    override def jsonValueVariation[Ctx: ContextEncoder](
        featureKey: String,
        context: Ctx,
        defaultValue: LDValue,
    ): F[LDValue] = F.pure(defaultValue)
    override def stringVariation[Ctx: ContextEncoder](
        featureKey: String,
        context: Ctx,
        defaultValue: String,
    ): F[String] = F.pure(defaultValue)

    override def trackFlagValueChanges[Ctx: ContextEncoder](
        featureKey: String,
        context: Ctx,
    ): fs2.Stream[F, FlagValueChangeEvent] = fs2.Stream.empty

    override def flush: F[Unit] = Applicative[F].unit
  }

  def mapK[F[_], G[_]](self: LaunchDarklyClient[F])(fk: F ~> G): LaunchDarklyClient[G] =
    new LaunchDarklyClient[G] {
      override def boolVariation[Ctx: ContextEncoder](
          featureKey: String,
          context: Ctx,
          defaultValue: Boolean,
      ): G[Boolean] = fk(self.boolVariation(featureKey, context, defaultValue))

      override def doubleVariation[Ctx: ContextEncoder](
          featureKey: String,
          context: Ctx,
          defaultValue: Double,
      ): G[Double] = fk(self.doubleVariation(featureKey, context, defaultValue))

      override def intVariation[Ctx: ContextEncoder](
          featureKey: String,
          context: Ctx,
          defaultValue: Int,
      ): G[Int] = fk(self.intVariation(featureKey, context, defaultValue))

      override def jsonValueVariation[Ctx: ContextEncoder](
          featureKey: String,
          context: Ctx,
          defaultValue: LDValue,
      ): G[LDValue] = fk(self.jsonValueVariation(featureKey, context, defaultValue))

      override def stringVariation[Ctx: ContextEncoder](
          featureKey: String,
          context: Ctx,
          defaultValue: String,
      ): G[String] = fk(self.stringVariation(featureKey, context, defaultValue))

      override def trackFlagValueChanges[Ctx: ContextEncoder](
          featureKey: String,
          context: Ctx,
      ): fs2.Stream[G, FlagValueChangeEvent] =
        self.trackFlagValueChanges(featureKey, context).translate(fk)

      override def flush: G[Unit] = fk(self.flush)
    }

}
