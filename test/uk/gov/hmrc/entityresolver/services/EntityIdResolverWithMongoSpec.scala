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

package uk.gov.hmrc.entityresolver.services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.libs.json.Reads
import uk.gov.hmrc.domain._
import uk.gov.hmrc.entityresolver.connectors.PreferencesConnector
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.http.{ HeaderCarrier, HeaderNames }
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.{ EventTypes, ExtendedDataEvent }
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import utils.Generate

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }

class EntityIdResolverWithMongoSpec
    extends PlaySpec with GuiceOneAppPerTest with MockitoSugar with ScalaFutures with MongoSupport
    with IntegrationPatience {
  spec =>

  import TaxIds._

  implicit val idReads: Reads[TaxIds] = TaxIds.reads(TaxIds.defaultSerialisableIds.toSet[SerialisableTaxId])

  "When conflicts occur, entity-resolver" should {

    "do nothing if there is no conflict with utr and nino" in new TestCaseWithMongoSupport {

      private val (utr1, nino1) = (Generate.utr, Generate.nino)
      private val entityId1 = Generate.entityId

      private val entity1 = Entity(entityId1, utr1, nino1)

      entityRepository.confirm(entity1).futureValue.id mustBe entityId1

      private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(nino1, utr1)
      resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(entityId1)

      entityRepository.findByAny(Set(utr1, nino1)).futureValue must contain only entity1

      verifyNoInteractions(auditConnector)
    }

    "do nothing if there is no conflict with nino only" in new TestCaseWithMongoSupport {

      private val nino1 = Generate.nino
      private val entityId1 = Generate.entityId

      private val entity1 = Entity(entityId1, nino1)

      entityRepository.confirm(entity1).futureValue.id mustBe entityId1

      private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(nino1)
      resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(entityId1)

      entityRepository.findByAny(Set(nino1)).futureValue must contain only entity1

      verifyNoInteractions(auditConnector)
    }

    "do nothing if there is no conflict with utr only" in new TestCaseWithMongoSupport {

      private val utr1 = Generate.utr
      private val entityId1 = Generate.entityId

      private val entity1 = Entity(entityId1, utr1)

      entityRepository.confirm(entity1).futureValue.id mustBe entityId1

      private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(utr1)
      resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(entityId1)

      entityRepository.findByAny(Set(utr1)).futureValue must contain only entity1

      verifyNoInteractions(auditConnector)
    }

    "handle case 1: user logs in with [nino1, utr1], but the entity-resolver has two records, one with [utr1]," +
      " the other is [utr2, nino1]" in new TestCaseWithMongoSupport {

        private val (utr1, utr2, nino1) = (Generate.utr, Generate.utr, Generate.nino)
        private val (entityId1, entityId2) = (Generate.entityId, Generate.entityId)

        private val entity1 = Entity(entityId1, utr1)
        private val entity2 = Entity(entityId2, utr2, nino1)

        when(preferencesConnector.updated(entityId1)(hc)).thenReturn(Future.successful(true))

        entityRepository.confirm(entity1).futureValue.id mustBe entityId1
        entityRepository.confirm(entity2).futureValue.id mustBe entityId2

        private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(utr1, nino1)
        resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(entityId1)

        entityRepository.findByAny(Set(utr1, utr2, nino1)).futureValue must contain.allOf(
          entity1.copy(taxIdentifiers = Set(utr1, nino1)),
          entity2.copy(taxIdentifiers = Set(utr2))
        )

        private val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
        verify(auditConnector).sendExtendedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
        verify(preferencesConnector).updated(entityId1)(hc)

        private val dataEvent: ExtendedDataEvent = captor.getValue
        dataEvent.auditSource mustBe "test-app"
        dataEvent.auditType mustBe EventTypes.Succeeded
        dataEvent.eventId must not be empty
        dataEvent.generatedAt must not be null
        dataEvent.tags must contain("transactionName" -> "conflict")
        dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
        (dataEvent.detail \ "case").as[String] mustBe "1"
        (dataEvent.detail \ "userLoggedInAs").as[TaxIds].values must contain.allOf(nino1, utr1)
        (dataEvent.detail \ "conflictingEntities").as[Array[Entity]] must contain.allOf(entity1, entity2)
      }

    "handle case 2: user logs in with [nino1, utr1], but the entity-resolver has one record with [utr2, nino1]" in new TestCaseWithMongoSupport {

      private val (utr1, utr2, nino1) = (Generate.utr, Generate.utr, Generate.nino)
      private val entityId1 = Generate.entityId

      private val entity1 = Entity(entityId1, utr2, nino1)
      entityRepository.confirm(entity1).futureValue.id mustBe entityId1

      private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(utr1, nino1)
      private val entity2 = resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get
      entity2 must not be entityId1

      entityRepository.findByAny(Set(utr1, nino1, utr2)).futureValue must contain.allOf(
        entity1.copy(taxIdentifiers = Set(utr2)),
        Entity(entity2.id, utr1, nino1)
      )

      private val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(auditConnector).sendExtendedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])

      private val dataEvent: ExtendedDataEvent = captor.getValue
      dataEvent.auditSource mustBe "test-app"
      dataEvent.auditType mustBe EventTypes.Succeeded
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must contain("transactionName" -> "conflict")
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty

      (dataEvent.detail \ "case").as[String] mustBe "2"
      (dataEvent.detail \ "userLoggedInAs").as[TaxIds].values must contain.allOf(nino1, utr1)
      (dataEvent.detail \ "conflictingEntities").as[Array[Entity]] must contain only entity1
    }

    "handle case 3: user logs in with [nino1, utr1], but the entity-resolver has two records, one with [utr1], the other is [nino1]" in new TestCaseWithMongoSupport {

      private val (utr1, nino1) = (Generate.utr, Generate.nino)
      private val (entityId1, entityId2) = (Generate.entityId, Generate.entityId)

      private val entity1 = Entity(entityId1, utr1)
      private val entity2 = Entity(entityId2, nino1)

      when(preferencesConnector.updated(entityId1)(hc)).thenReturn(Future.successful(true))

      entityRepository.confirm(entity1).futureValue.id mustBe entityId1
      entityRepository.confirm(entity2).futureValue.id mustBe entityId2

      private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(utr1, nino1)
      resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(entityId1)

      entityRepository.findByAny(Set(utr1, nino1)).futureValue mustBe List(
        entity1.copy(taxIdentifiers = Set(utr1, nino1))
      )

      private val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(auditConnector).sendExtendedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
      verify(preferencesConnector).updated(entityId1)(hc)

      private val dataEvent: ExtendedDataEvent = captor.getValue
      dataEvent.auditSource mustBe "test-app"
      dataEvent.auditType mustBe EventTypes.Succeeded
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must contain("transactionName" -> "conflict")
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
      (dataEvent.detail \ "case").as[String] mustBe "3"
      (dataEvent.detail \ "userLoggedInAs").as[TaxIds].values must contain.allOf(nino1, utr1)
      (dataEvent.detail \ "conflictingEntities").as[Array[Entity]] must contain.allOf(entity1, entity2)
    }

    "handle case 4: user logs in with [nino1, utr1], but the entity-resolver has one records with [nino1]" in new TestCaseWithMongoSupport {

      private val (utr1, nino1) = (Generate.utr, Generate.nino)
      private val entityId1 = Generate.entityId

      private val entity1 = Entity(entityId1, nino1)

      when(preferencesConnector.updated(entityId1)(hc)).thenReturn(Future.successful(true))

      entityRepository.confirm(entity1).futureValue.id mustBe entityId1

      private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(utr1, nino1)
      resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(entityId1)

      entityRepository.findByAny(Set(utr1, nino1)).futureValue mustBe List(
        entity1.copy(taxIdentifiers = Set(utr1, nino1))
      )

      private val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(auditConnector).sendExtendedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
      verify(preferencesConnector).updated(entityId1)(hc)

      private val dataEvent: ExtendedDataEvent = captor.getValue
      dataEvent.auditSource mustBe "test-app"
      dataEvent.auditType mustBe EventTypes.Succeeded
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must contain("transactionName" -> "conflict")
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
      (dataEvent.detail \ "case").as[String] mustBe "4"
      (dataEvent.detail \ "userLoggedInAs").as[TaxIds].values must contain.allOf(nino1, utr1)
      (dataEvent.detail \ "conflictingEntities").as[Array[Entity]] must contain only entity1
    }

    "handle case 5: user logs in with [nino1, utr1], but the entity-resolver has one records with [utr1]" in new TestCaseWithMongoSupport {

      private val (utr1, nino1) = (Generate.utr, Generate.nino)
      private val entityId1 = Generate.entityId

      private val entity1 = Entity(entityId1, utr1)
      when(preferencesConnector.updated(entityId1)(hc)).thenReturn(Future.successful(true))

      entityRepository.confirm(entity1).futureValue.id mustBe entityId1

      private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(utr1, nino1)
      resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(entityId1)

      entityRepository.findByAny(Set(utr1, nino1)).futureValue mustBe List(
        entity1.copy(taxIdentifiers = Set(utr1, nino1))
      )

      private val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(auditConnector).sendExtendedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
      verify(preferencesConnector).updated(entityId1)(hc)

      private val dataEvent: ExtendedDataEvent = captor.getValue
      dataEvent.auditSource mustBe "test-app"
      dataEvent.auditType mustBe EventTypes.Succeeded
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must contain("transactionName" -> "conflict")
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
      (dataEvent.detail \ "case").as[String] mustBe "5"
      (dataEvent.detail \ "userLoggedInAs").as[TaxIds].values must contain.allOf(nino1, utr1)
      (dataEvent.detail \ "conflictingEntities").as[Array[Entity]] must contain only entity1
    }

    "handle case 6: user logs in with [nino1, utr1], but the entity-resolver has one records with [utr1, nino2]" in new TestCaseWithMongoSupport {

      private val (utr1, nino1, nino2) = (Generate.utr, Generate.nino, Generate.nino)
      private val entityId1 = Generate.entityId

      private val entity1 = Entity(entityId1, utr1, nino2)
      when(preferencesConnector.updated(entityId1)(hc)).thenReturn(Future.successful(true))

      entityRepository.confirm(entity1).futureValue.id mustBe entityId1

      private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(utr1, nino1)
      resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(entityId1)

      entityRepository.findByAny(Set(utr1, nino1, nino2)).futureValue mustBe List(
        entity1.copy(taxIdentifiers = Set(utr1, nino1))
      )

      private val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(auditConnector).sendExtendedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
      verify(preferencesConnector).updated(entityId1)(hc)

      private val dataEvent: ExtendedDataEvent = captor.getValue
      dataEvent.auditSource mustBe "test-app"
      dataEvent.auditType mustBe EventTypes.Succeeded
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must contain("transactionName" -> "conflict")
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
      (dataEvent.detail \ "case").as[String] mustBe "6"
      (dataEvent.detail \ "userLoggedInAs").as[TaxIds].values must contain.allOf(nino1, utr1)
      (dataEvent.detail \ "conflictingEntities").as[Array[Entity]] must contain only entity1
    }

    "handle case 7: user logs in with [nino1], but the entity-resolver has one records with [utr1, nino1]" in new TestCaseWithMongoSupport {

      private val (utr1, nino1) = (Generate.utr, Generate.nino)
      private val entityId1 = Generate.entityId

      private val entity1 = Entity(entityId1, utr1, nino1)
      when(preferencesConnector.updated(entityId1)(hc)).thenReturn(Future.successful(true))

      entityRepository.confirm(entity1).futureValue.id mustBe entityId1

      private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(nino1)
      resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(entityId1)

      entityRepository.findByAny(Set(utr1, nino1)).futureValue mustBe List(
        entity1.copy(taxIdentifiers = Set(utr1, nino1))
      )

      private val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(auditConnector).sendExtendedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])

      private val dataEvent: ExtendedDataEvent = captor.getValue
      dataEvent.auditSource mustBe "test-app"
      dataEvent.auditType mustBe EventTypes.Succeeded
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must contain("transactionName" -> "conflict")
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
      (dataEvent.detail \ "case").as[String] mustBe "7"
      (dataEvent.detail \ "userLoggedInAs").as[TaxIds].values must contain only nino1
      (dataEvent.detail \ "conflictingEntities").as[Array[Entity]] must contain only entity1
    }

    "handle case 8: user logs in with [utr1], but the entity-resolver has one record with [utr1, nino1]" in new TestCaseWithMongoSupport {

      private val (utr1, nino1) = (Generate.utr, Generate.nino)
      private val entityId1 = Generate.entityId

      private val entity1 = Entity(entityId1, utr1, nino1)
      when(preferencesConnector.updated(entityId1)(hc)).thenReturn(Future.successful(true))

      entityRepository.confirm(entity1).futureValue.id mustBe entityId1

      private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(utr1)
      resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(entityId1)

      entityRepository.findByAny(Set(utr1, nino1)).futureValue mustBe List(
        entity1.copy(taxIdentifiers = Set(utr1, nino1))
      )

      private val captor = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])
      verify(auditConnector).sendExtendedEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])

      private val dataEvent: ExtendedDataEvent = captor.getValue
      dataEvent.auditSource mustBe "test-app"
      dataEvent.auditType mustBe EventTypes.Succeeded
      dataEvent.eventId must not be empty
      dataEvent.generatedAt must not be null
      dataEvent.tags must contain("transactionName" -> "conflict")
      dataEvent.tags.get(HeaderNames.xRequestId) must not be empty
      (dataEvent.detail \ "case").as[String] mustBe "8"
      (dataEvent.detail \ "userLoggedInAs").as[TaxIds].values must contain only utr1
      (dataEvent.detail \ "conflictingEntities").as[Array[Entity]] must contain only entity1
    }

    "throw an exception in case the user logs in with [ctUtr and nino], but entity-resolver " +
      "has two records with [ctutr] and [nino]" in new TestCaseWithMongoSupport {

        private val ctUtr = CtUtr("testUtr")
        private val nino1 = Generate.nino

        private val ninoEntity = Entity(Generate.entityId, nino1)
        private val ctUtrEntity = Entity(Generate.entityId, ctUtr)

        entityRepository.confirm(ninoEntity).futureValue.id mustBe ninoEntity.id
        entityRepository.confirm(ctUtrEntity).futureValue.id mustBe ctUtrEntity.id

        private val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(ctUtr, nino1)
        intercept[RuntimeException] {
          resolver.resolveAny(taxIdentifiersInAuth, resolveIdsFlag).futureValue.get.id must be(ninoEntity.id)
        }.getMessage must include(
          "Unimplemented scenario! Two entities found for the current tax identifier but none is the utr"
        )
      }
  }

  trait TestCaseWithMongoSupport extends TestCase {}

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[PreferencesConnector].toInstance(mock[PreferencesConnector]))
      .overrides(bind[AuditConnector].toInstance(mock[AuditConnector]))
      .overrides(bind[Metrics].toInstance(mock[Metrics]))
      .overrides(bind[() => EntityId].toInstance(mock[() => EntityId]))
      .configure("appName" -> "test-app", "metrics.enabled" -> "false")
      .build()

  trait TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val utr: SaUtr = Generate.utr
    val nino: Nino = Generate.nino
    val entityId: EntityId = EntityId.generate()

    val entityRepository: EntityRepository = app.injector.instanceOf[EntityRepository]
    val preferencesConnector: PreferencesConnector = app.injector.instanceOf[PreferencesConnector]
    val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]
    val resolver: EntityIdResolver = app.injector.instanceOf[EntityIdResolver]
    val resolveIdsFlag = true

    entityRepository.removeAll().futureValue
  }

}
