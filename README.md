# Catapult

A thin wrapper for the [Launch Darkly Java server SDK](https://github.com/launchdarkly/java-server-sdk) using cats-effect.

```sbt
"org.typelevel" %% "catapult" % "latestVersion"
```

## Usage

### Basic variations

```scala
  object Main extends IOApp.Simple {
    val sdkKey = "my-sdk-key"
    val config: LDConfig = new LDConfig.Builder().build()
    val launchDarklyClientResource: Resource[IO, LaunchDarklyClient[IO]] = LaunchDarklyClient.resource[IO](sdkKey, config)

    override def run: IO[Unit] = launchDarklyClientResource.use { client =>
      for {
        // Boolean Variant
        bool <- client.boolVariation[LDUser]("BOOL-FLAG", new LDUser("user-identifier"), defaultValue = false)
        _ <- IO.println(s"Boolean Variation: ${bool}")

        // String Variant
        string <- client.stringVariation[LDUser]("STRING-FLAG", new LDUser("user-identifier"), defaultValue = "default-string")
        _ <- IO.println(s"String Variation: ${string}")

        // Integer Variant
        int <- client.intVariation[LDUser]("INTEGER-FLAG", new LDUser("user-identifier"), defaultValue = 0)
        _ <- IO.println(s"Integer Variation: ${int}")

        // Double Variant
        double <- client.doubleVariation[LDUser]("DOUBLE-FLAG", new LDUser("user-identifier"), defaultValue = 0.00D)
        _ <- IO.println(s"Double Variation: ${double}")

        // JSON Variant
        json <- client.jsonVariation[LDUser]("JSON-FLAG", new LDUser("user-identifier"), defaultValue = LDValue.of("{}"))
        _ <- IO.println(s"JSON Variation: ${json}")
      } yield ()
    }
  }
```

### LDUser versus LDContext

From the LaunchDarkly [documentation](https://javadoc.io/doc/com.launchdarkly/launchdarkly-java-server-sdk/latest/com/launchdarkly/sdk/LDContext.html):

> LDContext is the newer replacement for the previous, less flexible LDUser type. The current LaunchDarkly SDK still supports LDUser, but
LDContext is now the preferred model and may entirely replace LDUser in the future.

A typeclass exists within Catapult for converting between `LDUser` and `LDContext`, so the variation methods can be called using either, hence the typed variation methods.

### Flush the buffered event queue

Specifies that any buffered events should be sent as soon as possible, rather than waiting for the next flush interval. 
The underlying is asynchronous, so events still may not be sent until a later time.

```scala
  launchDarklyClientResource.use { client =>
    client.flush
  }.unsafeRunSync()
```
