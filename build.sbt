// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.6" // your current series x.y

ThisBuild / organization := "org.typelevel"
ThisBuild / organizationName := "Typelevel"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("bplommer", "Ben Plommer"),
  tlGitHubDev("averymcnab", "Avery McNab"),
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

val Scala213 = "2.13.16"
ThisBuild / crossScalaVersions := Seq(Scala213, "3.3.3")
ThisBuild / scalaVersion := Scala213 // the default Scala

lazy val root = tlCrossRootProject.aggregate(core, mtl, testkit)

lazy val testkit = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("testkit"))
  .settings(
    name := "catapult-testkit",
    libraryDependencies ++= Seq(
      "com.disneystreaming" %% "weaver-cats" % "0.8.4" % Test
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
      "org.typelevel" %%% "cats-core" % "2.12.0",
      "org.typelevel" %%% "cats-effect" % "3.5.7",
      "co.fs2" %%% "fs2-core" % "3.10.2",
      "com.launchdarkly" % "launchdarkly-java-server-sdk" % "7.7.0",
    ),
  )

lazy val mtl = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("mtl"))
  .settings(
    name := "catapult-mtl",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-mtl" % "1.5.0"
    ),
    tlVersionIntroduced := Map(
      "2.13" -> "0.5.1",
      "3" -> "0.5.1",
    ),
  )
  .dependsOn(core)

lazy val docs = project.in(file("site")).enablePlugins(TypelevelSitePlugin)
