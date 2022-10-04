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

package io.github.bplommer.launchcatsly

import cats.effect.{Resource, Sync}
import cats.~>
import com.launchdarkly.sdk.server.{LDClient, LDConfig}
import com.launchdarkly.sdk.{LDUser, LDValue}

trait LaunchDarklyClient[F[_]] { self =>

  def boolVariation(featureKey: String, user: LDUser, defaultValue: Boolean): F[Boolean]

  def stringVariation(featureKey: String, user: LDUser, defaultValue: String): F[String]

  def intVariation(featureKey: String, user: LDUser, defaultValue: Int): F[Int]

  def doubleVariation(featureKey: String, user: LDUser, defaultValue: Double): F[Double]

  def jsonVariation(featureKey: String, user: LDUser, defaultValue: LDValue): F[LDValue]

  def mapK[G[_]](fk: F ~> G): LaunchDarklyClient[G]
}

object LaunchDarklyClient {
  def resource[F[_]](sdkKey: String, config: LDConfig)(implicit
      F: Sync[F]
  ): Resource[F, LaunchDarklyClient[F]] =
    Resource.fromAutoCloseable(F.blocking(new LDClient(sdkKey, config)))
      .map { ldClient =>
        new LaunchDarklyClient.Default[F] {

          override def unsafeWithJavaClient[A](f: LDClient => A): F[A] =
            F.blocking(f(ldClient))

        }
      }

  trait Default[F[_]] extends LaunchDarklyClient[F] {
    self =>
    protected def unsafeWithJavaClient[A](f: LDClient => A): F[A]

    override def boolVariation(featureKey: String, user: LDUser, default: Boolean): F[Boolean] =
      unsafeWithJavaClient(_.boolVariation(featureKey, user, default))

    override def stringVariation(featureKey: String, user: LDUser, default: String): F[String] =
      unsafeWithJavaClient(_.stringVariation(featureKey, user, default))

    override def intVariation(featureKey: String, user: LDUser, default: Int): F[Int] =
      unsafeWithJavaClient(_.intVariation(featureKey, user, default))

    override def doubleVariation(featureKey: String, user: LDUser, default: Double): F[Double] =
      unsafeWithJavaClient(_.doubleVariation(featureKey, user, default))

    override def jsonVariation(featureKey: String, user: LDUser, default: LDValue): F[LDValue] =
      unsafeWithJavaClient(_.jsonValueVariation(featureKey, user, default))

    override def mapK[G[_]](fk: F ~> G): LaunchDarklyClient[G] = new LaunchDarklyClient.Default[G] {
      override def unsafeWithJavaClient[A](f: LDClient => A): G[A] = fk(
        self.unsafeWithJavaClient(f)
      )
    }
  }
}
