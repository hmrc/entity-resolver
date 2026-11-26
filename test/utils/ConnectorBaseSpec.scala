/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package utils

import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.test.WireMockSupport

abstract class ConnectorBaseSpec(configKey: String)
    extends PlaySpec with ScalaFutures with WireMockSupport with GuiceOneAppPerSuite with IntegrationPatience {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(s"microservice.services.$configKey.port" -> wireMockPort)
      .build()

}
