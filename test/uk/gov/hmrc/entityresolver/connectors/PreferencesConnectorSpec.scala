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

package uk.gov.hmrc.entityresolver.connectors

import ch.qos.logback.classic.{ Level, Logger as LogbackLogger }
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.http.Fault
import com.google.inject.name.Names
import com.google.inject.{ Injector, Key, TypeLiteral }
import org.apache.pekko.actor.ActorSystem
import org.scalactic.source.Position
import play.api.libs.json.{ JsResultException, Json }
import play.api.mvc.Result
import play.api.mvc.Results.*
import play.api.test.Helpers.*
import play.api.{ Configuration, Logger }
import play.shaded.ahc.org.asynchttpclient.exception.RemotelyClosedException
import uk.gov.hmrc.entityresolver.models.*
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.HttpClientV2
import utils.TestData.TEST_ENTITY_ID
import utils.{ ConnectorBaseSpec, LogCapturing }

import scala.concurrent.{ ExecutionContext, Future }

class PreferencesConnectorSpec extends ConnectorBaseSpec(PreferencesConnector.configKey) with LogCapturing {

  implicit val system: ActorSystem = ActorSystem("test")

  def stubServerFailureCall(): Unit =
    wireMockServer.stubFor(
      WireMock
        .any(WireMock.anyUrl())
        .willReturn(WireMock.aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
    )

  "updated" should {

    def stubUpdated(entityId: EntityId, statusCode: Int): Unit =
      wireMockServer.stubFor(
        WireMock
          .put(WireMock.urlEqualTo(s"/preferences/$entityId/updated"))
          .withRequestBody(WireMock.equalToJson(Json.obj().toString))
          .willReturn {
            WireMock
              .aResponse()
              .withStatus(statusCode)
          }
      )

    def testLoggedFailure(logger: LogbackLogger, call: => Future[Boolean], expected: Boolean, logLevel: Level)(implicit
      position: Position
    ): Unit =
      withCaptureOfLoggingFrom(logger) { logEvents =>
        call.futureValue mustBe expected
        logEvents.headOption.map(_.getLevel) mustBe Some(logLevel)
      }

    "call preferences updated for given entityId and return true for a NO CONTENT response" in new TestCase {
      stubUpdated(TEST_ENTITY_ID, play.api.http.Status.NO_CONTENT)

      await(preferencesConnector.updated(TEST_ENTITY_ID)) mustBe true
    }

    "recover from 500 return from calling preferences logging with warn" in new TestCase {
      stubUpdated(TEST_ENTITY_ID, play.api.http.Status.INTERNAL_SERVER_ERROR)

      testLoggedFailure(
        logger = logger,
        call = preferencesConnector.updated(TEST_ENTITY_ID),
        expected = false,
        logLevel = Level.WARN
      )
    }

    "recover from 404 return from calling preferences loggijng with info" in new TestCase {
      stubUpdated(TEST_ENTITY_ID, play.api.http.Status.NOT_FOUND)

      testLoggedFailure(
        logger = logger,
        call = preferencesConnector.updated(TEST_ENTITY_ID),
        expected = false,
        logLevel = Level.INFO
      )
    }

    "recover from Exception return from calling preferences logging with warn" in new TestCase {
      stubServerFailureCall()

      testLoggedFailure(
        logger = logger,
        call = preferencesConnector.updated(TEST_ENTITY_ID),
        expected = false,
        logLevel = Level.WARN
      )
    }
  }

  "getPreferencesStatus" should {

    def stubGetPreferencesStatus(entityId: EntityId, statusCode: Int, responseBody: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .get(WireMock.urlEqualTo(s"/preferences/${entityId.value}"))
          .willReturn {
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(responseBody)
          }
      )

    "return a 200 if preference does exist" in new TestCase {
      val body = """{"termsAndConditions":"true"}"""

      stubGetPreferencesStatus(TEST_ENTITY_ID, OK, body)
      val preferenceStatus: Future[Result] = preferencesConnector.getPreferencesStatus(TEST_ENTITY_ID)

      preferenceStatus.futureValue.header.status mustBe play.api.http.Status.OK
      contentAsString(preferenceStatus) mustBe body
    }

    "return a 404 if preference does not exist" in new TestCase {
      stubGetPreferencesStatus(TEST_ENTITY_ID, NOT_FOUND, "")
      await(preferencesConnector.getPreferencesStatus(TEST_ENTITY_ID)) mustBe NotFound
    }

    "return a 404 if preference api call gets an unexpected response" in new TestCase {
      stubGetPreferencesStatus(TEST_ENTITY_ID, INTERNAL_SERVER_ERROR, "")
      await(preferencesConnector.getPreferencesStatus(TEST_ENTITY_ID)) mustBe NotFound
    }

    "return a failed future on connectivity issues" in new TestCase {
      stubServerFailureCall()
      preferencesConnector.getPreferencesStatus(TEST_ENTITY_ID).failed.futureValue mustBe a[RemotelyClosedException]
    }

  }

  "getPreferences" should {

    def stubGetPreferences(entityId: EntityId, statusCode: Int, responseBody: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .get(WireMock.urlEqualTo(s"/preferences/${entityId.value}"))
          .willReturn {
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(responseBody)
          }
      )

    "return successful HttpResponse if preference does exist" in new TestCase {
      val body = """{"termsAndConditions":"true"}"""
      stubGetPreferences(TEST_ENTITY_ID, OK, body)

      val maybPreferenceStatus: Option[(Int, String)] =
        preferencesConnector.getPreferences(TEST_ENTITY_ID).futureValue.map(status => status.status -> status.body)

      maybPreferenceStatus mustBe Some(
        play.api.http.Status.OK -> body
      )

    }

    "return None if preference does not exist" in new TestCase {
      stubGetPreferences(TEST_ENTITY_ID, NOT_FOUND, "")

      preferencesConnector.getPreferences(TEST_ENTITY_ID).futureValue mustBe None
    }

    "return None if preference api call does not get a 200 response" in new TestCase {
      stubGetPreferences(TEST_ENTITY_ID, INTERNAL_SERVER_ERROR, "")

      await(preferencesConnector.getPreferences(TEST_ENTITY_ID)) mustBe None
    }

    "return a failed future on connectivity issues" in new TestCase {
      stubServerFailureCall()
      preferencesConnector.getPreferences(TEST_ENTITY_ID).failed.futureValue mustBe a[RemotelyClosedException]
    }
  }

  "getPreferencesForOptOut" should {
    def stubGetPreferencesForOptOut(entityId: EntityId, statusCode: Int, responseBody: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .get(WireMock.urlEqualTo(s"/preferences/${entityId.value}"))
          .willReturn {
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(responseBody)
          }
      )

    def createResponse(maybeGeneric: Option[Boolean], maybeTaxCredits: Option[Boolean]): String =
      s"""
         |{
         |  "termsAndConditions" : {
         |     "generic" : {
         |        "accepted" : ${maybeGeneric.orNull}
         |     },
         |     "taxCredits" : {
         |        "accepted" : ${maybeTaxCredits.orNull}
         |     }
         |  }
         |}
         |""".stripMargin

    "return a response with all None when the JSON has missing values" in new TestCase {
      val body = "{}"
      stubGetPreferencesForOptOut(TEST_ENTITY_ID, OK, body)

      val maybePreferenceStatus: Option[PreferenceStatus] =
        preferencesConnector.getPreferencesForOptOut(TEST_ENTITY_ID).futureValue

      maybePreferenceStatus mustBe Some(PreferenceStatus(maybeGeneric = None, maybeTaxCredits = None))
    }

    "return a response with all None when values are null" in new TestCase {
      val body: String = createResponse(maybeGeneric = None, maybeTaxCredits = None)
      stubGetPreferencesForOptOut(TEST_ENTITY_ID, OK, body)

      val maybePreferenceStatus: Option[PreferenceStatus] =
        preferencesConnector.getPreferencesForOptOut(TEST_ENTITY_ID).futureValue

      maybePreferenceStatus mustBe Some(PreferenceStatus(maybeGeneric = None, maybeTaxCredits = None))
    }

    "return a response with gerneric only set to false" in new TestCase {
      val body: String = createResponse(maybeGeneric = Some(false), maybeTaxCredits = None)
      stubGetPreferencesForOptOut(TEST_ENTITY_ID, OK, body)

      val maybePreferenceStatus: Option[PreferenceStatus] =
        preferencesConnector.getPreferencesForOptOut(TEST_ENTITY_ID).futureValue

      maybePreferenceStatus mustBe Some(PreferenceStatus(maybeGeneric = Some(false), maybeTaxCredits = None))
    }

    "return a response with gerneric only set to true" in new TestCase {
      val body: String = createResponse(maybeGeneric = Some(true), maybeTaxCredits = None)
      stubGetPreferencesForOptOut(TEST_ENTITY_ID, OK, body)

      val maybePreferenceStatus: Option[PreferenceStatus] =
        preferencesConnector.getPreferencesForOptOut(TEST_ENTITY_ID).futureValue

      maybePreferenceStatus mustBe Some(PreferenceStatus(maybeGeneric = Some(true), maybeTaxCredits = None))
    }

    "return a response with textCredits only set to false" in new TestCase {
      val body: String = createResponse(maybeGeneric = None, maybeTaxCredits = Some(false))
      stubGetPreferencesForOptOut(TEST_ENTITY_ID, OK, body)

      val maybePreferenceStatus: Option[PreferenceStatus] =
        preferencesConnector.getPreferencesForOptOut(TEST_ENTITY_ID).futureValue

      maybePreferenceStatus mustBe Some(PreferenceStatus(maybeGeneric = None, maybeTaxCredits = Some(false)))
    }

    "return a response with textCredits only set to true" in new TestCase {
      val body: String = createResponse(maybeGeneric = None, maybeTaxCredits = Some(true))
      stubGetPreferencesForOptOut(TEST_ENTITY_ID, OK, body)

      val maybePreferenceStatus: Option[PreferenceStatus] =
        preferencesConnector.getPreferencesForOptOut(TEST_ENTITY_ID).futureValue

      maybePreferenceStatus mustBe Some(PreferenceStatus(maybeGeneric = None, maybeTaxCredits = Some(true)))
    }

    "return a response with both values set to false" in new TestCase {
      val body: String = createResponse(maybeGeneric = Some(false), maybeTaxCredits = Some(false))
      stubGetPreferencesForOptOut(TEST_ENTITY_ID, OK, body)

      val maybePreferenceStatus: Option[PreferenceStatus] =
        preferencesConnector.getPreferencesForOptOut(TEST_ENTITY_ID).futureValue

      maybePreferenceStatus mustBe Some(PreferenceStatus(maybeGeneric = Some(false), maybeTaxCredits = Some(false)))
    }

    "return a response with both values set to true" in new TestCase {
      val body: String = createResponse(maybeGeneric = Some(true), maybeTaxCredits = Some(true))
      stubGetPreferencesForOptOut(TEST_ENTITY_ID, OK, body)

      val maybePreferenceStatus: Option[PreferenceStatus] =
        preferencesConnector.getPreferencesForOptOut(TEST_ENTITY_ID).futureValue

      maybePreferenceStatus mustBe Some(PreferenceStatus(maybeGeneric = Some(true), maybeTaxCredits = Some(true)))
    }

    "return a failed future on connectivity issues" in new TestCase {
      stubServerFailureCall()
      preferencesConnector.getPreferencesForOptOut(TEST_ENTITY_ID).failed.futureValue mustBe a[RemotelyClosedException]
    }
  }

  "optIn" should {
    def stubOptIn(entityId: EntityId, body: String, statusCode: Int, responseText: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .post(WireMock.urlEqualTo(s"/preferences/${entityId.value}/optin"))
          .withRequestBody(WireMock.equalToJson(body))
          .willReturn {
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(responseText)
          }
      )

    val terms = TermsAndConditionsUpdate.from((GenericTerms, TermsAccepted(true)), Some("random@mail.com"))

    "return a Ok if the preference service returns OK" in new TestCase {
      val responseText = """{"termsAndConditions":"true"}"""

      stubOptIn(TEST_ENTITY_ID, Json.toJson(terms).toString, OK, responseText)

      val termsAndConditionsStatus: Future[Result] = preferencesConnector.optIn(TEST_ENTITY_ID, terms)

      termsAndConditionsStatus.futureValue.header.status mustBe OK
      contentAsString(termsAndConditionsStatus) mustBe responseText
    }

    "return a BadRequest if the preference service returns BAD_REQUEST" in new TestCase {
      stubOptIn(TEST_ENTITY_ID, Json.toJson(terms).toString, BAD_REQUEST, "")

      await(preferencesConnector.optIn(TEST_ENTITY_ID, terms)) mustBe BadRequest
    }

    "return a Created if the preference service returns CREATED" in new TestCase {
      private val terms = TermsAndConditionsUpdate.from((GenericTerms, TermsAccepted(true)), Some("random@mail.com"))
      private val responseBody = """{"termsAndConditions":"true"}"""

      stubOptIn(TEST_ENTITY_ID, Json.toJson(terms).toString, CREATED, responseBody)

      private val termsAndConditionsStatus = preferencesConnector.optIn(TEST_ENTITY_ID, terms)

      termsAndConditionsStatus.futureValue.header.status mustBe CREATED
      contentAsString(termsAndConditionsStatus) mustBe responseBody
    }

    "return a failed future on connectivity issues" in new TestCase {
      stubServerFailureCall()
      preferencesConnector
        .optIn(TEST_ENTITY_ID, terms)
        .failed
        .futureValue mustBe a[RemotelyClosedException]
    }
  }

  "optOut" should {
    def stubOptOut(entityId: EntityId, body: String, statusCode: Int, responseText: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .post(WireMock.urlEqualTo(s"/preferences/${entityId.value}/optout"))
          .withRequestBody(WireMock.equalToJson(body))
          .willReturn {
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(responseText)
          }
      )

    "return as expected" in new TestCase {
      private val terms = TermsAndConditionsUpdate.from((GenericTerms, TermsAccepted(false)), Some("random@mail.com"))
      private val responseText = """{"termsAndConditions":false}"""

      stubOptOut(TEST_ENTITY_ID, Json.toJson(terms).toString, CREATED, responseText)

      private val termsAndConditionsStatus = preferencesConnector.optOut(TEST_ENTITY_ID, terms)

      termsAndConditionsStatus.futureValue.header.status mustBe CREATED
      contentAsString(termsAndConditionsStatus) mustBe responseText
    }

    "return a failed future on connectivity issues" in new TestCase {
      private val terms = TermsAndConditionsUpdate.from((GenericTerms, TermsAccepted(false)), Some("random@mail.com"))
      stubServerFailureCall()
      preferencesConnector
        .optOut(TEST_ENTITY_ID, terms)
        .failed
        .futureValue mustBe a[RemotelyClosedException]
    }
  }

  "changeEmailLanguage" should {
    def stubChangeEmailLanguage(entityId: EntityId, body: String, statusCode: Int, responseText: String): Unit =
      wireMockServer.stubFor(
        WireMock
          .post(WireMock.urlEqualTo(s"/preferences/${entityId.value}/email-language"))
          .withRequestBody(WireMock.equalToJson(body))
          .willReturn {
            WireMock
              .aResponse()
              .withStatus(statusCode)
              .withBody(responseText)
          }
      )

    "email-language returns as expected" in new TestCase {
      private val terms = TermsAndConditionsUpdate.from((GenericTerms, TermsAccepted(false)), Some("random@mail.com"))
      private val responseText = """{"language":"en"}"""

      stubChangeEmailLanguage(TEST_ENTITY_ID, Json.toJson(terms).toString, CREATED, responseText)

      private val termsAndConditionsStatus = preferencesConnector.changeEmailLanguage(TEST_ENTITY_ID, terms)

      termsAndConditionsStatus.futureValue.header.status mustBe CREATED
      contentAsString(termsAndConditionsStatus) mustBe responseText
    }

    "return a failed future on connectivity issues" in new TestCase {
      private val terms = TermsAndConditionsUpdate.from((GenericTerms, TermsAccepted(false)), Some("random@mail.com"))
      stubServerFailureCall()
      preferencesConnector
        .changeEmailLanguage(TEST_ENTITY_ID, terms)
        .failed
        .futureValue mustBe a[RemotelyClosedException]
    }
  }

  "optOutTerms" should {
    val terms = "termIdentifier"
    val payload = s"""{"$terms":{"accepted":false, "manualOptOut":true}}"""
    val entityId = EntityId("1234453")

    def stubOptOutTerms(statusCode: Int): Unit =
      wireMockServer.stubFor(
        WireMock
          .post(WireMock.urlEqualTo(s"/preferences-admin/optout/${entityId.value}"))
          .withRequestBody(WireMock.equalToJson(payload))
          .willReturn {
            WireMock
              .aResponse()
              .withStatus(statusCode)
          }
      )

    "return true when OK is returned" in new TestCase {
      stubOptOutTerms(OK)
      private val updatedOptOutTerms: Boolean = preferencesConnector.optOutTerms(entityId, terms).futureValue

      updatedOptOutTerms mustBe true

    }

    "return false when OK is returned" in new TestCase {
      stubOptOutTerms(CONFLICT)
      private val updatedOptOutTerms: Boolean = preferencesConnector.optOutTerms(entityId, terms).futureValue

      updatedOptOutTerms mustBe false
    }

    "throw a UnexpectedOptOutResponseException on an unexpected status code" in new TestCase {
      stubOptOutTerms(NOT_FOUND)

      private val errorOnUnexpectedStatusCode = preferencesConnector.optOutTerms(entityId, terms).failed.futureValue

      errorOnUnexpectedStatusCode mustBe an[UnexpectedOptOutResponseException]
    }

    "return a failed future on connectivity issues" in new TestCase {
      stubServerFailureCall()
      preferencesConnector.optOutTerms(TEST_ENTITY_ID, terms).failed.futureValue mustBe a[RemotelyClosedException]
    }

  }

  "EntityResolverRequest.format" should {
    import EntityResolverRequest.format

    val entityResolverReq = EntityResolverRequest(entityId = TEST_ENTITY_ID)
    val entityResolverReqJsonString = """{"entityId":"123456"}""".stripMargin
    val entityResolverReqInvalidJsonString = """{"entityId1":"test"}""".stripMargin

    "read the json correctly" in {
      Json.parse(entityResolverReqJsonString).as[EntityResolverRequest] mustBe entityResolverReq
    }

    "throw the exception for invalid json" in {
      intercept[JsResultException] {
        Json.parse(entityResolverReqInvalidJsonString).as[EntityResolverRequest]
      }
    }

    "write the object correctly" in {
      Json.toJson(entityResolverReq) mustBe Json.parse(entityResolverReqJsonString)
    }
  }

  class TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

    val httpReads: HttpReads[Result] = app.injector
      .instanceOf[Injector]
      .getInstance(Key.get(new TypeLiteral[HttpReads[Result]] {}, Names.named("httpReadsResult")))

    val itemId = "someTestId"
    val testServiceUrl = "testServiceUrl"

    val config: Configuration = app.injector.instanceOf[Configuration]

    val preferencesConnector: PreferencesConnector = app.injector.instanceOf[PreferencesConnector]
    val logger: LogbackLogger = Logger(preferencesConnector.getClass).underlyingLogger.asInstanceOf[LogbackLogger]

    val mockHttpClient: HttpClientV2 = app.injector.instanceOf[HttpClientV2]

  }

}
