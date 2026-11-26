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

import com.codahale.metrics.{ MetricRegistry, SharedMetricRegistries }
import com.google.inject.name.Names
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{ BindingKey, QualifierInstance, bind }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.entityresolver.connectors.PreferencesConnector
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import utils.Generate

import scala.concurrent.{ ExecutionContext, Future }

class EntityIdResolverSpec
    extends PlaySpec with GuiceOneAppPerTest with MockitoSugar with ScalaFutures with MongoSupport {
  spec =>

  "Given a tax identifier" should {
    "create new entity id if no matching entity id found for user with a utr only" in new TestCase {

      val currentTaxIds: Set[TaxIdWithName] = Set(utr)

      private val dataEventCaptor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])

      when(mockAuditConnector.sendEvent(dataEventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))
      when(mockEntityRepository.findByAny(currentTaxIds)).thenReturn(Future.successful(List.empty))
      when(mockEntityRepository.confirm(any[Entity])(any[ExecutionContext]))
        .thenReturn(Future.successful(Entity(entityId, currentTaxIds)))
      when(mockEntityRepository.updateTaxIds(entityId, currentTaxIds)).thenReturn(Future.successful(true))

      resolver.resolveAny(currentTaxIds, resolveIdsFlag).futureValue.get.id mustBe entityId

      dataEventCaptor.getValue must have(
        Symbol("auditSource")(appName),
        Symbol("auditType")("TxSucceeded"),
        Symbol("tags")(
          Map(
            "transactionName" -> "EntityId Created - Resolving On Empty"
          )
        ),
        Symbol("detail")(
          Map(
            "sautr"    -> utr.value,
            "entityId" -> entityId.value
          )
        )
      )
    }

    "create new entity id if no matching entity id found for user with nino and utr" in new TestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr, nino)

      private val dataEventCaptor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])

      when(mockAuditConnector.sendEvent(dataEventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))
      when(mockEntityRepository.findByAny(currentTaxIds)).thenReturn(Future.successful(List.empty))
      when(mockEntityRepository.confirm(any[Entity])(any[ExecutionContext]))
        .thenReturn(Future.successful(Entity(entityId, currentTaxIds)))
      when(mockEntityRepository.updateTaxIds(entityId, currentTaxIds)).thenReturn(Future.successful(true))

      resolver.resolveAny(currentTaxIds, resolveIdsFlag).futureValue.get.id mustBe entityId

      dataEventCaptor.getValue must have(
        Symbol("auditSource")(appName),
        Symbol("auditType")("TxSucceeded"),
        Symbol("tags")(
          Map(
            "transactionName" -> "EntityId Created - Resolving On Empty"
          )
        ),
        Symbol("detail")(
          Map(
            "sautr"    -> utr.value,
            "nino"     -> nino.value,
            "entityId" -> entityId.value
          )
        )
      )
    }

    "not create new entity id if no matching entity id found and resolveIds flag is false but return a dummy entity id" in new TestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr)
      val entity = Entity(entityId, currentTaxIds)

      when(mockEntityRepository.findByAny(currentTaxIds)).thenReturn(Future.successful(List.empty))

      resolver.resolveAny(currentTaxIds, resolveIds = false).futureValue mustBe None
      verify(mockEntityRepository, never()).confirm(entity)
    }

    "create new entity id if no matching entity id found for user with a nino only" in new TestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(nino)

      private val dataEventCaptor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])

      when(mockAuditConnector.sendEvent(dataEventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))
      when(mockEntityRepository.findByAny(currentTaxIds)).thenReturn(Future.successful(Nil))
      when(mockEntityRepository.confirm(any[Entity])(any[ExecutionContext]))
        .thenReturn(Future.successful(Entity(entityId, currentTaxIds)))
      when(mockEntityRepository.updateTaxIds(entityId, currentTaxIds)).thenReturn(Future.successful(true))

      resolver.resolveAny(currentTaxIds, resolveIdsFlag).futureValue.get.id mustBe entityId

      dataEventCaptor.getValue must have(
        Symbol("auditSource")(appName),
        Symbol("auditType")("TxSucceeded"),
        Symbol("tags")(
          Map(
            "transactionName" -> "EntityId Created - Resolving On Empty"
          )
        ),
        Symbol("detail")(
          Map(
            "nino"     -> nino.value,
            "entityId" -> entityId.value
          )
        )
      )
    }

    "return existing entity id if matching entity id found - user with utr" in new TestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr)
      val entity = Entity(entityId, currentTaxIds)

      when(mockEntityRepository.findByAny(currentTaxIds)).thenReturn(Future.successful(List(entity)))

      when(mockEntityRepository.updateTaxIds(entityId, currentTaxIds)).thenReturn(Future.successful(true))

      resolver.resolveAny(currentTaxIds, resolveIdsFlag).futureValue.get.id mustBe entityId

      verify(mockEntityRepository, never()).confirm(any[Entity])(any[ExecutionContext])
    }

    "return existing entity id if matching entity id found - user with nino and utr" in new TestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(nino, utr)
      val entity = Entity(entityId, currentTaxIds)

      when(mockEntityRepository.findByAny(currentTaxIds)).thenReturn(Future.successful(List(entity)))
      when(mockEntityRepository.updateTaxIds(entityId, currentTaxIds)).thenReturn(Future.successful(true))

      resolver.resolveAny(currentTaxIds, resolveIdsFlag).futureValue.get.id mustBe entityId

      verify(mockEntityRepository, never()).confirm(any[Entity])(any[ExecutionContext])
    }

    "return existing entity id if matching entity id found - nino only" in new TestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(nino)
      val entity = Entity(entityId, currentTaxIds)

      when(mockEntityRepository.findByAny(currentTaxIds)).thenReturn(Future.successful(List(entity)))
      when(mockEntityRepository.updateTaxIds(entityId, currentTaxIds)).thenReturn(Future.successful(true))

      resolver.resolveAny(currentTaxIds, resolveIdsFlag).futureValue.get.id mustBe entityId

      verify(mockEntityRepository, never()).confirm(any[Entity])(any[ExecutionContext])
    }

    "return utr for entity id when they are associated with each other" in new TestCase {
      val entity = Entity(entityId, utr)

      when(mockEntityRepository.findBy(entityId)).thenReturn(Future.successful(Some(entity)))

      resolver.resolveUtrForEntityId(entityId).futureValue mustBe Some(utr)
    }

    "return none for entity id when associated with a nino" in new TestCase {
      val entity = Entity(entityId, nino)

      when(mockEntityRepository.findBy(entityId)).thenReturn(Future.successful(Some(entity)))

      resolver.resolveUtrForEntityId(entityId).futureValue mustBe None
    }

    "return utr for entity id when entity is associated with multiple tax identifiers" in new TestCase {
      val entity = new Entity(entityId, Set(utr, nino))

      when(mockEntityRepository.findBy(entityId)).thenReturn(Future.successful(Some(entity)))

      resolver.resolveUtrForEntityId(entityId).futureValue mustBe Some(utr)
    }

    "return none for entity id when no associated utr " in new TestCase {
      when(mockEntityRepository.findBy(entityId)).thenReturn(Future.successful(None))

      resolver.resolveUtrForEntityId(entityId).futureValue must be(None)

      SharedMetricRegistries.clear()

    }

    "return none for entity id when no associated nino" in new TestCase {
      when(mockEntityRepository.findBy(entityId)).thenReturn(Future.successful(None))

      resolver.resolveNinoForEntityId(entityId).futureValue must be(None)
    }
  }

  "Finding a nino from entity id" should {
    "return nino if found" in new TestCase {
      val entity = new Entity(entityId, Set(Generate.utr, nino))

      when(mockEntityRepository.findBy(entityId)).thenReturn(Future.successful(Some(entity)))

      resolver.resolveNinoForEntityId(entityId).futureValue must be(Some(nino))
    }
  }

  "LookupResult" should {

    "recognise the master entity with the same utr found in auth" in new TestCase {
      val masterEntity = Entity(Generate.entityId, utr)
      val taxIdsInAuth: Set[TaxIdWithName] = Set(utr, nino)

      LookupResult(List(masterEntity), taxIdsInAuth) mustBe LookupResult(Some(masterEntity), None)
    }

    "recognise the conflicting entity without the utr found in auth" in new TestCase {
      val conflictingEntity = Entity(Generate.entityId, nino)
      val taxIdsInAuth: Set[TaxIdWithName] = Set(utr, nino)

      LookupResult(List(conflictingEntity), taxIdsInAuth) mustBe LookupResult(None, Some(conflictingEntity))
    }

    "recognise the conflicting entity when there is no utr in auth but there is one in the entity" in new TestCase {
      val conflictingEntity = Entity(Generate.entityId, utr, nino)
      val taxIdsInAuth: Set[TaxIdWithName] = Set(nino)

      LookupResult(List(conflictingEntity), taxIdsInAuth) mustBe LookupResult(None, Some(conflictingEntity))
    }

    "handle users with no utr in and entity" in new TestCase {
      val ninoOnlyEntity = Entity(Generate.entityId, nino)
      val taxIdsInAuth: Set[TaxIdWithName] = Set(nino)

      LookupResult(List(ninoOnlyEntity), taxIdsInAuth) mustBe LookupResult(
        None,
        Some(ninoOnlyEntity),
        needsConflictResolution = false
      )
    }

    "throw an exception when there are more than two matching entities" in new TestCase {
      val conflictingEntity = Entity(Generate.entityId, nino)
      val taxIdsInAuth: Set[TaxIdWithName] = Set(utr, nino)

      intercept[RuntimeException] {
        LookupResult(
          List(conflictingEntity, Entity(Generate.entityId, Generate.utr), Entity(Generate.entityId, Generate.utr)),
          taxIdsInAuth
        )
      }.getMessage must include("More than two entities found")
    }

    "match the identifiers of the master entity with the ones in auth" in new TestCase {
      val masterEntity = Entity(Generate.entityId, nino, utr)
      val matchingIds: Set[TaxIdWithName] = Set(utr, nino)
      val nonMatchingIds: Set[TaxIdWithName] = Set(utr)

      LookupResult(List(masterEntity), matchingIds) mustBe LookupResult(
        Some(masterEntity),
        None,
        needsConflictResolution = false
      )
      LookupResult(List(masterEntity), nonMatchingIds) mustBe LookupResult(Some(masterEntity), None)
    }

    "match the identifiers of the conflicting entity where auth doesnt have a utr" in new TestCase {
      val (utr2, nino1) = (Generate.utr, Generate.nino)
      private val entityId1 = Generate.entityId

      val conflictingEntity = Entity(entityId1, utr2, nino1)
      val taxIdentifiersInAuth: Set[TaxIdWithName] = Set(nino1)

      LookupResult(List(conflictingEntity), taxIdentifiersInAuth) mustBe LookupResult(None, Some(conflictingEntity))
    }
  }

  override def fakeApplication(): Application = {
    val mockMetrics = mock[Metrics]
    val mockMetricsRegistry = mock[MetricRegistry]
    when(mockMetrics.defaultRegistry).thenReturn(mockMetricsRegistry)

    val generateEntityIdMock = mock[() => EntityId]

    new GuiceApplicationBuilder()
      .overrides(bind[PreferencesConnector].toInstance(mock[PreferencesConnector]))
      .overrides(bind[AuditConnector].toInstance(mock[AuditConnector]))
      .overrides(bind[Metrics].toInstance(mockMetrics))
      .overrides(bind[EntityRepository].toInstance(mock[EntityRepository]))
      .overrides(bind[() => EntityId].toInstance(generateEntityIdMock))
      .configure("appName" -> "test-app", "metrics.enabled" -> "false")
      .build()
  }

  trait TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val ec: ExecutionContext = ExecutionContext.global

    val utr: SaUtr = Generate.utr
    val nino: Nino = Generate.nino
    val entityId: EntityId = EntityId.generate()

    val mockEntityRepository: EntityRepository = app.injector.instanceOf[EntityRepository]
    val mockPreferencesConnector: PreferencesConnector = app.injector.instanceOf[PreferencesConnector]
    val mockAuditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]

    val resolver: EntityIdResolver = app.injector.instanceOf[EntityIdResolver]
    val resolveIdsFlag = true

    val appName: String =
      app.injector.instanceOf(BindingKey(classOf[String], Some(QualifierInstance(Names.named("appName")))))
  }

}
