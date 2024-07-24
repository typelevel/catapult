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

import cats.implicits._
import cats.mtl._
import com.launchdarkly.sdk.LDContext
import cats._

package object implicits extends AskInstances

private[mtl] trait AskInstances {
  implicit def askLDContextFromAskCTX[F[_]: Applicative, CTX: ContextEncoder](implicit
      F: Ask[F, CTX]
  ): Ask[F, LDContext] = F.map(ContextEncoder[CTX].encode)
}
