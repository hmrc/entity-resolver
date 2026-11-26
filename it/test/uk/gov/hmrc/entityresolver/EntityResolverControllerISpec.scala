/*
 * Copyright 2019 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver

import org.apache.pekko.stream.Materializer
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ToSingleObservablePublisher
import org.scalatest.concurrent.ScalaFutures
import play.api.libs.json.*
import play.api.test.Helpers.*
import play.api.Configuration
import uk.gov.hmrc.domain.*
import uk.gov.hmrc.entityresolver.models.*
import uk.gov.hmrc.entityresolver.util.{ AuthUtil, WithWireMock }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.Generate
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.mvc.Result
import play.api.test.FakeRequest

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

class EntityResolverControllerISpec extends ControllerISpec with WithWireMock with ScalaFutures {

  import EndpointSupport._

  private val runModeConfiguration = app.injector.instanceOf[Configuration]

  val servicesConfig = new ServicesConfig(runModeConfiguration)

  override def beforeEach(): Unit = {
    super.beforeEach()
    prepareDatabase()
  }

  "utr based requests" should {
    "return UNAUTHORIZED if auth returns 401" in {
      // Auth connector for auth test needs an actual bearer token
      pending

      val result = httpClient
        .url(resource("/preferences/terms-and-conditions"))
        .post(Json.parse(s"""{"generic":{"accepted":true}, "email": "$testEmail"}"""))
        .futureValue
      result.status must be(UNAUTHORIZED)
    }

    "return NOT_FOUND with reason when there is no entity" in new TestCaseWithUtrAndNino {
      private val authHeader = getAuthHeader(authLoginUrl)
      private val response = httpClient
        .url(resource("/preferences"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status must be(NOT_FOUND)
      response.json \ "reason" must be(JsDefined(JsString("PREFERENCES_NOT_FOUND")))
    }

    "be mapped to the correct entityId endpoints with the request body" in new TestCaseWithUtrAndNino {
      stubEntityResolverTaxIdUtrNino()
      private val authHeader = getAuthHeader(authLoginUrl)
      private val initialPreferenceResponse = httpClient
        .url(resource("/preferences"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      initialPreferenceResponse.status must be(NOT_FOUND)

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

      private val entityIdResponseForNino = httpClient
        .url(resource(s"/entity-resolver/paye/$randomNino"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      entityIdResponseForNino.status mustBe OK
      (entityIdResponseForNino.json \ "nino").get mustBe JsString(randomNino.value)

      private val entityIdResponse = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=$randomUtr"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      entityIdResponse.status mustBe OK
      (entityIdResponse.json \ "sautr").get mustBe JsString(randomUtr.value)

      entityIdResponseForNino.json mustBe entityIdResponse.json

      private val response = httpClient
        .url(resource("/preferences"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      response.status mustBe OK
      (response.json \ "digital").get mustBe JsBoolean(true)
      (response.json \ "email" \ "email").get mustBe JsString(testEmail)
    }

  }

  "nino based requests" should {
    "be mapped to the correct entityId endpoints with the request body" in new TestCaseWithNinoOnly {
      stubEntityResolverTaxIdNino()

      implicit val authHeader: (String, String) = getAuthHeader(authLoginUrl)
      private val updateTandCResponse = httpClient
        .url(resource("/preferences/terms-and-conditions"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"generic":{"accepted":true,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, "email":"$testEmail","language": "en"}"""
          )
        )
        .futureValue
      updateTandCResponse.status must be(CREATED)

      private val entityResponse = httpClient
        .url(resource(s"/entity-resolver/paye/$randomNino"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      private val entity = entityResponse.json.as[Entity]
      private val verificationToken = verificationTokenFor(entity.id)

      private val emailPreferencesResponse = httpClient
        .url(resource("/portal/preferences/email"))
        .withHttpHeaders(authHeader)
        .put(Json.parse(s"""{"token":"$verificationToken"}"""))
        .futureValue

      emailPreferencesResponse.status must be(NO_CONTENT)
    }

    "get preferences for a UTR where a NINO already exists for that user and add UTR to the tax ids in entity-resolver" in new TestCaseWithNinoOnly {
      stubEntityResolverTaxIdNino()

      private val authHeader = getAuthHeader(authLoginUrl)
      private val futureTermsAndConditions = httpClient
        .url(resource("/preferences/terms-and-conditions"))
        .withHttpHeaders(authHeader)
        .post(
          Json.parse(
            s"""{"generic":{"accepted":true,"optInPage":{"version": {"major":2,"minor":1}, "cohort":1, "pageType":"IPage"}}, "email":"$testEmail","language": "en"}"""
          )
        )
        .futureValue
      futureTermsAndConditions.status must be(CREATED)

      private val entityResponse1 = httpClient.url(resource(s"/entity-resolver/paye/$randomNino")).get().futureValue
      private val utr = Generate.utr
      private val preferencesResponse = httpClient
        .url(resource("/preferences"))
        .withHttpHeaders(AuthUtil.buildNinoSaUserToken(randomNino, utr, authLoginUrl))
        .get()
        .futureValue
      preferencesResponse.status must be(OK)

      private val entityResponse2 = httpClient
        .url(resource(s"/entity-resolver?taxRegime=sa&taxId=$utr"))
        .withHttpHeaders(authHeader)
        .get()
        .futureValue
      private val entityId1 = entityResponse1.json.as[Entity].id
      private val entityId2 = entityResponse2.json.as[Entity].id
      entityId2.value must be(entityId1.value)
    }
  }

  "find entity by auth" should {
    implicit val materializer: Materializer = Materializer(system)
    implicit val executionContext: ExecutionContext = materializer.executionContext

    "unresolved" should {

      "be OK normal conditions" in new TestCaseWithUtrAndNino {
        val entity = Entity(EntityId.generate(), Set(randomNino, randomUtr))
        entityRepository.confirm(entity).futureValue

        private val authHeader = getAuthHeader(authLoginUrl)
        val request = FakeRequest(GET, s"/entity-resolver?resolve=false").withHeaders(authHeader)
        val result: Result = route(app, request).get.futureValue
        result.header.status mustBe OK
      }

      "cause a CONFLICT with 2 entities with the ID belonging to the same person" in new TestCaseWithUtrAndNino {
        val inserted1 =
          entityRepository.collection
            .insertOne(Entity(EntityId.generate(), Set(randomNino)))
            .toFuture()
            .futureValue

        val inserted2 =
          entityRepository.collection
            .insertOne(Entity(EntityId.generate(), Set(randomUtr)))
            .toFuture()
            .futureValue

        private val authHeader = getAuthHeader(authLoginUrl)
        val request = FakeRequest(GET, s"/entity-resolver?resolve=false").withHeaders(authHeader)
        val result: Result = route(app, request).get.futureValue
        result.header.status mustBe CONFLICT
      }
    }

    "resolved" should {
      "resolve successfully when 2 entities with the ID belonging to the same person" in new TestCaseWithUtrAndNino {
        val inserted1 =
          entityRepository.collection
            .insertOne(Entity(EntityId.generate(), Set(randomNino)))
            .toFuture()
            .futureValue

        val inserted2 =
          entityRepository.collection
            .insertOne(Entity(EntityId.generate(), Set(randomUtr)))
            .toFuture()
            .futureValue

        private val authHeader = getAuthHeader(authLoginUrl)
        val request = FakeRequest(GET, s"/entity-resolver?resolve=true").withHeaders(authHeader)
        val result: Result = route(app, request).get.futureValue
        result.header.status mustBe OK
      }
    }

    "regime/itsa tests" should {

      "use resolveIds flag to return a new entity" in new TestCaseWithNinoOnly {
        val entityId: EntityId = Generate.entityId
        private val authHeader = getAuthHeader(authLoginUrl)

        val request = FakeRequest(GET, s"/regime/entity-resolver").withHeaders(authHeader)
        val response: Result = route(app, request).get.futureValue

        response.header.status must be(OK)
        val json: JsValue = response.body.consumeData.map(a => Json.parse(a.utf8String)).futureValue
        val nino = (json \ "nino").as[String]

        val e = entityRepository.collection.find().toSingle().toFuture().futureValue

        e.getNino.value must be(nino)
      }
    }
  }
}
