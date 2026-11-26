/*
 * Copyright 2019 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.matchers.must.Matchers
import play.api.Application
import play.api.http._
import play.api.libs.json._
import play.api.libs.ws.{ WSClient, WSRequest }
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers._
import play.api.test.{ FakeRequest, Helpers }
import uk.gov.hmrc.domain._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.format.DateTimeFormatter
import java.time.{ Duration, Instant, ZoneId }
import java.util.Locale
import scala.util.Try

object EndpointSupport extends Matchers {

  lazy val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE
    .withLocale(
      Locale.getDefault()
    )
    .withZone(ZoneId.of("UTC"))

  val testEmail = "test@test.com"

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val serviceBase = "preferences"

  protected def mkExternalResource(url: String, headers: Option[(String, String)] = None)(implicit
    wsClient: WSClient
  ): WSRequest =
    headers.fold(wsClient.url(url))(wsClient.url(url).withHttpHeaders(_))

  final case class FakeResponse(status: Int, body: String) {
    val emptyBody: Map[String, String] = Map.empty
    val json: JsValue = Try(Json.parse(body)).getOrElse(Json.toJson(emptyBody))
  }

  final case class FakeRequestBuilder(
    url: String,
    headers: Map[String, String] = Map.empty,
    queryParameters: Map[String, String] = Map.empty
  ) {

    def withHeaders(params: (String, String)*): FakeRequestBuilder =
      this.copy(headers = this.headers ++ params)

    def withQueryString(params: (String, String)*): FakeRequestBuilder =
      this.copy(queryParameters = this.queryParameters ++ params)

    def build(method: String): FakeRequest[AnyContentAsEmpty.type] =
      FakeRequest(method, url)
        .withHeaders(headers.toList: _*)

  }

  def doFakeRequest[A: Writeable](fakeRequest: FakeRequest[A])(implicit app: Application): FakeResponse = {
    val result = route(app, fakeRequest).get

    FakeResponse(Helpers.status(result), contentAsString(result))
  }

  def post(req: FakeRequestBuilder, body: JsValue)(implicit app: Application): FakeResponse =
    doFakeRequest(req.build("POST").withJsonBody(body))

  def nowDateTime: String = formatDateTimeForUrl(Instant.now())
  def yesterdayDateTime: String = formatDateTimeForUrl(Instant.now().minus(Duration.ofDays(1)))

  val yesterdayDate: String = dateFormatter.format(Instant.now().minus(Duration.ofDays(1)))

  val dateTimeFormatPattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
  val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter
      .ofPattern(dateTimeFormatPattern)
      .withLocale(Locale.getDefault())
      .withZone(ZoneId.of("UTC"))

  def formatDateTimeForUrl(value: Instant): String = dateTimeFormatter.format(value)

  protected def mkResource(url: String) = FakeRequestBuilder(url)

  def fakePostPreferencesTermsAndConditions(implicit authHeader: (String, String)): FakeRequestBuilder =
    mkResource("/preferences/terms-and-conditions").withHeaders(authHeader)

}
