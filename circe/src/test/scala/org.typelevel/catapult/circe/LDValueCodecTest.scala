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

package org.typelevel.catapult.circe

import cats.syntax.all.*
import com.launchdarkly.sdk.LDValue
import io.circe.syntax._
import munit.ScalaCheckSuite
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Prop.*
import org.scalacheck.{Arbitrary, Gen}
import org.typelevel.catapult.circe.LDValueCodec._

class LDValueCodecTest extends ScalaCheckSuite {
  private val maxDepth = 3
  private val maxEntries = 5
  private val maxFields = 5

  private val genLDScalar = Gen.oneOf(
    Gen.const(LDValue.ofNull()),
    arbitrary[Boolean].map(LDValue.of),
    arbitrary[String].map(LDValue.of),
    // Important: all numeric constructors delegate to LDValue.of(Double), so
    // we're only using this one. This means it will not exercise the full range
    // of values supported by circe, but since the values coming in are LDValues,
    // this will need to be good enough.
    arbitrary[Double].map(LDValue.of),
  )

  private def genLDArray(depth: Int): Gen[LDValue] =
    for {
      len <- Gen.chooseNum(0, maxEntries)
      entries <- Gen.listOfN(len, genLDValue(depth + 1))
    } yield {
      val builder = LDValue.buildArray()
      entries.foreach(builder.add)
      builder.build()
    }

  private def genLDObject(depth: Int): Gen[LDValue] =
    for {
      count <- Gen.chooseNum(0, maxFields)
      fields <- Gen.listOfN(
        count,
        Gen.zip(arbitrary[String], genLDValue(depth + 1)),
      )
    } yield {
      val builder = LDValue.buildObject()
      fields.foreach { case (key, value) =>
        builder.put(key, value)
      }
      builder.build()
    }

  private def genLDValue(depth: Int): Gen[LDValue] =
    if (depth < maxDepth) Gen.oneOf(genLDScalar, genLDArray(depth + 1), genLDObject(depth + 1))
    else genLDScalar

  implicit private val arbLDValue: Arbitrary[LDValue] = Arbitrary(genLDValue(0))

  property("LDValueSerde round trip")(forAll { (input: LDValue) =>
    input.asJson
      .as[LDValue]
      .map { output =>
        if (input == output) proved
        else
          falsified :| s"Expected ${input.toJsonString} but was ${output.toJsonString}"
      }
      .valueOr(fail("Conversion failed", _))
  })
}
