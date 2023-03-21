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

package catapult

import cats.effect.std.{Dispatcher, Queue}
import cats.effect.{Async, Resource}
import cats.~>
import com.launchdarkly.sdk.server.interfaces.{FlagValueChangeEvent, FlagValueChangeListener}
import com.launchdarkly.sdk.server.{LDClient, LDConfig}
import com.launchdarkly.sdk.{LDContext, LDUser, LDValue}
import fs2._

trait LaunchDarklyClient[F[_]] {

  def boolVariation(featureKey: String, context: LDContext, defaultValue: Boolean): F[Boolean]

  final def boolVariation(featureKey: String, user: LDUser, defaultValue: Boolean): F[Boolean] =
    boolVariation(featureKey, LDContext.fromUser(user), defaultValue)

  def stringVariation(featureKey: String, context: LDContext, defaultValue: String): F[String]

  final def stringVariation(featureKey: String, user: LDUser, defaultValue: String): F[String] =
    stringVariation(featureKey, LDContext.fromUser(user), defaultValue)
  def intVariation(featureKey: String, context: LDContext, defaultValue: Int): F[Int]

  final def intVariation(featureKey: String, user: LDUser, defaultValue: Int): F[Int] =
    intVariation(featureKey, LDContext.fromUser(user), defaultValue)
  def doubleVariation(featureKey: String, context: LDContext, defaultValue: Double): F[Double]

  final def doubleVariation(featureKey: String, user: LDUser, defaultValue: Double): F[Double] =
    doubleVariation(featureKey, LDContext.fromUser(user), defaultValue)

  def jsonVariation(featureKey: String, context: LDContext, defaultValue: LDValue): F[LDValue]

  final def jsonVariation(featureKey: String, user: LDUser, defaultValue: LDValue): F[LDValue] =
    jsonVariation(featureKey, LDContext.fromUser(user), defaultValue)

  def listen(featureKey: String, context: LDContext): Stream[F, FlagValueChangeEvent]

  final def listen(featureKey: String, user: LDUser): Stream[F, FlagValueChangeEvent] =
    listen(featureKey, LDContext.fromUser(user))

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

          override def listen(
              featureKey: String,
              context: LDContext,
          ): Stream[F, FlagValueChangeEvent] =
            Stream.eval(F.delay(ldClient.getFlagTracker)).flatMap { tracker =>
              Stream.resource(Dispatcher[F]).flatMap { dispatcher =>
                Stream.eval(Queue.unbounded[F, FlagValueChangeEvent]).flatMap { q =>
                  val listener = new FlagValueChangeListener {
                    override def onFlagValueChange(event: FlagValueChangeEvent): Unit =
                      dispatcher.unsafeRunSync(q.offer(event))
                  }

                  Stream.bracket(
                    F.delay(tracker.addFlagValueChangeListener(featureKey, context, listener))
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

    override def boolVariation(
        featureKey: String,
        context: LDContext,
        default: Boolean,
    ): F[Boolean] =
      unsafeWithJavaClient(_.boolVariation(featureKey, context, default))

    override def stringVariation(
        featureKey: String,
        context: LDContext,
        default: String,
    ): F[String] =
      unsafeWithJavaClient(_.stringVariation(featureKey, context, default))

    override def intVariation(featureKey: String, context: LDContext, default: Int): F[Int] =
      unsafeWithJavaClient(_.intVariation(featureKey, context, default))

    override def doubleVariation(
        featureKey: String,
        context: LDContext,
        default: Double,
    ): F[Double] =
      unsafeWithJavaClient(_.doubleVariation(featureKey, context, default))

    override def jsonVariation(
        featureKey: String,
        context: LDContext,
        default: LDValue,
    ): F[LDValue] =
      unsafeWithJavaClient(_.jsonValueVariation(featureKey, context, default))

    override def flush: F[Unit] = unsafeWithJavaClient(_.flush())

    override def mapK[G[_]](fk: F ~> G): LaunchDarklyClient[G] = new LaunchDarklyClient.Default[G] {
      override def unsafeWithJavaClient[A](f: LDClient => A): G[A] = fk(
        self.unsafeWithJavaClient(f)
      )

      override def listen(featureKey: String, context: LDContext): Stream[G, FlagValueChangeEvent] =
        self.listen(featureKey, context).translate(fk)

      override def flush: G[Unit] = fk(self.flush)
    }
  }
}
