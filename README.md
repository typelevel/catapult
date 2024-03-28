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
        bool <- client.boolVariation("BOOL-FLAG", new LDContext("context-name"), defaultValue = false)
        _ <- IO.println(s"Boolean Variation: ${bool}")
  
        // String Variant
        string <- client.stringVariation("STRING-FLAG", new LDContext("context-name"), defaultValue = "default-string")
        _ <- IO.println(s"String Variation: ${string}")
  
        // Integer Variant
        int <- client.intVariation("INTEGER-FLAG", new LDContext("context-name"), defaultValue = 0)
        _ <- IO.println(s"Integer Variation: ${int}")
  
        // Double Variant
        double <- client.doubleVariation("DOUBLE-FLAG", new LDContext("context-name"), defaultValue = 0.00D)
        _ <- IO.println(s"Double Variation: ${double}")
  
        // JSON Variant
        json <- client.jsonValueVariation("JSON-FLAG", new LDContext("context-name"), defaultValue = LDValue.of("{}"))
        _ <- IO.println(s"JSON Variation: ${json}")
      } yield ()
    }
  }
```

### Flush the buffered event queue

Specifies that any buffered events should be sent as soon as possible, rather than waiting for the next flush interval. 
The underlying is asynchronous, so events still may not be sent until a later time.

```scala
  launchDarklyClientResource.use { client =>
    client.flush
  }.unsafeRunSync()
```
