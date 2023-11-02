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

import cats.data.{Chain, NonEmptyChain}
import cats.effect._
import cats.effect.std.Supervisor
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent
import com.launchdarkly.sdk.{LDUser, LDValue}
import org.typelevel.catapult.testkit._
import weaver.SimpleIOSuite

import scala.concurrent.duration.DurationInt

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

  test("listen to change events")(
    testClient.use { case (td, client) =>
      def setFooFlag(value: String) = IO(td.update(td.flag("foo").valueForAll(LDValue.of(value))))

      Supervisor[IO].use { sup =>
        for {
          received <- IO.ref[Chain[FlagValueChangeEvent]](Chain.empty)
          _ <- sup.supervise(
            client
              .trackFlagValueChanges("foo", new LDUser.Builder("derek").build())
              .evalTap(event => received.update(_.append(event)))
              .compile
              .drain
          )
          _ <- IO.sleep(500.millis)
          _ <- setFooFlag("value1")
          _ <- setFooFlag("value2")
          _ <- client.flush
          _ <- IO.sleep(1000.millis)
          result <- received.get
          unchained = NonEmptyChain
            .fromChain(
              result.map(event =>
                (event.getOldValue.stringValue(), event.getNewValue.stringValue())
              )
            )
            .map(_.reduceLeft { case ((old1, new1), (old2, new2)) =>
              if (old2 == new1) (old1, new2) else throw new Exception("")
            })

        } yield expect(unchained == Some((null, "value2")))
      }
    }
  )
}
