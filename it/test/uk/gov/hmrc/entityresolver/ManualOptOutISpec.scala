/*
 * Copyright 2019 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver

import org.scalatest.BeforeAndAfterEach
import play.api.http.Status.*
import play.api.libs.json.{ JsBoolean, JsDefined, Json }
import uk.gov.hmrc.entityresolver.util.{ AuthUtil, WithWireMock }
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String

class ManualOptOutISpec extends ControllerISpec with WithWireMock with BeforeAndAfterEach {

  import EndpointSupport._

  val sa = "sa"
  val nino = "paye"
  val ctutr = "ctUtr"

  "manual opt out" should {
    "return CONFLICT if the UTR is already opted-out" in new TestCaseWithUtrOnly {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)
      private val updateResult = httpClient
        .url(resource("/preferences/terms-and-conditions"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"generic":{"accepted":false,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, "email":"$testEmail","language": "en"}"""
          )
        )
        .futureValue
      updateResult.status must be(CREATED)

      private val optOutResult =
        httpClient.url(resource(s"/entity-resolver-admin/manual-opt-out/$sa/$randomUtr")).post("").futureValue
      optOutResult.status must be(CONFLICT)
    }

    "return OK and call preferences to opt utr and nino out having opted in for both" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)
      private val termsAndConditionsResponse = httpClient
        .url(resource("/preferences/terms-and-conditions"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"generic":{"accepted":true,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, "email":"$testEmail","language": "en"}"""
          )
        )
        .futureValue
      termsAndConditionsResponse.status must be(CREATED)

      private val optOutResponse = httpClient
        .url(resource(s"/entity-resolver-admin/manual-opt-out/$sa/$randomUtr"))
        .post(Json.obj())
        .futureValue
      optOutResponse.status must be(OK)

      private val newAuthHeader: (String, String) = AuthUtil.buildNinoUserToken(randomNino, authLoginUrl)
      private val response = httpClient.url(resource("/preferences")).withHttpHeaders(newAuthHeader).get().futureValue
      response.status must be(OK)
      (response.json \ "termsAndConditions" \ "generic" \ "accepted") must be(JsDefined(JsBoolean(false)))
    }

    "return OK and call preferences to opt utr and nino out having opted in for generic only" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)
      private val termsAndConditionsAcceptedResponse = httpClient
        .url(resource("/preferences/terms-and-conditions"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"generic":{"accepted":true,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, "email":"$testEmail","language": "en"}"""
          )
        )
        .futureValue
      termsAndConditionsAcceptedResponse.status must be(CREATED)

      private val termsAndConditionsDeclinedRespons = httpClient
        .url(resource("/preferences/terms-and-conditions"))
        .withHttpHeaders(authHeader)
        .post(Json.parse(s"""{"taxCredits":{"accepted":false}, "email": "$testEmail"}"""))
        .futureValue
      termsAndConditionsDeclinedRespons.status must be(OK)

      private val optOutResponse = httpClient
        .url(resource(s"/entity-resolver-admin/manual-opt-out/$sa/$randomUtr"))
        .withHttpHeaders(authHeader)
        .post(Json.obj())
        .futureValue
      optOutResponse.status must be(OK)

      private val newAuthHeader: (String, String) = AuthUtil.buildNinoUserToken(randomNino, authLoginUrl)

      private val response = httpClient
        .url(resource("/preferences"))
        .withHttpHeaders(newAuthHeader)
        .get()
        .futureValue
      response.status must be(OK)
      (response.json \ "termsAndConditions" \ "generic" \ "accepted") must be(JsDefined(JsBoolean(false)))
    }

    "return OK and call preferences to opt utr and nino out" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)
      private val termsAndConditionsResponse = httpClient
        .url(resource("/preferences/terms-and-conditions"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"generic":{"accepted":true,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, "email":"$testEmail","language": "en"}"""
          )
        )
        .futureValue
      termsAndConditionsResponse.status must be(CREATED)

      private val optOutResponse = httpClient
        .url(resource(s"/entity-resolver-admin/manual-opt-out/$sa/$randomUtr"))
        .post(Json.obj())
        .futureValue
      optOutResponse.status must be(OK)

      private val newAuthHeader: (String, String) = AuthUtil.buildNinoUserToken(randomNino, authLoginUrl)

      private val response = httpClient
        .url(resource("/preferences"))
        .withHttpHeaders(newAuthHeader)
        .get()
        .futureValue
      response.status must be(OK)
      (response.json \ "termsAndConditions" \ "generic" \ "accepted") must be(JsDefined(JsBoolean(false)))
    }

    "return OK and call preferences to opt utr out" in new TestCaseWithUtrOnly {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)
      private val termsAndConditionsResponse = httpClient
        .url(resource("/preferences/terms-and-conditions"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"generic":{"accepted":true,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, "email":"$testEmail","language": "en"}"""
          )
        )
        .futureValue
      termsAndConditionsResponse.status must be(CREATED)

      private val optOutResponse = httpClient
        .url(resource(s"/entity-resolver-admin/manual-opt-out/$sa/$randomUtr"))
        .withHttpHeaders(authHeader)
        .post(Json.obj())
        .futureValue
      optOutResponse.status must be(OK)

      private val response = httpClient
        .url(resource("/preferences"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status must be(OK)
      (response.json \ "termsAndConditions" \ "generic" \ "accepted") must be(JsDefined(JsBoolean(false)))
    }

    "return OK and call preferences to opt nino out" in new TestCaseWithNinoOnly {
      stubEntityResolverTaxIdNino()
      private val authHeader = getAuthHeader(authLoginUrl)
      private val termsAndConditionsResponse = httpClient
        .url(resource("/preferences/terms-and-conditions"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"generic":{"accepted":true,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, "email":"$testEmail","language": "en"}"""
          )
        )
        .futureValue
      termsAndConditionsResponse.status must be(CREATED)

      private val optOutResponse = httpClient
        .url(resource(s"/entity-resolver-admin/manual-opt-out/$nino/$randomNino"))
        .withHttpHeaders(authHeader)
        .post(Json.obj())
        .futureValue
      optOutResponse.status must be(OK)

      private val response = httpClient
        .url(resource("/preferences"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status must be(OK)
      (response.json \ "termsAndConditions" \ "generic" \ "accepted") must be(JsDefined(JsBoolean(false)))
    }

    "return NOT_FOUND to opt ct-utr out" in new TestCaseWithCtUtrOnly {
      stubEntityResolverTaxIdUtrNino()
      private val response = httpClient
        .url(resource(s"/entity-resolver-admin/manual-opt-out/$ctutr/$randomCtUtr"))
        .post(Json.obj())
        .futureValue
      response.status must be(NOT_FOUND)
    }

    "return PRECONDITION_FAILED if the UTR is not present in entity" in new TestCaseWithUtrOnly {
      private val response =
        httpClient.url(resource(s"/entity-resolver-admin/manual-opt-out/$sa/$randomUtr")).post(Json.obj()).futureValue
      response.status must be(PRECONDITION_FAILED)
    }
  }
}
