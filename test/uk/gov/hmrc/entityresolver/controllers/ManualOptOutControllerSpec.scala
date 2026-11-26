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
import org.mockito.ArgumentMatchers.{ any, anyBoolean, anyString }
import org.mockito.ArgumentMatchers.{ eq => argEq }
import org.mockito.Mockito.{ times, verify, when }
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.mvc.{ AnyContent, Request, Results }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr }
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.entityresolver.services.ManualOptOutService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

class ManualOptOutControllerSpec extends PlaySpec with GuiceOneAppPerTest with MockitoSugar with ScalaFutures {

  "manualOptOutController" should {

    "fail on an invalid Nino" in new TestCase {
      private val result = call(controller.manualOptOut(ninoRegime, "invalidnino"), FakeRequest())
      status(result) mustBe Status.INTERNAL_SERVER_ERROR
    }

    "return OK and audit as succeeded with manual opt out for a saUtr if entityId is present, opted in and call to opt out successful" in new TestCase {
      when(mockRepo.findBy(argEq(utr))(any[ExecutionContext])).thenReturn(Future.successful(Some(entityWithUtr)))
      when(mockService.optOut(argEq(entityWithUtr))(any[HeaderCarrier], any[Request[AnyContent]]))
        .thenReturn(Future.successful(Results.Ok))

      private val result = call(controller.manualOptOut(saUtrRegime, utrId), FakeRequest())
      status(result) mustBe Status.OK

      verify(mockService, times(1)).audit(argEq(true), argEq(utr), argEq("Opted out"))(any[HeaderCarrier])
    }

    "return OK and audit as succeeded with manual opt out for a nino if entityId is present, opted in and call to opt out successful" in new TestCase {
      when(mockRepo.findBy(argEq(nino))(any[ExecutionContext])).thenReturn(Future.successful(Some(entityWithNino)))
      when(mockService.optOut(argEq(entityWithNino))(any[HeaderCarrier], any[Request[AnyContent]]))
        .thenReturn(Future.successful(Results.Ok))

      private val result = call(controller.manualOptOut(ninoRegime, ninoId), FakeRequest())
      status(result) mustBe Status.OK

      verify(mockService, times(1)).audit(argEq(true), argEq(nino), argEq("Opted out"))(any[HeaderCarrier])
    }

    "return OK and audit as succeeded with manual opt out for a itsa if entityId is present, opted in and call to opt out successful" in new TestCase {
      when(mockRepo.findBy(argEq(itsa))(any[ExecutionContext])).thenReturn(Future.successful(Some(entityWithItsa)))
      when(mockService.optOut(argEq(entityWithItsa))(any[HeaderCarrier], any[Request[AnyContent]]))
        .thenReturn(Future.successful(Results.Ok))

      private val result = call(controller.manualOptOut(itsaRegime, itsaId), FakeRequest())
      status(result) mustBe Status.OK

      verify(mockService, times(1)).audit(argEq(true), argEq(itsa), argEq("Opted out"))(any[HeaderCarrier])
    }

    "return CONFLICT and audit as succeeded with already opted out if entityId is present but preference already opted out" in new TestCase {
      when(mockRepo.findBy(argEq(utr))(any[ExecutionContext])).thenReturn(Future.successful(Some(entityWithUtr)))
      when(mockService.optOut(argEq(entityWithUtr))(any[HeaderCarrier], any[Request[AnyContent]]))
        .thenReturn(Future.successful(Results.Conflict))

      private val result = call(controller.manualOptOut(saUtrRegime, utrId), FakeRequest())
      status(result) mustBe Status.CONFLICT

      verify(mockService, times(1)).audit(argEq(true), argEq(utr), argEq("Already opted out"))(any[HeaderCarrier])
      verify(mockService, times(1)).optOut(argEq(entityWithUtr))(any[HeaderCarrier], any[Request[AnyContent]])
    }

    "return NOT_FOUND and audit as failed with No Preference if entityId is present but no matching preference" in new TestCase {
      when(mockRepo.findBy(argEq(utr))(any[ExecutionContext])).thenReturn(Future.successful(Some(entityWithUtr)))
      when(mockService.optOut(argEq(entityWithUtr))(any[HeaderCarrier], any[Request[AnyContent]]))
        .thenReturn(Future.successful(Results.NotFound))

      private val result = call(controller.manualOptOut(saUtrRegime, utrId), FakeRequest())
      status(result) mustBe Status.NOT_FOUND

      verify(mockService, times(1)).audit(argEq(true), argEq(utr), argEq("No preference"))(any[HeaderCarrier])
      verify(mockService, times(1)).optOut(argEq(entityWithUtr))(any[HeaderCarrier], any[Request[AnyContent]])
    }

    "return INTERNAL_SERVER_ERROR if entityId is present and call to connector fails" in new TestCase {
      when(mockRepo.findBy(argEq(utr))(any[ExecutionContext])).thenReturn(Future.successful(Some(entityWithUtr)))
      when(mockService.optOut(argEq(entityWithUtr))(any[HeaderCarrier], any[Request[AnyContent]]))
        .thenReturn(Future.successful(Results.InternalServerError))

      private val result = call(controller.manualOptOut(saUtrRegime, utrId), FakeRequest())
      status(result) mustBe Status.INTERNAL_SERVER_ERROR

      verify(mockService, times(0)).audit(anyBoolean(), any[TaxIdWithName](), anyString())(any[HeaderCarrier])
    }

    "return PRECONDITION_FAILED and audit as failed with No EntityId if entityId is not found" in new TestCase {
      when(mockRepo.findBy(argEq(utr))(any[ExecutionContext])).thenReturn(Future.successful(None))

      private val result = call(controller.manualOptOut(saUtrRegime, utrId), FakeRequest())
      status(result) mustBe Status.PRECONDITION_FAILED

      verify(mockService, times(1)).audit(argEq(false), argEq(utr), argEq("No EntityId"))(any[HeaderCarrier])
      verify(mockService, times(0)).optOut(argEq(entityWithUtr))(any[HeaderCarrier], any[Request[AnyContent]])
    }

    "return NOT_FOUND without audit if regime not supported" in new TestCase {
      private val result = call(controller.manualOptOut("unknown-regime", "123456"), FakeRequest())
      status(result) mustBe Status.NOT_FOUND
      verify(mockService, times(0)).audit(anyBoolean(), any[TaxIdWithName](), anyString())(any[HeaderCarrier])
    }
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[EntityRepository].toInstance(mock[EntityRepository]))
      .overrides(bind[ManualOptOutService].toInstance(mock[ManualOptOutService]))
      .configure("metrics.enabled" -> "false")
      .build()

  trait TestCase {
    val saUtrRegime = "sa"
    val ninoRegime = "paye"
    val itsaRegime = "itsa"
    val utrId = "1234"
    val ninoId = "CE043355D"
    val itsaId = "testItsa"
    val utr = SaUtr(utrId)
    val nino = Nino(ninoId)
    val itsa = HmrcMtdItsa(itsaId)
    val entityWithUtr = Entity(EntityId("7654"), utr)
    val entityWithNino = Entity(EntityId("7600"), nino)
    val entityWithItsa = Entity(EntityId("7601"), itsa)
    val mockRepo: EntityRepository = app.injector.instanceOf[EntityRepository]
    val mockService: ManualOptOutService = app.injector.instanceOf[ManualOptOutService]
    val controller: ManualOptOutController = app.injector.instanceOf[ManualOptOutController]
    implicit val mat: Materializer = app.injector.instanceOf[Materializer]
  }

}
