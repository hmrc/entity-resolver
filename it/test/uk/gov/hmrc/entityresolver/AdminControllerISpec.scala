/*
 * Copyright 2019 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver

import play.api.libs.json._
import play.api.test.Helpers._
import uk.gov.hmrc.entityresolver.models._
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

class AdminControllerISpec extends ControllerISpec {

  "nino based requests" should {

    "when a preference does not exist POST returns CREATED and create an entity id" in new TestCaseWithNinoOnly {
      private val authHeader = getAuthHeader(authLoginUrl)
      private val responseFromPostNino = httpClient
        .url(resource(s"/test-only/entity-resolver-admin/paye/$randomNino"))
        .withHttpHeaders(authHeader)
        .post(Json.obj())
        .futureValue
      private val entityId = EntityId(responseFromPostNino.body)
      private val responseFromGetNino = httpClient
        .url(resource(s"/entity-resolver?taxRegime=paye&taxId=$randomNino"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      responseFromPostNino.status must be(CREATED)
      responseFromGetNino.status must be(OK)
      responseFromGetNino.json.as[Entity] mustBe Entity(entityId, randomNino)
    }

    "when a preference does exist POST returns OK and create an entity id" in new TestCaseWithNinoOnly {
      private val authHeader = getAuthHeader(authLoginUrl)
      await(
        httpClient
          .url(resource(s"/test-only/entity-resolver-admin/paye/$randomNino"))
          .withHttpHeaders(authHeader)
          .post(Json.obj())
      ) // create preference
      private val responseFromPostNino = httpClient
        .url(resource(s"/test-only/entity-resolver-admin/paye/$randomNino"))
        .withHttpHeaders(authHeader)
        .post(Json.obj())
        .futureValue
      private val entityId = EntityId(responseFromPostNino.body)
      private val responseFromGetNino = httpClient
        .url(resource(s"/entity-resolver/paye/$randomNino"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      responseFromPostNino.status must be(OK)
      responseFromGetNino.status must be(OK)
      responseFromGetNino.json.as[Entity] mustBe Entity(entityId, randomNino)
    }
  }

  "utr based requests" should {

    "when a preference does not exist POST returns CREATED and create an entity id" in new TestCaseWithUtrOnly {
      private val authHeader = getAuthHeader(authLoginUrl)
      private val responseFromPostNino = httpClient
        .url(resource(s"/test-only/entity-resolver-admin/sa/$randomUtr"))
        .withHttpHeaders(authHeader)
        .post(Json.obj())
        .futureValue
      private val entityId = EntityId(responseFromPostNino.body)
      private val responseFromGetNino = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=$randomUtr"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      responseFromPostNino.status must be(CREATED)
      responseFromGetNino.status must be(OK)
      responseFromGetNino.json.as[Entity] mustBe Entity(entityId, randomUtr)
    }

    "when a preference does exist POST returns OK and create an entity id" in new TestCaseWithUtrOnly {
      private val authHeader = getAuthHeader(authLoginUrl)
      await(
        httpClient
          .url(resource(s"/test-only/entity-resolver-admin/sa/$randomUtr"))
          .withHttpHeaders(authHeader)
          .post(Json.obj())
      ) // create preference
      private val responseFromPostNino = httpClient
        .url(resource(s"/test-only/entity-resolver-admin/sa/$randomUtr"))
        .withHttpHeaders(authHeader)
        .post(Json.obj())
        .futureValue
      private val entityId = EntityId(responseFromPostNino.body)
      private val responseFromGetNino = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=$randomUtr"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      responseFromPostNino.status must be(OK)
      responseFromGetNino.status must be(OK)
      responseFromGetNino.json.as[Entity] mustBe Entity(entityId, randomUtr)
    }
  }
}
