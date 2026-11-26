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
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.{ Application, Configuration }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.libs.json.{ JsBoolean, JsString, JsValue, Writes }
import play.api.mvc.{ Result, Results }
import play.api.test.Helpers._
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads, HttpResponse }
import uk.gov.hmrc.http.client.{ HttpClientV2, RequestBuilder }
import java.net.URL
import scala.concurrent.{ ExecutionContext, Future }

class GenericConnectorSpec extends PlaySpec with ScalaFutures with MockitoSugar with GuiceOneAppPerTest {

  implicit val system: ActorSystem = ActorSystem("test")

  "doGet" should {
    "return the response from the call the the destination url" in new TestCase {
      private val result = connector.doGet[Result]("test=1")("/test")
      result.futureValue.header.status must be(OK)
      contentAsString(result) must be("true")
    }
    "return the response from the call the destination url and empty querystring" in new TestCase {
      private val result = connector.doGet[Result]("")("/test")
      result.futureValue.header.status must be(OK)
      contentAsString(result) must be("true")
    }
  }

  "doPost" should {
    "return the response from the call the the destination url" in new TestCase {
      private val result = connector.doPost[Result](JsString("body"), "test=1")("/test")
      result.futureValue.header.status must be(OK)
      contentAsString(result) must be("true")
    }
  }

  "doPut" should {
    "return the response from the call the the destination url" in new TestCase {
      private val result = connector.doPut[Result](JsString("body"), "test")("/test")
      result.futureValue.header.status must be(OK)
      contentAsString(result) must be("true")
    }
  }

  "doDelete" should {
    "return the response from the call the the destination url" in new TestCase {
      when(mockRequestBuilder.execute[HttpResponse](any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, "")))
      private val result: Future[HttpResponse] = connector.doDelete("/test")
      result.futureValue.status must be(OK)
    }
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[HttpClientV2].toInstance(mock[HttpClientV2]))
      .configure("metrics.enabled" -> "false")
      .build()

  trait TestCase {
    val config: Configuration = app.injector.instanceOf[Configuration]

    implicit val hc: HeaderCarrier = HeaderCarrier()

    implicit val httpReads: HttpReads[Result] = new HttpReads[Result] {
      override def read(method: String, url: String, response: HttpResponse): Result =
        Results.Status(response.status)(response.body)
    }

    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
    implicit val httpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]
    private val response: Result = Results.Ok(JsBoolean(true))
    val mockRequestBuilder: RequestBuilder = mock[RequestBuilder]
    when(
      httpClient
        .get(any())(any[HeaderCarrier])
    ).thenReturn(mockRequestBuilder)

    when(
      httpClient
        .post(
          any[URL]
        )(any[HeaderCarrier])
    ).thenReturn(mockRequestBuilder)

    when(
      httpClient
        .put(
          any[URL]
        )(any[HeaderCarrier])
    ).thenReturn(mockRequestBuilder)

    when(
      httpClient
        .delete(
          any[URL]
        )(any[HeaderCarrier])
    ).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(any[JsValue])(any(), any(), any()))
      .thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.execute(any(), any()))
      .thenReturn(Future.successful(response))
    val connector = new GenericConnector(httpClient, "http://localhost:8080/")
  }

}
