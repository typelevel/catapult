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

import com.launchdarkly.sdk.{LDContext, LDUser}

/** A typeclass for converting values of type `Ctx` into [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]]. An instance must be in scope when
  * evaulating flags against a context represented by the `Ctx` type. Instances are provided for [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html LDContext]]
  * and [[https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDUser.html LDUser]]; custom instances can be created to allow other types to be used.
  */
trait ContextEncoder[Ctx] {
  def encode(ctx: Ctx): LDContext
}

object ContextEncoder {
  implicit val catapultContextEncoderForLdContext: ContextEncoder[LDContext] = identity(_)

  implicit val catapultContextEncoderForLdUser: ContextEncoder[LDUser] = LDContext.fromUser(_)
}
