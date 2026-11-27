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

package uk.gov.hmrc.entityresolver.repository

import com.mongodb.client.model.Filters
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.test.Helpers.*
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ CtUtr, HmrcMtdItsa, HmrcMtdVat, Nino, SaUtr, TaxIds }
import uk.gov.hmrc.entityresolver.connectors.PreferencesConnector
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import utils.Generate
import org.mongodb.scala.{ ObservableFuture, SingleObservableFuture }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EntityRepositorySpec
    extends PlaySpec with GuiceOneAppPerTest with MongoSupport with ScalaFutures with IntegrationPatience
    with MockitoSugar with BeforeAndAfterEach {
  spec =>

  override def beforeEach(): Unit =
    mongoComponent.database.getCollection("entity").deleteMany(Filters.empty()).toFuture().futureValue

  "indexes" must {
    "match the expected number on ensureIndex" in new TestCase {
      repository.collection.listIndexes().toFuture().futureValue must have size 16
    }
  }

  "confirm" should {
    "persist given entity and return entityId if there's no record with given utr" in new TestCase {
      private val entityId = EntityId.generate()
      private val utr = Generate.utr

      repository.confirm(Entity(entityId, utr, Generate.nino)).futureValue.id mustBe entityId
    }

    "not update existing record with extra ids" in new TestCase {
      private val entityId = EntityId.generate()
      private val utr = Generate.utr

      repository.confirm(Entity(entityId, utr)).futureValue.id mustBe entityId

      private val nino = Generate.nino
      private val entity = Entity(entityId, utr, nino)
      repository.confirm(entity).futureValue.id mustBe entityId

      repository.findBy(nino).futureValue must be(None)
    }

    "return the existing entityId if a tax identifier is stored second time" in new TestCase {
      private val initialEntityId = EntityId.generate()
      private val utr = Generate.utr
      private val nino = Generate.nino

      repository.confirm(Entity(initialEntityId, utr, nino)).futureValue.id mustBe initialEntityId
      repository.confirm(Entity(Generate.entityId, utr)).futureValue.id mustBe initialEntityId
    }

    "not throw an duplicate key error with near simultaneous confirms" in new TestCase {
      private val entityId = EntityId.generate()
      private val utr = Generate.utr
      private val nino = Generate.nino
      private val entity = Entity(entityId, utr, nino)
      private val instances = 1 until 10

      await(Future.sequence(instances.map { _ =>
        repository.confirm(entity)
      }))
    }
  }

  "findBy" should {
    "find existing entity by utr" in new TestCase {
      private val utr = Generate.utr
      private val entity = Entity(EntityId.generate(), utr, Generate.nino)
      repository.confirm(entity).futureValue.id mustBe entity.id

      repository.findBy(utr).futureValue mustBe Some(entity)
    }

    "find existing entity by nino" in new TestCase {
      private val nino = Generate.nino
      private val entity = Entity(EntityId.generate(), Generate.utr, nino)
      repository.confirm(entity).futureValue.id mustBe entity.id

      repository.findBy(nino).futureValue mustBe Some(entity)
    }

    "find existing entity by ctutr" in new TestCase {
      private val ctutr = Generate.ctUtr
      private val entity = Entity(EntityId.generate(), ctutr)
      repository.confirm(entity).futureValue.id mustBe entity.id

      repository.findBy(ctutr).futureValue mustBe Some(entity)
    }
  }

  "findByAny" should {
    "return empty list if no matches with the given tax ids" in new TestCase {
      repository.findByAny(Set(Generate.utr)).futureValue mustBe List.empty
    }

    "find a record having more than the only given tax id" in new TestCase {
      private val utr = Generate.utr
      private val nino = Generate.nino
      private val entity = Entity(EntityId.generate(), utr, nino)
      repository.confirm(entity).futureValue.id mustBe entity.id

      repository.findByAny(Set(utr)).futureValue mustBe List(entity)
    }

    "find a record having all of the given tax ids" in new TestCase {
      private val utr = Generate.utr
      private val nino = Generate.nino
      private val ctutr = Generate.ctUtr
      private val entity = Entity(EntityId.generate(), utr, nino, ctutr)
      repository.confirm(entity).futureValue.id mustBe entity.id

      repository.findByAny(Set(utr, nino, ctutr)).futureValue mustBe List(entity)
    }

    "return a list of found entities for more than one tax ids" in new TestCase {
      private val utr = Generate.utr
      private val entityForUtr = EntityId.generate()
      private val utrEntity = Entity(entityForUtr, utr)
      repository.confirm(utrEntity).futureValue.id mustBe utrEntity.id

      private val nino = Generate.nino
      private val entityForNino = EntityId.generate()
      private val ninoEntity = Entity(entityForNino, nino)
      repository.confirm(ninoEntity).futureValue.id mustBe ninoEntity.id

      repository.findByAny(Set(utr, nino)).futureValue must contain.allOf(ninoEntity, utrEntity)
    }
  }

  "updateTaxIds" should {

    "return false if tax ids empty" in new TestCase {
      private val entity = Entity(EntityId.generate(), Generate.utr, Generate.nino)
      repository.confirm(entity).futureValue.id mustBe entity.id
      repository.findBy(entity.id).futureValue must be(Some(entity))

      private val newTaxIds = Set.empty[TaxIdWithName]
      repository.updateTaxIds(entity.id, newTaxIds).futureValue must be(false)
    }

    "updates existing tax ids with the given ones and return true if there is no conflict with other entities" in new TestCase {
      private val entity = Entity(EntityId.generate(), Generate.utr, Generate.nino, Generate.ctUtr)
      repository.confirm(entity).futureValue.id mustBe entity.id
      repository.findBy(entity.id).futureValue must be(Some(entity))

      private val newTaxIds = Set[TaxIdWithName](Generate.utr, Generate.nino, Generate.ctUtr)
      repository.updateTaxIds(entity.id, newTaxIds).futureValue must be(true)
      repository.findBy(entity.id).futureValue must be(Some(entity.copy(taxIdentifiers = newTaxIds)))
    }

    "updates only given tax id and does not remove others if only one tax id is given" in new TestCase {
      private val utr = Generate.utr
      private val entity = Entity(EntityId.generate(), utr, Generate.nino)
      repository.confirm(entity).futureValue.id mustBe entity.id
      repository.findBy(entity.id).futureValue must be(Some(entity))

      private val newNino = Generate.nino
      repository.updateTaxIds(entity.id, Set[TaxIdWithName](newNino)).futureValue must be(true)
      repository.findBy(entity.id).futureValue must be(Some(entity.copy(taxIdentifiers = Set(newNino, utr))))
    }

    "return false if there is no entity with the given entityId" in new TestCase {
      private val entityId = EntityId.generate()
      repository.updateTaxIds(entityId, Set(Generate.utr)).futureValue must be(false)
      repository.findBy(entityId).futureValue must be(None)
    }

    "return false and saves failure if one of the new tax ids conflicts with another entity" in new TestCase {
      private val entity1utr = Generate.utr
      private val entity1 = Entity(EntityId.generate(), entity1utr, Generate.nino)
      repository.confirm(entity1).futureValue.id mustBe entity1.id
      repository.findBy(entity1.id).futureValue must be(Some(entity1))

      private val entity2 = Entity(EntityId.generate(), Generate.utr, Generate.nino)
      repository.confirm(entity2).futureValue.id mustBe entity2.id
      repository.findBy(entity2.id).futureValue must be(Some(entity2))

      private val newTaxIds = Set[TaxIdWithName](entity1utr)
      repository.updateTaxIds(entity2.id, newTaxIds).futureValue must be(false)
      repository.findBy(entity2.id).futureValue must be(Some(entity2))
    }
  }

  "override tax ids" should {
    "remove the ids that are not required any more" in new TestCase {
      private val utr1 = Generate.utr
      private val entity1 = Entity(EntityId.generate(), utr1, Generate.nino)
      repository.confirm(entity1).futureValue.id mustBe entity1.id

      private val newTaxIdentifiers: Set[TaxIdWithName] = Set(utr1)
      repository.updateTaxIds(entity1.id, newTaxIdentifiers, overrideIds = true).futureValue must be(true)

      repository.findBy(entity1.id).futureValue must be(Some(entity1.copy(taxIdentifiers = newTaxIdentifiers)))
    }

    "keep existing ids that are still required" in new TestCase {
      private val utr1 = Generate.utr
      private val entity1 = Entity(EntityId.generate(), utr1)

      repository.confirm(entity1).futureValue.id mustBe entity1.id

      private val newTaxIdentifiers: Set[TaxIdWithName] = Set(utr1)
      repository.updateTaxIds(entity1.id, newTaxIdentifiers, overrideIds = true).futureValue must be(false)

      repository.findBy(entity1.id).futureValue must be(Some(entity1))
    }

    "add new ids that are now required" in new TestCase {
      private val utr1 = Generate.utr
      private val nino1 = Generate.nino
      private val entity1 = Entity(EntityId.generate(), utr1)
      repository.confirm(entity1).futureValue.id mustBe entity1.id

      private val newTaxIdentifiers: Set[TaxIdWithName] = Set(utr1, nino1)
      repository.updateTaxIds(entity1.id, newTaxIdentifiers, overrideIds = true).futureValue must be(true)

      repository.findBy(entity1.id).futureValue must be(Some(entity1.copy(taxIdentifiers = newTaxIdentifiers)))
    }
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[PreferencesConnector].toInstance(mock[PreferencesConnector]))
      .overrides(bind[AuditConnector].toInstance(mock[AuditConnector]))
      .configure("appName" -> "test-app", "metrics.enabled" -> "false")
      .build()

  trait TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val repository: EntityRepository = app.injector.instanceOf[EntityRepository]
    repository.collection.deleteMany(Filters.empty()).toFuture().futureValue
  }
}

class EntityMetricsRepositorySpec
    extends PlaySpec with GuiceOneAppPerTest with MongoSupport with ScalaFutures with IntegrationPatience {
  spec =>

  "countAll" should {
    "return count of all entity records" in new TestCase {

      entityRepository.collection.deleteMany(Filters.empty()).toFuture().futureValue

      val entity1 = Entity(EntityId.generate(), Generate.utr)
      entityRepository.confirm(entity1).futureValue.id mustBe entity1.id

      val entity2 = Entity(EntityId.generate(), Generate.nino)
      entityRepository.confirm(entity2).futureValue.id mustBe entity2.id

      val entity3 = Entity(EntityId.generate(), Generate.utr, Generate.nino)
      entityRepository.confirm(entity3).futureValue.id mustBe entity3.id

      val entity4 = Entity(EntityId.generate(), Generate.nino, Generate.itsa)
      entityRepository.confirm(entity4).futureValue.id mustBe entity4.id

      entityRepository.countAll().futureValue must be(4)
    }
  }

  "countITSAIDs" should {
    "return count of entity records with MTD-ITSA-ID" in new TestCase {
      entityRepository.collection.deleteMany(Filters.empty()).toFuture().futureValue

      val entity1 = Entity(EntityId.generate(), Generate.nino, Generate.utr, HmrcMtdItsa("999999999999991"))
      entityRepository.confirm(entity1).futureValue.id mustBe entity1.id

      val entity2 = Entity(EntityId.generate(), Generate.nino)
      entityRepository.confirm(entity2).futureValue.id mustBe entity2.id

      val entity3 = Entity(EntityId.generate(), Generate.utr, Generate.nino)
      entityRepository.confirm(entity3).futureValue.id mustBe entity3.id

      val entity4 = Entity(EntityId.generate(), Generate.nino, Generate.itsa)
      entityRepository.confirm(entity4).futureValue.id mustBe entity4.id

      entityRepository.countITSAIDs().futureValue must be(2)
    }
  }

  "countBySoleTaxId" should {
    "return count of entities having the given taxId type and only that taxId type" in new TestCase {

      entityRepository.collection.deleteMany(Filters.empty()).toFuture().futureValue

      val entity1 = Entity(EntityId.generate(), Generate.utr)
      entityRepository.confirm(entity1).futureValue.id mustBe entity1.id

      val entity2 = Entity(EntityId.generate(), Generate.nino)
      entityRepository.confirm(entity2).futureValue.id mustBe entity2.id

      val entity3 = Entity(EntityId.generate(), Generate.utr, Generate.nino)
      entityRepository.confirm(entity3).futureValue.id mustBe entity3.id

      val entity4 = Entity(EntityId.generate(), Generate.nino, Generate.itsa)
      entityRepository.confirm(entity4).futureValue.id mustBe entity4.id

      def taxIdFor(taxIdName: String) = TaxIds.defaultSerialisableIds.find(_.taxIdName == taxIdName).get

      entityRepository.countBySoleTaxId(taxIdFor("sautr")).futureValue must be(1)
      entityRepository.countBySoleTaxId(taxIdFor("nino")).futureValue must be(2)
      entityRepository.countBySoleTaxId(taxIdFor("ctutr")).futureValue must be(0)
      entityRepository.countBySoleTaxId(taxIdFor("HMRC-MTD-IT")).futureValue must be(0)
    }
  }

  "countByTaxId" should {
    "return count of entities having the given taxId type" in new TestCase {

      entityRepository.collection.deleteMany(Filters.empty()).toFuture().futureValue

      val entity1 = Entity(EntityId.generate(), Generate.utr)
      entityRepository.confirm(entity1).futureValue.id mustBe entity1.id

      val entity2 = Entity(EntityId.generate(), Generate.nino)
      entityRepository.confirm(entity2).futureValue.id mustBe entity2.id

      val entity3 = Entity(EntityId.generate(), Generate.utr, Generate.nino)
      entityRepository.confirm(entity3).futureValue.id mustBe entity3.id

      val entity4 = Entity(EntityId.generate(), Generate.nino, Generate.itsa)
      entityRepository.confirm(entity4).futureValue.id mustBe entity4.id

      def taxIdFor(taxIdName: String) = TaxIds.defaultSerialisableIds.find(_.taxIdName == taxIdName).get

      entityRepository.countByTaxId(taxIdFor("sautr")).futureValue must be(2)
      entityRepository.countByTaxId(taxIdFor("nino")).futureValue must be(3)
      entityRepository.countByTaxId(taxIdFor("ctutr")).futureValue must be(0)
      entityRepository.countByTaxId(taxIdFor("HMRC-MTD-IT")).futureValue must be(1)
    }
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[PreferencesConnector].toInstance(mock[PreferencesConnector]))
      .overrides(bind[AuditConnector].toInstance(mock[AuditConnector]))
      .configure("appName" -> "test-app", "metrics.enabled" -> "false")
      .build()

  trait TestCase {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val entityRepository: EntityRepository = app.injector.instanceOf[EntityRepository]
  }

}

class EntityFormatSpec extends PlaySpec {

  "format" should {
    "serialize Entity object to json" in {
      val entity = Entity(EntityId("123"), SaUtr("456"), Nino("CE123456D"), CtUtr("08765"), HmrcMtdVat("12345"))
      Entity.formats.writes(entity) must be(Json.parse("""{
                                                         |"_id":"123",
                                                         |"sautr":"456",
                                                         |"nino":"CE123456D",
                                                         |"ctutr":"08765",
                                                         |"HMRC-MTD-VAT":"12345"
                                                         |}""".stripMargin))
    }

    "deserialize json to Entity object" in {
      val json = Json.parse(
        """{
          |"_id":"123",
          |"sautr":"456",
          |"nino":"CE123456D",
          |"ctutr":"08765",
          |"HMRC-MTD-VAT":"12345"
          |}""".stripMargin
      )
      Entity.formats.reads(json).get must be(
        Entity(EntityId("123"), SaUtr("456"), Nino("CE123456D"), CtUtr("08765"), HmrcMtdVat("12345"))
      )
    }
  }
}
