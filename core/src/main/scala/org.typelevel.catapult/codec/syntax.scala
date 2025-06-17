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

package org.typelevel.catapult.codec

import cats.data.Validated
import com.launchdarkly.sdk.LDValue
import org.typelevel.catapult.codec.LDCodec.LDCodecResult

object syntax {
  implicit final class LDCursorEncodeOps[A](private val a: A) extends AnyVal {
    def asLDValue(implicit CA: LDCodecWithInfallibleEncode[A]): LDValue = CA.safeEncode(a)
    def asLDValueOrFailure(history: LDCursorHistory)(implicit
        CA: LDCodec[A]
    ): LDCodecResult[LDValue] = CA.encode(a, history)
  }

  implicit final class LDValueDecodeOps(private val ldValue: LDValue) extends AnyVal {
    def decode[A: LDCodec]: LDCodecResult[A] = LDCodec[A].decode(ldValue)
  }

  implicit final class LDCodecResultOps[A](private val result: LDCodecResult[A]) extends AnyVal {
    def asDecodingFailure: Validated[LDCodec.DecodingFailure, A] =
      result.leftMap(new LDCodec.DecodingFailure(_))

    def asEncodingFailure: Validated[LDCodec.EncodingFailure, A] =
      result.leftMap(new LDCodec.EncodingFailure(_))
  }
}
