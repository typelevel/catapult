package io.github.bplommer

import cats.effect.{Async, Resource}
import cats.implicits.toFunctorOps
import com.launchdarkly.sdk.server.LDConfig
import com.launchdarkly.sdk.server.integrations.TestData

package object launchcatsly {
  def testClient[F[_]](implicit F: Async[F]): Resource[F, (TestData, LaunchDarklyClient[F])] = {
    Resource.eval(F.delay(new TestData)).flatMap { td =>
      LaunchDarklyClient.resource("fake-key", new LDConfig.Builder().dataSource(td).build).tupleLeft(td)
    }
  }
}
