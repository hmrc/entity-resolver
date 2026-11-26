import play.sbt.routes.RoutesKeys.routesImport
import sbt.{ Def, Test, * }

private object AppDependencies {
  import play.sbt.PlayImport.*

  val bootstrapVersion = "10.7.0"
  val hmrcMongoVersion = "2.14.0"
  val hmrcDomainVersion = "13.0.0"

  val microServiceDependencies: Seq[ModuleID] = Seq(
    ws,
    "org.typelevel"     %% "cats-core"                 % "2.13.0",
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-work-item-repo-play-30" % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "domain-play-30"            % hmrcDomainVersion,
    "uk.gov.hmrc"       %% "domain-test-play-30"       % hmrcDomainVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30"   % hmrcMongoVersion  % Test,
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"    % bootstrapVersion  % Test,
    "org.scalatestplus" %% "mockito-3-4"               % "3.2.10.0"        % Test,
    "org.scalactic"     %% "scalactic"                 % "3.2.19"          % Test
  )

  val playSettings: Seq[Def.Setting[Seq[String]]] = Seq(
    routesImport ++= Seq(
      "uk.gov.hmrc.entityresolver._",
      "uk.gov.hmrc.entityresolver.binders.Binders._",
      "uk.gov.hmrc.domain._"
    )
  )

  val itDependencies: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion % "it/test",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % "it/test"
  )
}
