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

import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.test.Helpers._
import play.api.Application
import uk.gov.hmrc.entityresolver.models._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.{ HttpClientV2, RequestBuilder }
import scala.concurrent.{ ExecutionContext, Future }

class EnrolmentStoreProxyConnectorSpec extends PlaySpec with ScalaFutures with MockitoSugar with GuiceOneAppPerTest {

  implicit val system: ActorSystem = ActorSystem("test")

  "enrolment store proxy connector" should {
    "get the credentials id" in new TestCase {
      private val body =
        """{
          |    "principalUserIds": [
          |       "ABCEDEFGI1234567",
          |       "ABCEDEFGI1234568"
          |    ]
          |}""".stripMargin
      private val response = HttpResponse(OK, body)

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(response))

      val expectedResult = PrincipalUserIdResponse(List("ABCEDEFGI1234567", "ABCEDEFGI1234568"))
      await(connector.getCredentialsId(saUtr)) mustBe Right(Some(expectedResult))
    }

    "return Left - when the request body is invalid" in new TestCase {
      private val body =
        """{
          |    "key": "value"
          |}""".stripMargin
      private val response = HttpResponse(OK, body)

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(response))

      await(connector.getCredentialsId(saUtr)) mustBe Left("invalid of json body")
    }

    "return Left - when the request body is not a json" in new TestCase {
      private val body = "NotAJsonString"
      private val response = HttpResponse(OK, body)

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(response))

      await(connector.getCredentialsId(saUtr)) mustBe Left("Not a json error")
    }

    "return Right(None) when downstream response is a 204" in new TestCase {

      private val response = HttpResponse(NO_CONTENT, "")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(response))

      await(connector.getCredentialsId(saUtr)) mustBe Right(None)
    }

    "return Left when downstream response status is not OK or NO_CONTENT" in new TestCase {

      private val response = HttpResponse(INTERNAL_SERVER_ERROR, "")

      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(response))

      await(connector.getCredentialsId(saUtr)) mustBe Left("Got unexpected status 500")
    }

  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[HttpClientV2].toInstance(mock[HttpClientV2]))
      .configure(
        "metrics.enabled" -> "false"
      )
      .build()

  class TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val saUtr = EnrolmentKey("IR-SA", "SA-UTR", "9999999999")

    val connector: EnrolmentStoreProxyConnector = app.injector.instanceOf[EnrolmentStoreProxyConnector]
    val mockHttpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
    val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
    when(
      mockHttpClient
        .get(any())(any[HeaderCarrier])
    ).thenReturn(mockRequestBuilder)
  }
}
