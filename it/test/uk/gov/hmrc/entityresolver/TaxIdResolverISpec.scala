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

import org.scalatest.Assertion
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.entityresolver.EndpointSupport.testEmail
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.entityresolver.util.WithWireMock
import utils.Generate
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

class TaxIdResolverISpec extends ControllerISpec with WithWireMock {

  "Entity id based requests" should {
    "return utr associated with the entityId" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      implicit val authHeader: (String, String) = getAuthHeader(authLoginUrl)
      preferenceExistsFor()
      private val entityResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=$randomUtr"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      private val entity = entityResponse.json.as[Entity]
      private val response = httpClient
        .url(resource(s"/entity-resolver?entityId=${entity.id}"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status must be(OK)
      response.json.as[Entity] must be(Entity(entity.id, randomUtr, randomNino))
    }

    "return nino associated with the entityId" in new TestCaseWithNinoOnly {
      stubEntityResolverTaxIdUtrNino()
      implicit val authHeader: (String, String) = getAuthHeader(authLoginUrl)
      preferenceExistsFor()
      private val entityResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=paye&taxId=$randomNino"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      private val entity = entityResponse.json.as[Entity]
      private val response = httpClient
        .url(resource(s"/entity-resolver?entityId=${entity.id}"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status must be(OK)
      response.json.as[Entity] must be(Entity(entity.id, randomNino))
    }

    "return all taxiIds associated with the entityId" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)
      private val preferencesResponse1 = httpClient
        .url(resource("/preferences"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      preferencesResponse1.status must be(NOT_FOUND)
      preferenceExistsFor()
      private val preferencesResponse2 = httpClient
        .url(resource("/preferences"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      preferencesResponse2.status must be(OK)
      private val entityResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=$randomUtr"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      private val entity = entityResponse.json.as[Entity]
      private val response = httpClient
        .url(resource(s"/entity-resolver?entityId=${entity.id}"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status must be(OK)
      response.json.as[Entity] must be(Entity(entity.id, randomUtr, randomNino))
    }

    "return 404 if the entity id is not found" in {
      val response = httpClient.url(resource(s"/entity-resolver/${Generate.entityId}")).get().futureValue
      response.status must be(NOT_FOUND)
    }
  }

  "SA and PAYE based requests" should {
    "return the entity information associated with the given utr" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)
      preferenceExistsFor()
      private val entityResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=$randomUtr"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      private val entity = entityResponse.json.as[Entity]
      private val response = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=$randomUtr"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status mustBe OK
      response.json.as[Entity] mustBe Entity(entity.id, randomUtr, randomNino)
    }

    "return the entity information associated with the given nino" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)
      preferenceExistsFor()
      private val entityResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=paye&taxId=$randomNino"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      private val entity = entityResponse.json.as[Entity]
      private val response = httpClient
        .url(resource(s"/entity-resolver?taxRegime=paye&taxId=$randomNino"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status mustBe OK
      response.json.as[Entity] mustBe Entity(entity.id, randomUtr, randomNino)
    }

    "return the entity information associated with the given nino that contains nino only" in new TestCaseWithNinoOnly {
      private val authHeader = getAuthHeader(authLoginUrl)
      private val entityIdResponse = httpClient
        .url(resource(s"/test-only/entity-resolver-admin/paye/$randomNino"))
        .withHttpHeaders(authHeader)
        .post(Json.obj())
        .futureValue
      private val entityId = EntityId(entityIdResponse.body)
      private val response = httpClient
        .url(resource(s"/entity-resolver?taxRegime=paye&taxId=$randomNino"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status mustBe OK
      response.json.as[Entity] mustBe Entity(entityId, randomNino)
    }

    "return the entity information associated with the given utr that contains utr only" in new TestCaseWithUtrOnly {
      private val authHeader = getAuthHeader(authLoginUrl)
      private val entityIdResponse = httpClient
        .url(resource(s"/test-only/entity-resolver-admin/sa/$randomUtr"))
        .withHttpHeaders(authHeader)
        .post(Json.obj())
        .futureValue

      private val entityId = EntityId(entityIdResponse.body)

      private val response = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=$randomUtr"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status mustBe OK
      response.json.as[Entity] mustBe Entity(entityId, randomUtr)
    }

    "return 404 if the utr is not found" in {
      val response = httpClient.url(resource(s"/entity-resolver?taxRegime=sa&taxId=${Generate.utr}")).get().futureValue
      response.status mustBe NOT_FOUND
    }

    "return 404 if the nino is not found" in {
      val response =
        httpClient.url(resource(s"/entity-resolver?taxRegime=paye&taxId=${Generate.nino}")).get().futureValue
      response.status mustBe NOT_FOUND
    }
  }

  private def preferenceExistsFor()(implicit authHeader: String => (String, String)): Assertion = {
    val response = httpClient
      .url(resource("/preferences/terms-and-conditions"))
      .withHttpHeaders(authHeader(authLoginUrl))
      .post(
        Json.parse(
          s"""{"generic":{"accepted":true,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, "email": "$testEmail","language": "en"}"""
        )
      )
      .futureValue
    response.status must be(CREATED)
  }

}
