/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver

import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.Application

import scala.concurrent.ExecutionContext.Implicits.global

class EntityResolverMainSpec extends PlaySpec with ScalaFutures with MockitoSugar with GuiceOneAppPerTest {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure("metrics.enabled" -> false)
      .build()

  "EntityResolverMain" should {
    "include metricOrchestrator" in new TestCase {
      entityResolver.isInstanceOf[EntityResolverMain] must be(true)
      entityResolver.mc.isCancelled must be(false)
    }
  }

  class TestCase {
    val entityResolver: EntityResolverMain = app.injector.instanceOf[EntityResolverMain]
  }
}
