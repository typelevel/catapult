// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.4" // your current series x.y

ThisBuild / organization := "io.github.bplommer"
ThisBuild / organizationName := "Ben Plommer"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("bplommer", "Ben Plommer")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

val Scala213 = "2.13.10"
ThisBuild / crossScalaVersions := Seq(Scala213, "3.2.2")
ThisBuild / scalaVersion := Scala213 // the default Scala

lazy val root = tlCrossRootProject.aggregate(core, testkit)

lazy val testkit = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("testkit"))
  .settings(
    name := "catapult-testkit",
    libraryDependencies ++= Seq(
      "com.disneystreaming" %% "weaver-cats" % "0.8.1" % Test
    ),
    testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
    tlVersionIntroduced := List("2.13", "3").map(_ -> "0.1.0").toMap,
  )
  .dependsOn(core)

lazy val core = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "catapult",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.9.0",
      "org.typelevel" %%% "cats-effect" % "3.4.8",
      "co.fs2" %%% "fs2-core" % "3.6.1",
      "com.launchdarkly" % "launchdarkly-java-server-sdk" % "6.0.6",
    ),
  )

lazy val docs = project.in(file("site")).enablePlugins(TypelevelSitePlugin)
