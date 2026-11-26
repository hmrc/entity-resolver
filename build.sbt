import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "entity-resolver"

Global / majorVersion := 3
Global / scalaVersion := "3.3.6"
Global / scalafmtOnCompile := true

val excludedPackages: Seq[String] = Seq(
  "<empty>",
  "Reverse.*",
  ".*Routes.*",
  ".*BuildInfo.*",
  ".*\\$anon.*",
  "uk.gov.hmrc.entityresolver.controllers.testonly",
  "testOnlyDoNotUseInAppConf.*"
)

lazy val scoverageSettings =
  Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(","),
    ScoverageKeys.coverageMinimumStmtTotal := 90.00,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )

lazy val appDependencies: Seq[ModuleID] = AppDependencies.microServiceDependencies

lazy val playSettings: Seq[Setting[?]] = AppDependencies.playSettings

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) // Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(playSettings *)
  .settings(routesGenerator := InjectedRoutesGenerator)
  .settings(
    libraryDependencies ++= appDependencies,
    Test / parallelExecution := false,
    Test / fork := false,
    retrieveManaged := true,
    scalacOptions ++= List(
      "-no-indent",
      "-feature",
      "-deprecation",
      "-language:postfixOps",
      "-language:reflectiveCalls",
      // Silence unused imports in template files
      "-Wconf:msg=unused import&src=.*:s",
      // Silence "Flag -XXX set repeatedly"
      "-Wconf:msg=Flag.*repeatedly:s",
      // Silence unused warnings on Play `routes` files
      "-Wconf:src=routes/.*:s"
    )
  )
  .settings(scoverageSettings.settings *)

lazy val it = Project(id = "it", base = file("it"))
  .enablePlugins(PlayScala, ScalafmtPlugin)
  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(ScalafmtPlugin.scalafmtConfigSettings)
  .settings(libraryDependencies ++= AppDependencies.itDependencies)
  .settings(
    scalacOptions ++= List(
      "-language:implicitConversions",
      "-feature",
      // Silence "Flag -XXX set repeatedly"
      "-Wconf:msg=Flag.*repeatedly:s"
    )
  )

addCommandAlias("fmt", "Compile/scalafmtAll;Test/scalafmtAll;it/scalafmtAll")
