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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ times, verify, verifyNoMoreInteractions, when }
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.{ contentAsJson, * }
import play.api.test.*
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.entityresolver.binders.GetEntityParamsBinder
import uk.gov.hmrc.entityresolver.connectors.TaxIdentifierRetriever
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import utils.Generate

import scala.concurrent.{ ExecutionContext, Future }

class EntityControllerSpec extends PlaySpec with GuiceOneAppPerTest with ScalaFutures with MockitoSugar {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[TaxIdentifierRetriever].toInstance(mock[TaxIdentifierRetriever]))
      .overrides(bind[EntityRepository].toInstance(mock[EntityRepository]))
      .configure("metrics.enabled" -> "false")
      .configure("auditing.enabled" -> "false")
      .build()

  "Entity Controller" should {

    "invalid querystring combination" in new TestCase {
      private val result = controller
        .findEntity(GetEntityParamsBinder(Some("nino"), Some(""), Some(entityId.value), None))
        .apply(FakeRequest())
      status(result) must be(BAD_REQUEST)
      contentAsString(result) must be("Invalid querystring format")
    }

    "find entity by supplying an entityId" in new TestCase {
      val taxRegimes: Set[TaxIdWithName] = Set(nino)
      when(mockRepo.findBy(any[EntityId])(any[ExecutionContext])).thenReturn(
        Future.successful(Some(Entity(entityId, taxRegimes)))
      )
      private val result =
        controller
          .findEntity(GetEntityParamsBinder(None, None, Some(entityId.value), None))
          .apply(FakeRequest())
      status(result) must be(OK)
      val json = contentAsJson(result)
      json must be(Json.obj("_id" -> s"${entityId.value}", "nino" -> s"${nino.value}"))
    }

    "find entity by supplying a nino" in new TestCase {
      val taxRegimes: Set[TaxIdWithName] = Set(nino)
      when(mockRepo.findBy(any[TaxIdWithName])(any[ExecutionContext])).thenReturn(
        Future.successful(Some(Entity(entityId, taxRegimes)))
      )
      private val result = controller
        .findEntity(GetEntityParamsBinder(Some("paye"), Some(nino.value), None, None))
        .apply(FakeRequest())
      status(result) must be(OK)
      val json = contentAsJson(result)
      json must be(Json.obj("_id" -> s"${entityId.value}", "nino" -> s"${nino.value}"))
    }

    "find entity by supplying an sautr" in new TestCase {
      val taxRegimes: Set[TaxIdWithName] = Set(utr)
      when(mockRepo.findBy(any[TaxIdWithName])(any[ExecutionContext])).thenReturn(
        Future.successful(Some(Entity(entityId, taxRegimes)))
      )
      private val result = controller
        .findEntity(GetEntityParamsBinder(Some("sa"), Some(utr.value), None, None))
        .apply(FakeRequest())
      status(result) must be(OK)
      val json = contentAsJson(result)
      json must be(Json.obj("_id" -> s"${entityId.value}", "sautr" -> s"${utr.value}"))
    }

    "find entity by supplying an itsa" in new TestCase {
      val taxRegimes: Set[TaxIdWithName] = Set(itsa)
      when(mockRepo.findBy(any[TaxIdWithName])(any[ExecutionContext])).thenReturn(
        Future.successful(Some(Entity(entityId, taxRegimes)))
      )
      private val result =
        controller
          .findEntity(GetEntityParamsBinder(Some("itsa"), Some(itsa.value), None, None))
          .apply(FakeRequest())
      status(result) must be(OK)
      val json = contentAsJson(result)
      json must be(Json.obj("_id" -> s"${entityId.value}", "HMRC-MTD-IT" -> s"${itsa.value}"))
    }

    "find entity with invalid tax regime returns 400" in new TestCase {
      private val result =
        controller
          .findEntity(GetEntityParamsBinder(Some("wibble"), Some(nino.value), None, None))
          .apply(FakeRequest())
      status(result) must be(BAD_REQUEST)
      contentAsString(result) must be("Invalid tax regime: wibble")
    }

    "find entity by inspecting the auth token in the http request" in new TestCase {
      val taxRegimes: Set[TaxIdWithName] = Set(nino)
      when(mockTaxIdentifierRetriever.currentTaxIdentifiers(any)).thenReturn(
        Future.successful(taxRegimes)
      )
      when(mockRepo.findByAny(taxRegimes)).thenReturn(
        Future.successful(Seq(Entity(entityId, taxRegimes)))
      )
      when(mockRepo.findBy(any[EntityId])(any)).thenReturn(
        Future.successful(Some(Entity(entityId, taxRegimes)))
      )
      val result = controller.findEntity(GetEntityParamsBinder(None, None, None, None)).apply(FakeRequest())
      status(result) must be(OK)
      contentAsJson(result) must be(Json.obj("_id" -> s"${entityId.value}", "nino" -> s"${nino.value}"))
    }

    "find entity by inspecting the auth token in the http request, without an existing entity" in new TestCase {
      val taxRegimes: Set[TaxIdWithName] = Set(nino)
      when(mockTaxIdentifierRetriever.currentTaxIdentifiers(any)).thenReturn(
        Future.successful(taxRegimes)
      )
      when(mockRepo.findByAny(taxRegimes)).thenReturn(
        Future.successful(List())
      )

      private val entity = Entity(EntityId.generate(), taxRegimes)
      when(mockRepo.confirm(any)(any)).thenReturn(
        Future.successful(entity)
      )
      val result = controller
        .findEntity(GetEntityParamsBinder(None, None, None, Some(true), Some(true)))
        .apply(FakeRequest())
      status(result) must be(OK)
      val json = contentAsJson(result)
      (json \ "_id").as[String] must fullyMatch regex "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
      (json \ "nino").as[String] must be(s"${nino.value}")

      verify(mockTaxIdentifierRetriever).currentTaxIdentifiers(any)
      verify(mockRepo).findByAny(any)
      verify(mockRepo).confirm(any[Entity])(any)
    }

    "find entity by inspecting the auth token in the http request without resolution" in new TestCase {
      val taxRegimes: Set[TaxIdWithName] = Set(nino)
      when(mockTaxIdentifierRetriever.currentTaxIdentifiers(any)).thenReturn(
        Future.successful(taxRegimes)
      )
      when(mockRepo.findByAny(taxRegimes)).thenReturn(
        Future.successful(Seq(Entity(entityId, taxRegimes)))
      )

      val result =
        controller
          .findEntity(GetEntityParamsBinder(None, None, None, None))
          .apply(FakeRequest())

      status(result) must be(OK)
      contentAsJson(result) must be(Json.obj("_id" -> s"${entityId.value}", "nino" -> s"${nino.value}"))

      verify(mockRepo, times(1)).findByAny(any)
      verifyNoMoreInteractions(mockRepo)
    }

    "repo throws an exception" in new TestCase {
      val taxRegimes: Set[TaxIdWithName] = Set(nino)
      when(mockRepo.findBy(any[TaxIdWithName])(any[ExecutionContext])).thenThrow(
        RuntimeException("Whoops")
      )
      private val result = controller
        .findEntity(GetEntityParamsBinder(Some("paye"), Some(nino.value), None, None))
        .apply(FakeRequest())
      status(result) must be(INTERNAL_SERVER_ERROR)
      contentAsString(result) must be("Whoops")
    }
  }

  "ITSA entity controller" should {

    "find entity by inspecting the auth token in the http request with existing entity" in new TestCase {
      val taxRegimes: Set[TaxIdWithName] = Set(nino)
      when(mockTaxIdentifierRetriever.currentTaxIdentifiers(any))
        .thenReturn(Future.successful(taxRegimes))
      when(mockRepo.findByAny(taxRegimes))
        .thenReturn(Future.successful(Seq(Entity(entityId, taxRegimes))))

      val result = controller.findRegimeEntity().apply(FakeRequest())
      status(result) must be(OK)
      contentAsJson(result) must be(Json.obj("_id" -> s"${entityId.value}", "nino" -> s"${nino.value}"))

      verify(mockTaxIdentifierRetriever).currentTaxIdentifiers(any)
      verify(mockRepo).findByAny(any)
      verifyNoMoreInteractions(mockRepo)
    }

    "find entity by inspecting the auth token in the http request without existing entity" in new TestCase {
      val taxRegimes: Set[TaxIdWithName] = Set(nino, itsa)
      when(mockTaxIdentifierRetriever.currentTaxIdentifiers(any))
        .thenReturn(Future.successful(taxRegimes))
      when(mockRepo.findByAny(taxRegimes))
        .thenReturn(Future.successful(Seq()))
      when(mockRepo.confirm(any)(any))
        .thenReturn(Future.successful(Entity(entityId, taxRegimes)))

      val result = controller.findRegimeEntity().apply(FakeRequest())
      status(result) must be(OK)
      contentAsJson(result) must be(
        Json.obj("_id" -> s"${entityId.value}", "nino" -> s"${nino.value}", "HMRC-MTD-IT" -> s"${itsa.value}")
      )

      verify(mockTaxIdentifierRetriever).currentTaxIdentifiers(any)
      verify(mockRepo).findByAny(any)
      verify(mockRepo).confirm(any[Entity])(any)
      verifyNoMoreInteractions(mockRepo)
    }
  }

  trait TestCase {
    val entityId: EntityId = Generate.entityId
    val nino = Generate.nino
    val utr = Generate.utr
    val itsa = Generate.itsa
    val mockRepo = app.injector.instanceOf[EntityRepository]
    val mockTaxIdentifierRetriever = app.injector.instanceOf[TaxIdentifierRetriever]
    val controller = app.injector.instanceOf[EntityController]
    implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  }
}
