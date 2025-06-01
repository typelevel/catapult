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
import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import org.typelevel.catapult.circe.JsonLDCodec.*
import org.typelevel.catapult.codec.LDCursorHistory
import org.typelevel.catapult.codec.syntax.*
import org.typelevel.catapult.mtl.LaunchDarklyMTLClient

object mtlClient {
  implicit final class CatapultLaunchDarklyMTLClientCirceOps[F[_]](
      private val client: LaunchDarklyMTLClient[F]
  ) extends AnyVal {
    def circeVariation(featureKey: String, defaultValue: Json)(implicit F: MonadThrow[F]): F[Json] =
      defaultValue
        .asLDValueOrFailure(LDCursorHistory.root)
        .asEncodingFailure
        .liftTo[F]
        .flatMap(client.jsonValueVariation(featureKey, _))
        .flatMap(_.decode[Json].asDecodingFailure.liftTo[F])

    def circeVariationAs[A: Decoder: Encoder](featureKey: String, defaultValue: A)(implicit
        F: MonadThrow[F]
    ): F[A] =
      circeVariation(featureKey, defaultValue.asJson)
        .flatMap(_.as[A].liftTo[F])
  }
}
