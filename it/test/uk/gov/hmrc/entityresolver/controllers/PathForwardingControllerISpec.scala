/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.entityresolver.controllers

import play.api.http.Status.{ BAD_REQUEST, CREATED, NOT_FOUND }
import play.api.libs.json.Json
import uk.gov.hmrc.entityresolver.ControllerISpec
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.libs.ws.DefaultBodyReadables.readableAsString
import uk.gov.hmrc.entityresolver.EndpointSupport.testEmail
import uk.gov.hmrc.entityresolver.util.WithWireMock

class PathForwardingControllerISpec extends ControllerISpec with WithWireMock {

  "invalid mapping" should {
    "return not found" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)

      private val response = httpClient
        .url(resource("/preferences/opt1n"))
        .withHttpHeaders(authHeader)
        .post(Json.parse(s"""{"language": "en"}"""))
        .futureValue
      response.status must be(NOT_FOUND)
    }
  }

  "optin & email-language path mappings" should {

    "check there is a valid optin path mapping" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)

      private val response = httpClient
        .url(resource("/preferences/optin"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{
               |"generic":{"accepted":true,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, 
               |"email":"$testEmail",
               |"language": "en"
               |}""".stripMargin
          )
        )
        .futureValue
      response.status must be(CREATED)
    }

    "check there is a valid email-language path mapping" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)

      private val response = httpClient
        .url(resource("/preferences/email-language"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"language": "en"}"""
          )
        )
        .futureValue

      // This is the expected response for set-language if there are no emails in the preference
      response.status must be(BAD_REQUEST)
      response.body must be("""{"reason":"Unable to update language"}""")
    }
  }

  "regime optin optout path mappings" should {

    "check there is a valid optin path mapping" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)

      private val response = httpClient
        .url(resource("/preferences/regime/optin"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{
               |"generic":{"accepted":true,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, 
               |"email":"$testEmail",
               |"language": "en"
               |}""".stripMargin
          )
        )
        .futureValue
      response.status must be(CREATED)
    }

    "check there is a valid optout path mapping" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)

      private val response = httpClient
        .url(resource("/preferences/regime/optout"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"generic":{"accepted":false,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, "language": "en"}"""
          )
        )
        .futureValue
      response.status must be(CREATED)
    }

    "check there is a valid email-language path mapping" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)

      private val response = httpClient
        .url(resource("/preferences/regime/email-language"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"language": "en"}"""
          )
        )
        .futureValue

      // This is the expected response for set-language if there are no emails in the preference
      response.status must be(BAD_REQUEST)
      response.body must be("""{"reason":"Unable to update language"}""")
    }
  }
}
