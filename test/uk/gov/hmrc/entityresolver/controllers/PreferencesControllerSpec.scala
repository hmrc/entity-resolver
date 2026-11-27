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

package uk.gov.hmrc.entityresolver.controllers

import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{ any, anyBoolean, anyString, contains, eq as argEq }
import org.mockito.Mockito.{ doNothing, verify, when }
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.Results.{ NotFound, Ok }
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{ Retrieval, ~ }
import uk.gov.hmrc.auth.core.{ AuthConnector, UnsupportedAffinityGroup }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr }
import uk.gov.hmrc.entityresolver.connectors.{ EnrolmentStoreProxyConnector, PreferencesConnector, TaxIdentifierRetriever }
import uk.gov.hmrc.entityresolver.models.{ EnrolmentKey, Entity, EntityId, PrincipalUserIdResponse }
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.entityresolver.services.PathMappingOperations
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import utils.Generate

import scala.concurrent.{ ExecutionContext, Future }

class PreferencesControllerSpec extends PlaySpec with GuiceOneAppPerTest with ScalaFutures with MockitoSugar {

  "PreferencesController.capture" should {

    "return OK when there is one entityId exists for the given taxIdentifiers" in new CaptureTestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr, nino)
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(currentTaxIds))
      when(mockRepo.findByAny(any[Set[TaxIdWithName]])).thenReturn(Future.successful(List(entityWithNino1)))
      when(preferencesConnectorMock.getPreferencesStatus(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Ok))

      private val result = call(controller.capture(), FakeRequest())
      status(result) mustBe Status.OK
    }

    "return CONFLICT when more than one entityId exists for the given taxIdentifiers" in new CaptureTestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr, nino)
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(currentTaxIds))
      when(mockRepo.findByAny(any[Set[TaxIdWithName]]))
        .thenReturn(Future.successful(List(entityWithNino1, entityWithNino2)))

      private val result = call(controller.capture(), FakeRequest())
      status(result) mustBe Status.CONFLICT
    }

    "return NOT_FOUND when there is no entityId exists for the given taxIdentifiers" in new CaptureTestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr, nino)
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(currentTaxIds))
      when(mockRepo.findByAny(any[Set[TaxIdWithName]])).thenReturn(Future.successful(List.empty[Entity]))

      private val result = call(controller.capture(), FakeRequest())
      status(result) mustBe Status.NOT_FOUND
    }

    "return NOT_FOUND when there is an entityId exists, but no preference record found for the given taxIdentifiers" in new CaptureTestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr, nino)
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(currentTaxIds))
      when(mockRepo.findByAny(any[Set[TaxIdWithName]])).thenReturn(Future.successful(List(entityWithNino1)))
      when(preferencesConnectorMock.getPreferencesStatus(any[EntityId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(NotFound))

      private val result = call(controller.capture(), FakeRequest())
      status(result) mustBe Status.NOT_FOUND
    }
  }

  // See the case definition on https://jira.tools.tax.service.gov.uk/browse/DC-3504
  "PreferencesController.confirm" should {
    "Case 1.1: return OK (200) upon linking the given itsaId when the SAUTR in authToken is the same as SAUTR in entity-resolver" in new ConfirmTestCase {
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(retrievedEntity.copy(taxIdentifiers = Set(authTokenSaUtr)))))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe OK
      contentAsString(
        result
      ) mustBe """{"reason":"ITSA ID successfully added","preference":{"digital":true,"email":{"hasBounces":false,"isVerified":true,"email":"pihklyljtgoxeoh@mail.com"}}}"""
    }

    "Case 1.2: return UNAUTHORIZED (401) when SAUTR in authToken is different from SAUTR in entity-resolver" in new ConfirmTestCase {
      val differentAuthTokenSaUtr = SaUtr("different-auth-token")
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(
        Future.successful(Some(retrievedEntity.copy(taxIdentifiers = Set(differentAuthTokenSaUtr, authTokenNino))))
      )

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe UNAUTHORIZED
      contentAsString(
        result
      ) mustBe """{"reason":"SAUTR in Auth token is linked to a different entityId in entity resolver"}"""
    }

    "Case 1.3: return UNAUTHORIZED (401) when SAUTR in authToken is linked to a different entityId in entity-resolver" in new ConfirmTestCase {
      val differentEntityId = EntityId("different-entity-id")

      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(retrievedEntity.copy(taxIdentifiers = Set(authTokenNino, authTokenSaUtr)))))

      when(
        mockRepo.findBy(any[SaUtr])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(Entity(differentEntityId, authTokenNino))))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe UNAUTHORIZED
      contentAsString(
        result
      ) mustBe """{"reason":"SAUTR in Auth token is linked to a different entityId in entity resolver"}"""
    }

    "Case 1.4: return OK (200) upon linking the given itsaId when SAUTR does not exist in the Auth token" in new ConfirmTestCase {
      when(
        authConnector.authorise[Option[String] ~ Option[String]](
          any[Predicate](),
          any[Retrieval[Option[String] ~ Option[String]]]()
        )(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(Future.successful(new ~(None, Option(authTokenNino.value))))
      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe OK
      contentAsString(
        result
      ) mustBe """{"reason":"ITSA ID successfully added","preference":{"digital":true,"email":{"hasBounces":false,"isVerified":true,"email":"pihklyljtgoxeoh@mail.com"}}}"""

    }

    "Case 1.5: return UNAUTHORIZED (401) when entityId already has a different itsaId linked to it in entity resolver" in new ConfirmTestCase {
      val differentItsaId: HmrcMtdItsa = HmrcMtdItsa("XTIT00000003773")
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(retrievedEntity.copy(taxIdentifiers = Set(differentItsaId)))))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe UNAUTHORIZED
      contentAsString(
        result
      ) mustBe """{"reason":"entityId already has a different itsaId linked to it in entity resolver"}"""
    }

    "Case 1.6: return UNAUTHORIZED (401) when Nino in authToken is linked to a different entityId in entity-resolver".stripMargin in new ConfirmTestCase {

      val differentEntityId = "differentEntityId"

      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(retrievedEntity.copy(taxIdentifiers = Set(authTokenNino, authTokenSaUtr)))))

      when(
        mockRepo.findBy(any[HmrcMtdItsa])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(Entity(EntityId(differentEntityId), authTokenNino))))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe UNAUTHORIZED
      contentAsString(
        result
      ) mustBe """{"reason":"SAUTR in Auth token is linked to a different entityId in entity resolver"}"""
    }

    "Case 1.7: return UNAUTHORIZED (401) when NINO in Auth token is different from  NINO in entity resolver" in new ConfirmTestCase {

      val differentNino = Nino("AB003433A")
      when(
        authConnector.authorise[Option[String] ~ Option[String]](
          any[Predicate](),
          any[Retrieval[Option[String] ~ Option[String]]]()
        )(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(Future.successful(new ~(Option(authTokenSaUtr.value), Option(authTokenNino.value))))

      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(retrievedEntity.copy(taxIdentifiers = Set(authTokenSaUtr, differentNino)))))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) mustBe """{"reason":"NINO in Auth token is different from  NINO in entity resolver"}"""
    }

    "Case 1.8: return UNAUTHORIZED (401) SAUTR does not exist in the Auth token but exists in Entity Resolver & auth nino does not match with entity resolver" in new ConfirmTestCase {
      val differentNino = Nino("AB003433A")
      when(
        authConnector.authorise[Option[String] ~ Option[String]](
          any[Predicate](),
          any[Retrieval[Option[String] ~ Option[String]]]()
        )(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(Future.successful(new ~(None, Option(authTokenNino.value))))

      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(retrievedEntity.copy(taxIdentifiers = Set(authTokenSaUtr, differentNino)))))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe UNAUTHORIZED
      contentAsString(
        result
      ) mustBe """{"reason":"SAUTR does not exist in the Auth token but exists in Entity Resolver & auth nino doesn't match with entity nino"}"""
    }

    "Case 1.8: return OK (200) SAUTR does not exist in the Auth token but exists in Entity Resolver & auth nino matches with entity resolver" in new ConfirmTestCase {
      when(
        authConnector.authorise[Option[String] ~ Option[String]](
          any[Predicate](),
          any[Retrieval[Option[String] ~ Option[String]]]()
        )(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(Future.successful(new ~(None, Option(authTokenNino.value))))

      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(retrievedEntity.copy(taxIdentifiers = Set(authTokenSaUtr)))))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe OK
      contentAsString(
        result
      ) mustBe """{"reason":"ITSA ID successfully added","preference":{"digital":true,"email":{"hasBounces":false,"isVerified":true,"email":"pihklyljtgoxeoh@mail.com"}}}"""

    }

    "Case 1.8: return UNAUTHORIZED (401) SAUTR does not exist in the Entity resolver but exists in Auth token & auth nino doesn't match with entity nino" in new ConfirmTestCase {
      val differentNino = Nino("AB003433A")

      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(retrievedEntity.copy(taxIdentifiers = Set(differentNino)))))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe UNAUTHORIZED
      contentAsString(
        result
      ) mustBe """{"reason":"SAUTR does not exist in the Entity resolver but exists in Auth token & auth nino doesn't match with entity nino"}"""
    }

    "Case 1.8: return OK (200) SAUTR does not exist in the Entity resolver but exists in Auth token & auth nino matches with entity nino" in new ConfirmTestCase {
      when(
        authConnector.authorise[Option[String] ~ Option[String]](
          any[Predicate](),
          any[Retrieval[Option[String] ~ Option[String]]]()
        )(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(Future.successful(new ~(None, Option(authTokenNino.value))))

      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(retrievedEntity)))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe OK
      contentAsString(
        result
      ) mustBe """{"reason":"ITSA ID successfully added","preference":{"digital":true,"email":{"hasBounces":false,"isVerified":true,"email":"pihklyljtgoxeoh@mail.com"}}}"""

    }

    "Case 1.9: return UNAUTHORIZED (401) NINO does not exist in the Auth token but exists in Entity Resolver" in new ConfirmTestCase {
      when(
        authConnector.authorise[Option[String] ~ Option[String]](
          any[Predicate](),
          any[Retrieval[Option[String] ~ Option[String]]]()
        )(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(Future.successful(new ~(Option(authTokenSaUtr.value), None)))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe UNAUTHORIZED
      contentAsString(
        result
      ) mustBe """{"reason":"NINO does not exist in the Auth token but exists in Entity Resolver"}"""
    }

    "Case 3.1: return NOT_FOUND (404) when the entityId passed back does not exist in entity resolver" in new ConfirmTestCase {
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(None))

      val result = call(controller.confirm(entityId, itsaId), FakeRequest())
      status(result) mustBe NOT_FOUND
      contentAsString(result) mustBe """{"reason":"Invalid entity id or entity id has expired"}"""
    }
  }

  "PreferencesController.checkAndDelete" should {

    "return 'UNSET_MARK_DE_ENROLMENT' for the given identifier 'sautr' and the given entity has both itsa and sautr" in new CheckAndDeleteCase {
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(entityWIthSaAndItsa)))

      private val result = call(controller.checkAndDelete(Generate.entityId, "sautr"), FakeRequest())
      status(result) mustBe Status.OK
      contentAsString(result) mustBe """{"reason":"UNSET_MARK_DE_ENROLMENT"}"""
    }

    "return 'UNSET_MARK_DE_ENROLMENT' for the given identifier 'HMRC-MTD-IT' and the given entity has both itsa and sautr" in new CheckAndDeleteCase {
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(entityWIthSaAndItsa)))

      private val result = call(controller.checkAndDelete(Generate.entityId, "HMRC-MTD-IT"), FakeRequest())
      status(result) mustBe Status.OK
      contentAsString(result) mustBe """{"reason":"UNSET_MARK_DE_ENROLMENT"}"""
    }

    "return 'DELETE_PREFERENCES' for the given identifier 'HMRC-MTD-IT' and the given entity has just itsa and nino" in new CheckAndDeleteCase {
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(entityWIthItsaAndNino)))

      private val result = call(controller.checkAndDelete(Generate.entityId, "HMRC-MTD-IT"), FakeRequest())
      status(result) mustBe Status.OK
      contentAsString(result) mustBe """{"reason":"DELETE_PREFERENCES"}"""
    }

    "return 'DELETE_PREFERENCES' for the given identifier 'sautr' and the given entity has just sautr and nino" in new CheckAndDeleteCase {
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(entityWIthSaAndNino)))

      private val result = call(controller.checkAndDelete(Generate.entityId, "sautr"), FakeRequest())
      status(result) mustBe Status.OK
      contentAsString(result) mustBe """{"reason":"DELETE_PREFERENCES"}"""
    }
    "return 'DELETE_PREFERENCES' for the unknown identifier 'test'" in new CheckAndDeleteCase {
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(entityWIthSaAndNino)))

      private val result = call(controller.checkAndDelete(Generate.entityId, "test"), FakeRequest())
      status(result) mustBe Status.OK
      contentAsString(result) mustBe """{"reason":"DELETE_PREFERENCES"}"""
    }
    "return 'INVALID_ENTITY' for the wrong entity id" in new CheckAndDeleteCase {
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.successful(None))

      private val result = call(controller.checkAndDelete(Generate.entityId, "any"), FakeRequest())
      status(result) mustBe Status.NOT_FOUND
      contentAsString(result) mustBe """{"reason":"INVALID_ENTITY"}"""
    }
    "return 'INTERNAl_ERROR' for any errors" in new CheckAndDeleteCase {
      when(
        mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
      ).thenReturn(Future.failed(new RuntimeException("")))

      private val result = call(controller.checkAndDelete(Generate.entityId, "test"), FakeRequest())
      status(result) mustBe Status.BAD_REQUEST
      contentAsString(result) mustBe """{"reason":"INTERNAl_ERROR"}"""
    }
  }

  // See the case definition on https://jira.tools.tax.service.gov.uk/browse/DC-3521
  "PreferencesController.enrolment" should {
    "Case 1.1: Check Agents Auth Token" in new EnrolmentTestCase {
      when(
        authConnector
          .authorise[Unit](any[Predicate](), any[Retrieval[Unit]]())(any[HeaderCarrier](), any[ExecutionContext]())
      )
        .thenReturn(Future.failed(UnsupportedAffinityGroup()))

      val request = FakeRequest().withJsonBody(enrolments)
      val result = call(controller.enrolment(), request)
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) mustBe """{"reason":"Agent not Authorised"}"""
    }

    "Case 1.2: Check Agents ID" in new EnrolmentTestCase {

      val badEnrolments =
        enrolments - "arn" ++ Json.obj("arn" -> "Bad ARN")

      val request = FakeRequest().withJsonBody(badEnrolments)
      val result = call(controller.enrolment(), request)
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) mustBe """{"reason":"Agent not Authorised"}"""
    }

    "Scenario 2 Check that mandatory parameters are passed." in new EnrolmentTestCase {
      val missingParameterEnrolment = enrolments - "arn"
      val request = FakeRequest().withJsonBody(missingParameterEnrolment)
      val result = call(controller.enrolment(), request)
      status(result) mustBe BAD_REQUEST
      contentAsString(result) must include("Invalid")
    }

    "Case 3.1: The NINO and SA-UTR are not linked to the same EntityId in entity resolver" in new EnrolmentTestCase {
      val entityId1 = EntityId("faa93b10-5174-49fc-816a-49ae025e0b2f")
      val differentEntity = Entity(entityId1, nino)
      when(
        mockRepo.findBy(argEq(nino))(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(differentEntity)))

      val request = FakeRequest().withJsonBody(enrolments)
      val result = call(controller.enrolment(), request)
      status(result) mustBe UNAUTHORIZED
      contentAsString(
        result
      ) mustBe """{"reason":"The NINO and SAUTR are not linked to the same EntityId in entity resolver"}"""
    }

    "Case 3.3: The IR-SA enrolment exists in EACD" in new EnrolmentTestCase {

      when(
        enrolmentStoreProxyConnector.getCredentialsId(argEq(utrEnrolmentKey))(any[HeaderCarrier])
      ).thenReturn(Future.successful(Right(None)))

      val request = FakeRequest().withJsonBody(enrolments)
      val result = call(controller.enrolment(), request)
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) mustBe """{"reason":"IR-SA Identifier not found"}"""
    }

    "Case 3.4: The MTD-IT enrolment exists in EACD" in new EnrolmentTestCase {

      when(
        enrolmentStoreProxyConnector.getCredentialsId(argEq(itsaEnrolmentKey))(any[HeaderCarrier])
      ).thenReturn(Future.successful(Right(None)))

      val request = FakeRequest().withJsonBody(enrolments)
      val result = call(controller.enrolment(), request)
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) mustBe """{"reason":"MTD_IT Identifier not found"}"""
    }

    "Case 3.5: The user Id in GG must be the same for the IR-SA and the MTD-IT enrolment" in new EnrolmentTestCase {

      val differentPrincipalUserIdResponse = PrincipalUserIdResponse(List("differentUserID"))
      when(
        enrolmentStoreProxyConnector.getCredentialsId(argEq(itsaEnrolmentKey))(any[HeaderCarrier])
      ).thenReturn(Future.successful(Right(Some(differentPrincipalUserIdResponse))))

      val request = FakeRequest().withJsonBody(enrolments)
      val result = call(controller.enrolment(), request)
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) mustBe """{"reason":"Agent not Authorised"}"""
    }

    "Case 3.6: MTD-IT enrolment already exists and linked to the entity ID" in new EnrolmentTestCase {
      val entityAlreadyLinkedToItsa = Entity(retrievedEntity.id, nino, itsa)
      when(
        mockRepo.findBy(argEq(nino))(any[ExecutionContext]())
      ).thenReturn(Future.successful(Some(entityAlreadyLinkedToItsa)))

      val request = FakeRequest().withJsonBody(enrolments)
      val result = call(controller.enrolment(), request)
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) mustBe """{"reason":"MTD-IT already exists"}"""
    }

    "Case 4.1: Check that the preference record exists" in new EnrolmentTestCase {

      when(
        preferencesConnector.getPreferences(any[EntityId])(any[HeaderCarrier])
      ).thenReturn(Future.successful(None))

      val request = FakeRequest().withJsonBody(enrolments)
      val result = call(controller.enrolment(), request)
      status(result) mustBe UNAUTHORIZED
      contentAsString(result) mustBe """{"reason":"No preferences found"}"""
    }

    "Case 4.2: Link entity to itsa when preference is found" in new EnrolmentTestCase {

      val request = FakeRequest().withJsonBody(enrolments)
      val result = call(controller.enrolment(), request)
      status(result) mustBe OK
      val expectedResponseBody = Json.obj("reason" -> "ITSA ID successfully added", "preference" -> preferenceResponse)
      contentAsJson(result) mustBe expectedResponseBody
    }

    "Send audit message on happy path" in new EnrolmentTestCase {
      val request = FakeRequest().withJsonBody(enrolments)
      val arg1: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      whenReady(call(controller.enrolment(), request)) { _ =>
        verify(auditConnector)
          .sendExplicitAudit(arg1.capture(), any[Map[String, String]])(any[HeaderCarrier], any[ExecutionContext]())
        arg1.getValue mustBe "AgentEnrolmentConfirmed"
      }
    }

    "Send audit message on failure" in new EnrolmentTestCase {
      val badEnrolments = enrolments - "arn" ++ Json.obj("arn" -> "Bad ARN")
      val arg1: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val arg2: ArgumentCaptor[Map[String, String]] = ArgumentCaptor.forClass(classOf[Map[String, String]])
      val request = FakeRequest().withJsonBody(badEnrolments)
      whenReady(call(controller.enrolment(), request)) { _ =>
        verify(auditConnector)
          .sendExplicitAudit(arg1.capture(), arg2.capture())(any[HeaderCarrier], any[ExecutionContext]())
        arg1.getValue mustBe "AgentEnrolmentError"
        val argMap = arg2.getValue
        argMap("errorcode") mustBe "401"
        argMap("errordescription") mustBe "Agent not Authorised"
      }
    }
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[PreferencesConnector].toInstance(mock[PreferencesConnector]))
      .overrides(bind[AuditConnector].toInstance(mock[AuditConnector]))
      .overrides(bind[EnrolmentStoreProxyConnector].toInstance(mock[EnrolmentStoreProxyConnector]))
      .overrides(bind[PathMappingOperations].toInstance(mock[PathMappingOperations]))
      .overrides(bind[TaxIdentifierRetriever].toInstance(mock[TaxIdentifierRetriever]))
      .overrides(bind[AuthConnector].toInstance(mock[AuthConnector]))
      .overrides(bind[EntityRepository].toInstance(mock[EntityRepository]))
      .configure("metrics.enabled" -> "false")
      .build()

  trait CheckAndDeleteCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockRepo: EntityRepository = app.injector.instanceOf[EntityRepository]
    val controller: PreferencesController = app.injector.instanceOf[PreferencesController]
    val authConnector: TaxIdentifierRetriever = app.injector.instanceOf[TaxIdentifierRetriever]
    val preferencesConnectorMock: PreferencesConnector = app.injector.instanceOf[PreferencesConnector]
    val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]

    implicit val mat: Materializer = app.injector.instanceOf[Materializer]

    when(
      mockRepo.updateTaxIds(any[EntityId], any[Set[TaxIdWithName]], anyBoolean())(any[ExecutionContext]())
    ).thenReturn(Future.successful(true))

    when(
      mockRepo.remove(any[EntityId])(any[ExecutionContext]())
    ).thenReturn(Future.successful(true))

    val nino = Generate.nino
    val utr = Generate.utr
    val itsa = Generate.itsa
    val entityWIthSaAndItsa = Entity(Generate.entityId, nino, utr, itsa)
    val entityWIthSaAndNino = Entity(Generate.entityId, nino, utr)
    val entityWIthItsaAndNino = Entity(Generate.entityId, nino, itsa)
  }

  trait DeleteCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockRepo: EntityRepository = app.injector.instanceOf[EntityRepository]
    val controller: PreferencesController = app.injector.instanceOf[PreferencesController]
    val authConnector: TaxIdentifierRetriever = app.injector.instanceOf[TaxIdentifierRetriever]
    val preferencesConnectorMock: PreferencesConnector = app.injector.instanceOf[PreferencesConnector]
    val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]

    implicit val mat: Materializer = app.injector.instanceOf[Materializer]

    when(
      mockRepo.remove(any[EntityId])(any[ExecutionContext]())
    ).thenReturn(Future.successful(true))

    val nino = Generate.nino
    val utr = Generate.utr
    val itsa = Generate.itsa
    val entityWithSaAndItsa = Entity(Generate.entityId, nino, utr, itsa)
    val entityWithSaAndNino = Entity(Generate.entityId, nino, utr)
    val entityWithItsaAndNino = Entity(Generate.entityId, nino, itsa)
  }

  trait CaptureTestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockRepo: EntityRepository = app.injector.instanceOf[EntityRepository]
    val controller: PreferencesController = app.injector.instanceOf[PreferencesController]
    val authConnector: TaxIdentifierRetriever = app.injector.instanceOf[TaxIdentifierRetriever]
    val preferencesConnectorMock: PreferencesConnector = app.injector.instanceOf[PreferencesConnector]
    val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]

    implicit val mat: Materializer = app.injector.instanceOf[Materializer]

    val nino = Generate.nino
    val utr = Generate.utr
    val itsa = Generate.itsa
    val entityWithNino1 = Entity(Generate.entityId, nino, utr)
    val entityWithNino2 = Entity(Generate.entityId, Generate.nino, utr)
  }

  trait ConfirmTestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val authTokenSaUtr = Generate.utr
    val authTokenNino = Generate.nino
    val itsaId = Generate.itsa
    val entityId = Generate.entityId

    val retrievedEntity = Entity(entityId, authTokenNino)
    val mockRepo: EntityRepository = app.injector.instanceOf[EntityRepository]
    val controller: PreferencesController = app.injector.instanceOf[PreferencesController]
    val authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
    val preferencesConnector: PreferencesConnector = app.injector.instanceOf[PreferencesConnector]
    val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]

    implicit val mat: Materializer = app.injector.instanceOf[Materializer]

    when(
      authConnector.authorise[Option[String] ~ Option[String]](
        any[Predicate](),
        any[Retrieval[Option[String] ~ Option[String]]]()
      )(any[HeaderCarrier](), any[ExecutionContext]())
    )
      .thenReturn(Future.successful(new ~(Option(authTokenSaUtr.value), Option(authTokenNino.value))))

    when(
      mockRepo.findBy(any[EntityId])(any[ExecutionContext]())
    ).thenReturn(Future.successful(Some(retrievedEntity)))

    when(
      mockRepo.findBy(any[HmrcMtdItsa])(any[ExecutionContext]())
    ).thenReturn(Future.successful(Some(retrievedEntity)))

    when(
      mockRepo.findBy(any[SaUtr])(any[ExecutionContext]())
    ).thenReturn(Future.successful(Some(retrievedEntity)))

    when(
      mockRepo.updateTaxIds(any[EntityId], any[Set[TaxIdWithName]], anyBoolean())(any[ExecutionContext]())
    ).thenReturn(Future.successful(true))

    val preferenceResponse: JsValue = Json.parse("""{
                                                   |    "digital": true,
                                                   |    "email": {
                                                   |        "hasBounces": false,
                                                   |        "isVerified": true,
                                                   |        "email": "pihklyljtgoxeoh@mail.com"
                                                   |    }
                                                   |}""".stripMargin)

    when(
      preferencesConnector.getPreferences(any[EntityId])(any[HeaderCarrier])
    ).thenReturn(Future.successful(Some(HttpResponse(OK, preferenceResponse, Map.empty[String, Seq[String]]))))
  }

  trait EnrolmentTestCase {
    val entityId = Generate.entityId
    val nino = Generate.nino
    val utr = Generate.utr
    val utrEnrolmentKey = EnrolmentKey("IR-SA", "SA-UTR", utr.value)
    val itsa = Generate.itsa
    val itsaEnrolmentKey = EnrolmentKey("HMRC-MTD-IT", "MTDBSA", itsa.value)
    val enrolments =
      Json.obj(
        "arn"    -> "TARN0000001",
        "itsaId" -> itsaEnrolmentKey.asString,
        "nino"   -> nino.value,
        "sautr"  -> utrEnrolmentKey.asString
      )

    val mockRepo: EntityRepository = app.injector.instanceOf[EntityRepository]
    val controller: PreferencesController = app.injector.instanceOf[PreferencesController]
    val authConnector: AuthConnector = app.injector.instanceOf[AuthConnector]
    val enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector =
      app.injector.instanceOf[EnrolmentStoreProxyConnector]
    val preferencesConnector: PreferencesConnector = app.injector.instanceOf[PreferencesConnector]
    val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]

    implicit val mat: Materializer = app.injector.instanceOf[Materializer]

    when(
      authConnector
        .authorise[Unit](any[Predicate](), any[Retrieval[Unit]]())(any[HeaderCarrier](), any[ExecutionContext]())
    )
      .thenReturn(Future.successful(()))

    val retrievedEntity = Entity(entityId, nino, utr)
    val principalUserIdResponse = PrincipalUserIdResponse(List("ABCEDEFGI1234567"))

    when(
      mockRepo.findBy(argEq(nino))(any[ExecutionContext]())
    ).thenReturn(Future.successful(Some(retrievedEntity)))

    when(
      mockRepo.findBy(argEq(utr))(any[ExecutionContext]())
    ).thenReturn(Future.successful(Some(retrievedEntity)))

    when(
      enrolmentStoreProxyConnector.getCredentialsId(any[EnrolmentKey])(any[HeaderCarrier])
    ).thenReturn(Future.successful(Right(Some(principalUserIdResponse))))

    val preferenceResponse = Json.parse("""{
                                          |    "digital": true,
                                          |    "email": {
                                          |        "status": "verified",
                                          |        "mailboxFull": false,
                                          |        "hasBounces": false,
                                          |        "isVerified": true,
                                          |        "email": "pihklyljtgoxeoh@mail.com"
                                          |    }
                                          |}""".stripMargin)
    when(
      preferencesConnector.getPreferences(any[EntityId])(any[HeaderCarrier])
    ).thenReturn(Future.successful(Some(HttpResponse(OK, preferenceResponse, Map.empty[String, Seq[String]]))))

    when(
      mockRepo.updateTaxIds(any[EntityId], any[Set[TaxIdWithName]], anyBoolean())(any[ExecutionContext]())
    ).thenReturn(Future.successful(true))

    doNothing()
      .when(
        auditConnector
      )
      .sendExplicitAudit(anyString(), any[Map[String, String]])(any[HeaderCarrier], any[ExecutionContext]())

  }

}
