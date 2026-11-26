/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.controllers

import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.{ any, eq as eqTo }
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ ControllerComponents, Result }
import play.api.test.Helpers.*
import play.api.test.{ FakeRequest, Helpers }
import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr }
import uk.gov.hmrc.entityresolver.connectors.PreferencesConnector
import uk.gov.hmrc.entityresolver.models.*
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class ItsaControllerSpec extends PlaySpec with GuiceOneAppPerTest with MockitoSugar {

  val mockRepo: EntityRepository = mock[EntityRepository]
  val auditConnector: AuditConnector = mock[AuditConnector]
  val preferencesConnector: PreferencesConnector = mock[PreferencesConnector]
  val cc: ControllerComponents = Helpers.stubControllerComponents()
  val controller = new ItsaController(mockRepo, preferencesConnector, auditConnector, cc, "entity-resolver")

  val validRequestJson: JsValue = Json.parse(
    """
      |{
      |  "nino": "SJ123456A",
      |  "sautr": "1234567890",
      |  "mtditsaid": "XAIT12345678901"
      |}
      |""".stripMargin
  )

  val itsaRequest = ItsaUpdateRequest(
    Nino("SJ123456A"),
    SaUtr("1234567890"),
    HmrcMtdItsa("XAIT12345678901")
  )

  "ItsaController.updateItsaId" should {

    "return 200 OK with success message when update is successful" in {
      val entityId = EntityId("entity-id")
      val entity = Entity(entityId, Set(Nino("SJ123456A"), SaUtr("1234567890")))

      when(mockRepo.findByAny(any())).thenReturn(Future.successful(List(entity)))
      when(mockRepo.findBy(HmrcMtdItsa("XAIT12345678901"))).thenReturn(Future.successful(None))
      when(mockRepo.updateTaxIds(any(), any(), any())(any())).thenReturn(Future.successful(true))

      val jsonBody = """{"digital":true,"verified":true,"hasBounces":false}"""
      val httpResponse = HttpResponse(status = 200, json = Json.toJson(jsonBody), headers = Map.empty)
      when(preferencesConnector.getPreferences(any())(any())).thenReturn(Future.successful(Some(httpResponse)))

      val result: Future[Result] = controller.updateItsaId()(FakeRequest().withBody(validRequestJson))

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj(
        "response"   -> "MTD ITSA ID value updated successfully",
        "preference" -> """{"digital":true,"verified":true,"hasBounces":false}"""
      )
    }

    "return 200 OK with already exists message" in {
      val entityId = EntityId("entity-id")
      val entity = Entity(entityId, Set(Nino("SJ123456A"), SaUtr("1234567890")))
      val existingEntity = entity.copy(id = entityId)

      when(mockRepo.findByAny(any())).thenReturn(Future.successful(List(entity)))
      when(mockRepo.findBy(HmrcMtdItsa("XAIT12345678901"))).thenReturn(Future.successful(Some(existingEntity)))

      val result = controller.updateItsaId()(FakeRequest().withBody(validRequestJson))

      status(result) mustBe OK
      (contentAsJson(result) \ "response").as[String] mustBe "NotModified"
    }

    "return 404 Not Found if no entity found" in {
      when(mockRepo.findByAny(any())).thenReturn(Future.successful(Nil))

      val result = controller.updateItsaId()(FakeRequest().withBody(validRequestJson))

      status(result) mustBe NOT_FOUND
      (contentAsJson(result) \ "error").as[String] mustBe "Entity not found"
    }

    "return 409 Conflict if multiple entities found" in {
      val entity1 = Entity(EntityId("id1"), Set(Nino("SJ123456A")))
      val entity2 = Entity(EntityId("id2"), Set(SaUtr("1234567890")))

      when(mockRepo.findByAny(any())).thenReturn(Future.successful(List(entity1, entity2)))

      val result = controller.updateItsaId()(FakeRequest().withBody(validRequestJson))

      status(result) mustBe CONFLICT
      (contentAsJson(result) \ "error").as[String] mustBe "Multiple records found"
    }

    "return 409 Conflict if ItsaId conflict occurs" in {
      val entity = Entity(EntityId("entity-id"), Set(Nino("SJ123456A"), SaUtr("1234567890")))
      val otherEntity = Entity(EntityId("other-id"), Set(Nino("BB987654B")))

      when(mockRepo.findByAny(any())).thenReturn(Future.successful(List(entity)))
      when(mockRepo.findBy(HmrcMtdItsa("XAIT12345678901"))).thenReturn(Future.successful(Some(otherEntity)))

      val result = controller.updateItsaId()(FakeRequest().withBody(validRequestJson))

      status(result) mustBe CONFLICT
      (contentAsJson(result) \ "error").as[String] mustBe "Invalid request"
    }

    "return 400 Bad Request for invalid JSON" in {
      val invalidJson = Json.parse("""{ "nino": "INVALID" }""")

      val result = controller.updateItsaId()(FakeRequest().withBody(invalidJson))

      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "error").as[String] mustBe "Payload validation failed"
    }

    "return 500 Internal Server Error on unexpected exception" in {
      when(mockRepo.findByAny(any())).thenReturn(Future.failed(new RuntimeException("boom")))

      val result = controller.updateItsaId()(FakeRequest().withBody(validRequestJson))

      status(result) mustBe INTERNAL_SERVER_ERROR
      (contentAsJson(result) \ "error").as[String] mustBe "Unexpected error occurred"
    }
  }
}
