/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.entityresolver.connectors

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{ Retrieval, ~ }
import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.http._

import scala.concurrent.{ ExecutionContext, Future }

class TaxIdentifierRetrieverSpec extends PlaySpec with ScalaFutures with GuiceOneAppPerTest with MockitoSugar {

  "currentTaxIdentifiers" should {
    "return an empty set if the auth call is not authorised" in new TestCase {
      val retrievals = new ~(None, None)
      when(
        mockAuthConnector.authorise[~[Option[String], Option[String]]](
          any[Predicate],
          any[Retrieval[Option[String] ~ Option[String]]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(retrievals))

      taxIdentifierRetriever.currentTaxIdentifiers.futureValue must be(Set())
    }

    "throw an exception if the auth call fails " in new TestCase {
      when(
        mockAuthConnector.authorise[~[Option[String], Option[String]]](
          any[Predicate],
          any[Retrieval[Option[String] ~ Option[String]]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.failed(new RuntimeException("test exception")))

      intercept[RuntimeException] {
        taxIdentifierRetriever.currentTaxIdentifiers.futureValue must be(Set())
      }
    }

    "return a set of TaxIdsWithName" in new TestCase {
      val retrievals = new ~(Some("1872796160"), Some("CE123456D"))
      when(
        mockAuthConnector.authorise[~[Option[String], Option[String]]](
          any[Predicate],
          any[Retrieval[Option[String] ~ Option[String]]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(retrievals))

      taxIdentifierRetriever.currentTaxIdentifiers.futureValue must be(Set(SaUtr("1872796160"), Nino("CE123456D")))
    }
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure("metrics.enabled" -> "false")
      .overrides(bind[AuthConnector].toInstance(mock[AuthConnector]))
      .build()

  trait TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
    val taxIdentifierRetriever: TaxIdentifierRetriever = app.injector.instanceOf[TaxIdentifierRetriever]
    lazy val mockAuthConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
  }

}
