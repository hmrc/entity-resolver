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

import play.api.libs.ws.WSResponse
import uk.gov.hmrc.entityresolver.ControllerISpec
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ToSingleObservablePublisher
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.entityresolver.util.WithWireMock
import play.api.libs.ws.DefaultBodyReadables.readableAsString
import play.api.http.Status.{ BAD_REQUEST, NOT_FOUND, OK }
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import utils.Generate

class EntityControllerISpec extends ControllerISpec with WithWireMock with BeforeAndAfterEach {

  override def beforeEach(): Unit = prepareDatabase()

  "entity controller" should {

    "return an entity given an entityid" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId

      entityRepository.collection
        .insertOne(Entity(entityId, Set(randomNino)))
        .toFuture()
        .futureValue

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?entityId=${entityId.value}"))
        .get()
        .futureValue

      response.status must be(OK)
    }

    "return an entity given a nino" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId
      entityRepository.collection
        .insertOne(Entity(entityId, Set(randomNino)))
        .toFuture()
        .futureValue

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=paye&taxId=${randomNino.value}"))
        .get()
        .futureValue

      response.status must be(OK)
      (response.json \ "nino").as[String] must be(randomNino.value)
    }

    "return an entity given an sautr" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId
      entityRepository.collection
        .insertOne(Entity(entityId, Set(randomUtr)))
        .toFuture()
        .futureValue

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=${randomUtr.value}"))
        .get()
        .futureValue

      response.status must be(OK)
      (response.json \ "sautr").as[String] must be(randomUtr.value)
    }

    "return an entity given an itsa" in new TestCaseWithItsaAndNino {
      val entityId: EntityId = Generate.entityId
      entityRepository.collection
        .insertOne(Entity(entityId, Set(randomItsa)))
        .toFuture()
        .futureValue

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=itsa&taxId=${randomItsa.value}"))
        .get()
        .futureValue

      response.status must be(OK)
      (response.json \ "HMRC-MTD-IT").as[String] must be(randomItsa.value)
    }

    "return an entity given an auth header containing a nino and sautr" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId
      entityRepository.collection
        .insertOne(Entity(entityId, Set(randomUtr, randomNino)))
        .toFuture()
        .futureValue

      private val authHeader = getAuthHeader(authLoginUrl)

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue

      response.status must be(OK)
      (response.json \ "nino").as[String] must be(randomNino.value)
      (response.json \ "sautr").as[String] must be(randomUtr.value)
    }

    // IMPORTANT NOTE: The nino returned from these tests that create a new entity, will
    // not be the same as those set in the auth header (randomNino). This is because the
    // TaxIdentifierRetriever looks up a nino in auth service.
    "use resolveIds flag to return a new entity given an auth header containing a nino and sautr" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId
      private val authHeader = getAuthHeader(authLoginUrl)

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?resolve=true&resolveIds=true"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue

      response.status must be(OK)
      val nino = (response.json \ "nino").as[String]
      val sautr = (response.json \ "sautr").as[String]

      val e = entityRepository.collection.find().toSingle().toFuture().futureValue

      e.getNino.value mustBe nino
      e.getSaUtr.value mustBe sautr
    }

    "use resolveIds flag to return a new entity with create index which still doesn't create an index because theres a utr" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId
      private val authHeader = getAuthHeader(authLoginUrl)

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?resolve=true&resolveIds=true"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue

      response.status must be(OK)
      val nino = (response.json \ "nino").as[String]
      val sautr = (response.json \ "sautr").as[String]

      val e = entityRepository.collection.find().toSingle().toFuture().futureValue

      e.getNino.value mustBe nino
      e.getSaUtr.value mustBe sautr
    }

    "return not found if resolveIds flag unset and no entity exists for an auth header containing a nino and sautr" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId
      private val authHeader = getAuthHeader(authLoginUrl)

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue

      response.status must be(NOT_FOUND)
    }

    "return an entity given an auth header containing a nino and sautr bypassing resolution" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId
      entityRepository.collection
        .insertOne(Entity(entityId, Set(randomUtr, randomNino)))
        .toFuture()
        .futureValue

      private val authHeader = getAuthHeader(authLoginUrl)

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?resolve=false"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue

      response.status must be(OK)
      (response.json \ "nino").as[String] must be(randomNino.value)
      (response.json \ "sautr").as[String] must be(randomUtr.value)
    }

    "reject an invalid tax regime" in new TestCaseWithUtrAndNino {
      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=badtaxreg&taxId=1234")) // invalid, requires a value
        .get()
        .futureValue

      response.status must be(BAD_REQUEST)
      response.body must be("Invalid tax regime: badtaxreg")
    }

    "reject an invalid querystring" in new TestCaseWithUtrAndNino {
      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa")) // invalid, requires a value
        .get()
        .futureValue

      response.status must be(BAD_REQUEST)
      response.body must be("Invalid querystring format")
    }

    "return not found for an invalid entityid" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?entityId=${entityId.value}"))
        .get()
        .futureValue

      response.status must be(NOT_FOUND)
    }

    "return an error given both sautr and entityId" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId
      entityRepository.collection
        .insertOne(Entity(entityId, Set(randomUtr)))
        .toFuture()
        .futureValue

      val response: WSResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=${randomUtr.value}&entityId=${entityId.value}"))
        .get()
        .futureValue

      response.status must be(BAD_REQUEST)
      response.body must be("Invalid querystring format")
    }

    "ignore a duplicated querystring key" in new TestCaseWithUtrAndNino {
      val entityId: EntityId = Generate.entityId
      entityRepository.collection
        .insertOne(Entity(entityId, Set(randomNino)))
        .toFuture()
        .futureValue

      // only a Nino is in the database, but the first querystring key is used for the query and therefore fails.
      val response: WSResponse = httpClient
        .url(
          resource(
            s"/entity-resolver?taxRegime=sa&taxId=${randomUtr.value}&taxRegime=paye&taxId=${randomNino.value}"
          )
        )
        .get()
        .futureValue

      response.status must be(NOT_FOUND)
    }
  }
}
