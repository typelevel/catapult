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
