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

package com.github.bplommer.launchcatsly

import cats.effect._
import com.launchdarkly.sdk.{LDUser, LDValue}
import io.github.bplommer.launchcatsly.testkit._
import weaver.SimpleIOSuite

object VariationTests extends SimpleIOSuite {
  test("serve value of boolean variations")(
    testClient.use { case (td, client) =>
      def getFooFlag =
        client.stringVariation("foo", new LDUser.Builder("derek").build(), defaultValue = "default")

      def setFooFlag(value: String) = IO(td.update(td.flag("foo").valueForAll(LDValue.of(value))))

      for {
        default <- getFooFlag
        _ <- setFooFlag("newValue")
        newValue <- getFooFlag
      } yield expect(default == "default") && expect(newValue == "newValue")

    }
  )
}
