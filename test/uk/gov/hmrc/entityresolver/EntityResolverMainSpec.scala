/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
