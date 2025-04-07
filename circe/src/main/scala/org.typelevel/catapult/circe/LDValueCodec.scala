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
import com.launchdarkly.sdk.json.{JsonSerialization, SerializationException}
import com.launchdarkly.sdk.{LDValue, LDValueType}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

object LDValueCodec {

  /** Decode a `circe` [[Json]] value as a `launchdarkly` [[LDValue]]
    *
    * The primary failure path is due to the encoding of JSON numbers as doubles
    * in the [[LDValue]] type hierarchy
    */
  implicit val catapultLDValueDecoder: Decoder[LDValue] = Decoder.instance { cursor =>
    cursor.focus.fold(LDValue.ofNull().asRight[DecodingFailure]) { circeValue =>
      circeValue.fold(
        jsonNull = LDValue.ofNull().asRight,
        jsonBoolean = LDValue.of(_).asRight,
        jsonNumber = jNumber =>
          // This nasty hack is because LDValue number support is lacking.
          //
          // LDValueNumber encodes everything as Double and that introduces encoding
          // issues like negative/positive/unsigned zero and rounding if we try to do
          // the conversion ourselves with the raw.
          //
          // Here, we're basically hoping they've got their house in order and can
          // parse a valid JSON number.
          Either
            .catchOnly[SerializationException] {
              LDValue.normalize(
                JsonSerialization.deserialize(
                  Json.fromJsonNumber(jNumber).noSpaces,
                  classOf[LDValue],
                )
              )
            }
            .leftMap { (_: SerializationException) =>
              DecodingFailure("JSON value is not supported by LaunchDarkly LDValue", cursor.history)
            },
        jsonString = LDValue.of(_).asRight,
        jsonArray = _.traverse(catapultLDValueDecoder.decodeJson).map { values =>
          val builder = LDValue.buildArray()
          values.foreach(builder.add)
          builder.build()
        },
        jsonObject = _.toVector
          .traverse { case (key, value) =>
            catapultLDValueDecoder.decodeJson(value).tupleLeft(key)
          }
          .map { entries =>
            val builder = LDValue.buildObject()
            entries.foreach { case (key, ldValue) =>
              builder.put(key, ldValue)
            }
            builder.build()
          },
      )
    }
  }

  implicit val catapultLDValueEncoder: Encoder[LDValue] = Encoder.instance { ldValue =>
    // So we don't have to deal with JVM nulls
    val normalized = LDValue.normalize(ldValue)
    normalized.getType match {
      case LDValueType.NULL => Json.Null
      case LDValueType.BOOLEAN =>
        Json.fromBoolean(normalized.booleanValue())
      case LDValueType.NUMBER =>
        // This is a bit of a hack because LDValue number support is lacking.
        //
        // LDValueNumber encodes everything as Double and that introduces encoding
        // issues like negative/positive/unsigned zero and rounding when we try to do
        // the conversion ourselves with the raw.
        //
        // Here, we're basically deferring to circe for the right thing to do with a
        // Double by using the default Encoder[Double] (current behavior is to encode
        // invalid values as `null`).
        normalized.doubleValue().asJson
      case LDValueType.STRING =>
        Json.fromString(normalized.stringValue())
      case LDValueType.ARRAY =>
        normalized.values().iterator()
        val builder = Vector.newBuilder[LDValue]
        normalized.values().forEach { ldValue =>
          builder.addOne(ldValue)
        }
        Json.fromValues(builder.result().map(catapultLDValueEncoder(_)))
      case LDValueType.OBJECT =>
        val builder = Vector.newBuilder[(String, LDValue)]
        normalized.keys().forEach { key =>
          builder.addOne(key -> normalized.get(key))
        }
        Json.fromFields(builder.result().map { case (key, value) =>
          key -> catapultLDValueEncoder(value)
        })
    }
  }
}
