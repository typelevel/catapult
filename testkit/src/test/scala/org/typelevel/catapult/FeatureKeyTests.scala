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

import cats.effect.IO
import cats.syntax.all.*
import com.launchdarkly.sdk.{LDContext, LDValue}
import org.typelevel.catapult.codec.LDCodec
import org.typelevel.catapult.testkit.*
import weaver.SimpleIOSuite

object FeatureKeyTests extends SimpleIOSuite {
  private val ctx = LDContext.create("testContext")

  test("serve boolean variations through FeatureFlag") {
    testClient.use { case (td, client) =>
      for {
        fk <- IO(FeatureKey.boolOrDie("test", true))
        default <- client.variation(fk, ctx)
        _ <- IO(td.update(td.flag(fk.key).valueForAll(LDValue.of(false))))
        notDefault <- client.variation(fk, ctx)
      } yield expect(default === true) && expect(notDefault === false)
    }
  }

  test("serve string variations through FeatureFlag") {
    testClient.use { case (td, client) =>
      for {
        fk <- IO(FeatureKey.stringOrDie("test", "default"))
        default <- client.variation(fk, ctx)
        _ <- IO(td.update(td.flag(fk.key).valueForAll(LDValue.of("not-default"))))
        notDefault <- client.variation(fk, ctx)
      } yield expect(default === "default") && expect(notDefault === "not-default")
    }
  }

  test("serve int variations through FeatureFlag") {
    testClient.use { case (td, client) =>
      for {
        fk <- IO(FeatureKey.intOrDie("test", 10))
        default <- client.variation(fk, ctx)
        _ <- IO(td.update(td.flag(fk.key).valueForAll(LDValue.of(-10))))
        notDefault <- client.variation(fk, ctx)
      } yield expect(default === 10) && expect(notDefault === -10)
    }
  }

  test("serve double variations through FeatureFlag") {
    testClient.use { case (td, client) =>
      for {
        fk <- IO(FeatureKey.doubleOrDie("test", 2d))
        default <- client.variation(fk, ctx)
        _ <- IO(td.update(td.flag(fk.key).valueForAll(LDValue.of(-2d))))
        notDefault <- client.variation(fk, ctx)
      } yield expect(default === 2d) && expect(notDefault === -2d)
    }
  }

  test("serve ldValue variations through FeatureFlag") {
    testClient.use { case (td, client) =>
      for {
        fk <- IO(FeatureKey.ldValue("test", LDValue.of(5)))
        default <- client.variation(fk, ctx)
        _ <- IO(td.update(td.flag(fk.key).valueForAll(LDValue.of(true))))
        notDefault <- client.variation(fk, ctx)
      } yield expect(default == LDValue.of(5)) && expect(notDefault == LDValue.of(true))
    }
  }

  test("serve array variations through FeatureFlag") {
    testClient.use { case (td, client) =>
      val emptyArray = Vector.empty[Int]
      val arrayOfOne = Vector(1)
      def arrayOfMany = Vector(1, 10)
      for {
        fk <- IO(FeatureKey.instanceOrDie("test", emptyArray))
        default <- client.variation(fk, ctx)
        _ <- IO(
          td.update(
            td.flag(fk.key)
              .valueForAll(
                LDValue.arrayOf(
                  LDValue.of(1)
                )
              )
          )
        )
        notDefaultOne <- client.variation(fk, ctx)
        _ <- IO(
          td.update(
            td.flag(fk.key)
              .valueForAll(
                LDValue.arrayOf(
                  LDValue.of(1),
                  LDValue.of(10),
                )
              )
          )
        )
        notDefaultMany <- client.variation(fk, ctx)
      } yield expect(default == emptyArray) &&
        expect(notDefaultOne == arrayOfOne) &&
        expect(notDefaultMany == arrayOfMany)
    }
  }

  test("serve map variations through FeatureFlag") {
    testClient.use { case (td, client) =>
      val emptyMap = Map.empty[String, Boolean]
      val mapOfOne = Map("foo" -> true)
      def mapOfMany = Map(
        "foo" -> true,
        "bar" -> false,
      )
      for {
        fk <- IO(FeatureKey.instanceOrDie("test", emptyMap))
        default <- client.variation(fk, ctx)
        _ <- IO(td.update(td.flag(fk.key).valueForAll {
          LDValue
            .buildObject()
            .put("foo", true)
            .build()
        }))
        notDefaultOne <- client.variation(fk, ctx)
        _ <- IO(td.update(td.flag(fk.key).valueForAll {
          LDValue
            .buildObject()
            .put("foo", true)
            .put("bar", false)
            .build()
        }))
        notDefaultMany <- client.variation(fk, ctx)
      } yield expect(default == emptyMap) &&
        expect(notDefaultOne == mapOfOne) &&
        expect(notDefaultMany == mapOfMany)
    }
  }

  test("serve case class variation through FeatureFlag") {
    final case class Foo(a: String, b: Boolean)

    testClient.use { case (td, client) =>
      implicit val codec: LDCodec[Foo] = LDCodec.objInstance[Foo](
        (foo, _) => _.put("a", foo.a).put("b", foo.b).valid,
        obj =>
          (
            obj.at("a").as[String],
            obj.at("b").as[Boolean],
          ).mapN(Foo(_, _)),
      )
      val defaultFoo = Foo(a = "hi", b = false)
      for {
        fk <- IO(FeatureKey.instanceOrDie("test", defaultFoo))
        default <- client.variation(fk, ctx)
        _ <- IO(td.update(td.flag(fk.key).valueForAll {
          LDValue
            .buildObject()
            .put("a", "hola")
            .put("b", true)
            .build()
        }))
        notDefault <- client.variation(fk, ctx)
        _ <- IO(td.update(td.flag(fk.key).valueForAll {
          LDValue.buildObject().build()
        }))
        invalidValue <- client.variation(fk, ctx)
      } yield expect(default == defaultFoo) &&
        expect(notDefault == Foo(a = "hola", b = true)) &&
        expect(invalidValue == defaultFoo)
    }
  }
}
