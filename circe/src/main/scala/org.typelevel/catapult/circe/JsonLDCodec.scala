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

import cats.Defer
import cats.data.{Chain, NonEmptyChain, Validated}
import cats.syntax.all.*
import com.launchdarkly.sdk.json.{JsonSerialization, SerializationException}
import com.launchdarkly.sdk.{LDValue, LDValueType}
import io.circe.*
import org.typelevel.catapult.codec.*
import org.typelevel.catapult.codec.LDCodec.LDCodecResult
import org.typelevel.catapult.codec.LDCursorHistory.Move
import org.typelevel.catapult.codec.syntax.*

object JsonLDCodec {
  implicit val circeLDCodecForJSON: LDCodec[Json] =
    JsonLDCodecImplementation.jsonLDCodecRetainingNumberErrors

  def ldCodecFromCirceCodec[A: Encoder: Decoder]: LDCodec[A] =
    circeLDCodecForJSON.imapVFull[A](
      (a, _) => Encoder[A].apply(a).valid,
      (json, history) =>
        Decoder[A].decodeAccumulating(json.hcursor).leftMap { errors =>
          NonEmptyChain
            .fromNonEmptyList(errors)
            .map(JsonLDCodecImplementation.convertCirceFailureToLDFailure)
            .map(lcf => lcf.updateHistory(history.combine(_)))
        },
    )
}

// This is separate to keep the implicits from colliding during construction
private object JsonLDCodecImplementation {
  val jsonLDCodecRetainingNumberErrors: LDCodec[Json] = Defer[LDCodec].fix { implicit recurse =>
    LDCodec.instance(
      _encode = (json, history) =>
        json.fold[LDCodecResult[LDValue]](
          jsonNull = LDValue.ofNull().validNec,
          jsonBoolean = _.asLDValue.valid,
          jsonNumber = _.asLDValueOrFailure(history),
          jsonString = _.asLDValue.valid,
          jsonArray = _.asLDValueOrFailure(history),
          jsonObject = _.toIterable.asLDValueOrFailure(history),
        ),
      _decode = cursor =>
        cursor.valueType match {
          case LDValueType.NULL => Json.Null.valid
          case LDValueType.BOOLEAN => cursor.as[Boolean].map(Json.fromBoolean)
          case LDValueType.NUMBER => cursor.as[JsonNumber].map(Json.fromJsonNumber)
          case LDValueType.STRING => cursor.as[String].map(Json.fromString)
          case LDValueType.ARRAY => cursor.as[Iterable[Json]].map(Json.fromValues)
          case LDValueType.OBJECT => cursor.as[Vector[(String, Json)]].map(Json.fromFields(_))
        },
    )
  }

  def convertCirceFailureToLDFailure(decodingFailure: DecodingFailure): LDCodecFailure =
    LDCodecFailure(
      decodingFailure.reason match {
        case DecodingFailure.Reason.CustomReason(message) => LDReason.other(message)
        case DecodingFailure.Reason.MissingField => LDReason.missingField
        case DecodingFailure.Reason.WrongTypeExpectation(expectedJsonFieldType, jsonValue) =>
          LDReason.wrongType(
            expectedJsonFieldType,
            jsonValue.fold(
              jsonNull = LDValueType.NULL,
              jsonBoolean = _ => LDValueType.BOOLEAN,
              jsonNumber = _ => LDValueType.NUMBER,
              jsonString = _ => LDValueType.STRING,
              jsonArray = _ => LDValueType.ARRAY,
              jsonObject = _ => LDValueType.OBJECT,
            ),
          )
      },
      LDCursorHistory.of(Chain.fromSeq(decodingFailure.history).mapFilter {
        case CursorOp.DownField(k) => Move(k).some
        case CursorOp.DownN(n) => Move(n).some
        case _ => none
      }),
    )

  implicit private val jNumberCodec: LDCodec[JsonNumber] = LDCodec.instance(
    (jNumber, history) =>
      Validated
        .catchOnly[SerializationException] {
          // This nasty hack is because LDValue number support is lacking.
          //
          // LDValueNumber encodes everything as Double and that introduces encoding
          // issues like negative/positive/unsigned zero and rounding if we try to do
          // the conversion ourselves with the raw.
          //
          // Here, we're basically hoping they've got their house in order and can
          // parse a valid JSON number.
          LDValue.normalize(
            JsonSerialization.deserialize(
              Json.fromJsonNumber(jNumber).noSpaces,
              classOf[LDValue],
            )
          )
        }
        .leftMap { (_: SerializationException) =>
          LDCodecFailure(LDReason.unencodableValue(LDValueType.NUMBER, "JNumber"), history)
        }
        .toValidatedNec,
    _.checkType(LDValueType.NUMBER).andThen { c =>
      // Less of a hack on the encoding side, because circe can handle (most) doubles.
      // The JVM double doesn't map cleanly to the JSON number, so this can fail as well.
      LDCodec[Double]
        .decode(c)
        .map(Json.fromDouble(_).flatMap(_.asNumber))
        .andThen(_.toValidNec {
          c.fail(LDReason.undecodableValue(LDValueType.NUMBER, "JNumber"))
        })
    },
  )
}
