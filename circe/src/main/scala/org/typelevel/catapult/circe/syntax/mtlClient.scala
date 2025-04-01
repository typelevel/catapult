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

package org.typelevel.catapult.circe.syntax

import cats.MonadThrow
import cats.syntax.all.*
import com.launchdarkly.sdk.LDValue
import io.circe.syntax.*
import io.circe.{Decoder, Json}
import org.typelevel.catapult.FeatureKey
import org.typelevel.catapult.circe.LDValueCodec.*
import org.typelevel.catapult.mtl.LaunchDarklyMTLClient

object mtlClient {
  implicit final class CatapultLaunchDarklyMTLClientCirceOps[F[_]](
      private val client: LaunchDarklyMTLClient[F]
  ) extends AnyVal {
    def circeVariation(featureKey: String, defaultValue: Json)(implicit F: MonadThrow[F]): F[Json] =
      defaultValue
        .as[LDValue]
        .liftTo[F]
        .flatMap(client.jsonValueVariation(featureKey, _))
        .map(_.asJson)

    def circeVariation(featureKey: FeatureKey.Aux[LDValue])(implicit F: MonadThrow[F]): F[Json] =
      client.variation(featureKey).map(_.asJson)

    def circeVariationAs[A: Decoder](featureKey: String, defaultValue: Json)(implicit
        F: MonadThrow[F]
    ): F[A] =
      defaultValue
        .as[LDValue]
        .liftTo[F]
        .flatMap(client.jsonValueVariation(featureKey, _))
        .flatMap(_.asJson.as[A].liftTo[F])

    def circeVariationAs[A: Decoder](featureKey: FeatureKey.Aux[LDValue])(implicit
        F: MonadThrow[F]
    ): F[A] =
      client.variation(featureKey).flatMap(_.asJson.as[A].liftTo[F])
  }
}
