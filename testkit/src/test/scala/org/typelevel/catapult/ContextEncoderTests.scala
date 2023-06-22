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

import com.launchdarkly.sdk.{ContextKind, LDContext}
import weaver.SimpleIOSuite

case object ContextEncoderTests extends SimpleIOSuite {
  pureTest("contramap allows changing the input type of ContextEncoder") {
    // This is a trivial example - it would be just as simple to write a new ContextEncoder from scatch.
    case class Country(name: String)

    val enc = ContextEncoder[LDContext].contramap[Country](country =>
      LDContext.builder(ContextKind.of("country"), country.name).build()
    )

    expect.all(
      enc.encode(Country("France")) == LDContext.create(ContextKind.of("country"), "France"),
      enc.encode(Country("Germany")) == LDContext.create(ContextKind.of("country"), "Germany"),
    )
  }
}
