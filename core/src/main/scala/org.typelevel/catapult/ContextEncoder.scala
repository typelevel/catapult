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

import com.launchdarkly.sdk.{LDContext, LDUser}
import cats.Contravariant

/** A typeclass for converting values of type `Ctx` into [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]]. An instance must be in scope when
  * evaulating flags against a context represented by the `Ctx` type. Instances are provided for [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]]
  * and [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDUser.html LDUser]]; custom instances can be created to allow other types to be used.
  */
trait ContextEncoder[Ctx] {
  def encode(ctx: Ctx): LDContext

  /** Create a new `ContextEncoder` by applying a function to the input before encoding. */
  def contramap[A](f: A => Ctx): ContextEncoder[A] = a => encode(f(a))
}

object ContextEncoder {
  def apply[Ctx](implicit ev: ContextEncoder[Ctx]): ContextEncoder[Ctx] = ev
  def encode[Ctx](ctx: Ctx)(implicit ev: ContextEncoder[Ctx]): LDContext = ev.encode(ctx)

  implicit val catapultContextEncoderContravariant: Contravariant[ContextEncoder] =
    new Contravariant[ContextEncoder] {
      def contramap[A, B](fa: ContextEncoder[A])(f: B => A): ContextEncoder[B] = fa.contramap(f)
    }

  implicit val catapultContextEncoderForLdContext: ContextEncoder[LDContext] = identity(_)

  @deprecated("Use LDContext instead of LDUser", "0.5.0")
  implicit val catapultContextEncoderForLdUser: ContextEncoder[LDUser] = LDContext.fromUser(_)
}
