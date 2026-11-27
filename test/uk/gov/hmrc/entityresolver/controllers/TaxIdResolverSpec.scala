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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.entityresolver.models._
import uk.gov.hmrc.entityresolver.repository.EntityRepository

import scala.concurrent.{ ExecutionContext, Future }

class TaxIdResolverSpec extends PlaySpec with GuiceOneAppPerTest with MockitoSugar with ScalaFutures {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[EntityRepository].toInstance(mock[EntityRepository]))
      .configure("metrics.enabled" -> "false")
      .build()

  "TaxIdResolver" should {
    val request = FakeRequest()
    "findBy entityId return Not Found if no entity" in new TestCase {
      when(entityRepositoryMock.findBy(any[EntityId])(any[ExecutionContext]))
        .thenReturn(Future.successful(Option.empty[Entity]))
      val result = call(taxidResolver.findBy(entityId), request)
      status(result) must be(NOT_FOUND)
    }
    "findBy entityId return entity" in new TestCase {
      when(entityRepositoryMock.findBy(any[EntityId])(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(entity)))
      val result = call(taxidResolver.findBy(entityId), request)
      status(result) must be(OK)
    }
    "findBy sautr return entity" in new TestCase {
      when(entityRepositoryMock.findBy(any[TaxIdWithName])(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(entity)))
      val result = call(taxidResolver.byUtr(utr), request)
      status(result) must be(OK)
    }
    "findBy nino return entity" in new TestCase {
      when(entityRepositoryMock.findBy(any[TaxIdWithName])(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(entity)))
      val result = call(taxidResolver.byNino(nino), request)
      status(result) must be(OK)
    }
    "findBy itsa return entity" in new TestCase {
      when(entityRepositoryMock.findBy(any[TaxIdWithName])(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(entity)))
      val result = call(taxidResolver.byItsa(itsa), request)
      status(result) must be(OK)
    }
  }

  trait TestCase {
    val utrId = "1234"
    val ninoId = "CE043355D"
    val itsaId = "testItsa"
    val utr = SaUtr(utrId)
    val nino = Nino(ninoId)
    val itsa = HmrcMtdItsa(itsaId)
    val entityId = EntityId("some val")
    val entity = Entity(entityId, Set[TaxIdWithName](utr))
    val taxidResolver: TaxIdResolver = app.injector.instanceOf[TaxIdResolver]
    val entityRepositoryMock: EntityRepository = app.injector.instanceOf[EntityRepository]
    implicit val materializer: Materializer = app.injector.instanceOf[Materializer]
  }
}
