/*
 * Copyright 2022 Ben Plommer
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
import cats.~>
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
  def jsonVariation[Ctx: ContextEncoder](
      featureKey: String,
      context: Ctx,
      defaultValue: LDValue,
  ): F[LDValue]

  /** @param featureKey   the key of the flag to be evaluated
    * @param context      the context against which the flag is being evaluated
    * @tparam Ctx the type representing the context; this can be [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]], [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDUser.html LDUser]], or any type with a [[ContextEncoder]] instance in scope.
    * @return A `Stream` of [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/FlagValueChangeEvent.html FlagValueChangeEvent]] instances representing changes to the value of the flag in the provided context. Note: if the flag value changes multiple times in quick succession, some intermediate values may be missed; for example, a change from 1` to `2` to `3` may be represented only as a change from `1` to `3`
    * @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/FlagTracker.html FlagTracker]]
    */
  def listen[Ctx: ContextEncoder](featureKey: String, context: Ctx): Stream[F, FlagValueChangeEvent]

  /** @see [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/server/interfaces/LDClientInterface.html#flush() LDClientInterface#flush]]
    */
  def flush: F[Unit]

  def mapK[G[_]](fk: F ~> G): LaunchDarklyClient[G]
}

object LaunchDarklyClient {
  def resource[F[_]](sdkKey: String, config: LDConfig)(implicit
      F: Async[F]
  ): Resource[F, LaunchDarklyClient[F]] =
    Resource
      .fromAutoCloseable(F.blocking(new LDClient(sdkKey, config)))
      .map { ldClient =>
        new LaunchDarklyClient.Default[F] {

          override def unsafeWithJavaClient[A](f: LDClient => A): F[A] =
            F.blocking(f(ldClient))

          override def listen[Ctx](
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
      }

  trait Default[F[_]] extends LaunchDarklyClient[F] {
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

    override def jsonVariation[Ctx](
        featureKey: String,
        context: Ctx,
        default: LDValue,
    )(implicit ctxEncoder: ContextEncoder[Ctx]): F[LDValue] =
      unsafeWithJavaClient(_.jsonValueVariation(featureKey, ctxEncoder.encode(context), default))

    override def flush: F[Unit] = unsafeWithJavaClient(_.flush())

    override def mapK[G[_]](fk: F ~> G): LaunchDarklyClient[G] = new LaunchDarklyClient.Default[G] {
      override def unsafeWithJavaClient[A](f: LDClient => A): G[A] = fk(
        self.unsafeWithJavaClient(f)
      )

      override def listen[Ctx](featureKey: String, context: Ctx)(implicit
          ctxEncoder: ContextEncoder[Ctx]
      ): Stream[G, FlagValueChangeEvent] =
        self.listen(featureKey, context).translate(fk)

      override def flush: G[Unit] = fk(self.flush)
    }
  }
}
