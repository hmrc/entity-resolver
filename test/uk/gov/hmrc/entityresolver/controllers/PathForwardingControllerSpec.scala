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
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.{ Application, Logger }
import play.api.http.Status
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.mvc.Results.Ok
import play.api.mvc.{ AnyContent, Request }
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.entityresolver.connectors.PreferencesConnector
import uk.gov.hmrc.entityresolver.models.{ PathMapping, ReadIdsFromAuth, TaxIdExtraction, TaxIdExtractionMethod }
import uk.gov.hmrc.entityresolver.services.{ EntityIdResolver, PathMappingOperations }
import utils.LogCapturing
import ch.qos.logback.classic.{ Level, Logger => LogbackLogger }
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.lock.{ LockRepository, MongoLockRepository }
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PathForwardingControllerSpec extends PlaySpec with GuiceOneAppPerTest with ScalaFutures with MockitoSugar {

  val pathMappingOperationsMock: PathMappingOperations = mock[PathMappingOperations]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[PathMappingOperations].toInstance(pathMappingOperationsMock))
      .overrides(bind[EntityIdResolver].toInstance(mock[EntityIdResolver]))
      .overrides(bind[PreferencesConnector].toInstance(mock[PreferencesConnector]))
      .configure("metrics.enabled" -> "false")
      .build()

  "PathForwardingController" should {

    "return OK when the path forwarding is successful - doGet" in new TestCase {
      val result = call(controller.doGet("preferences/pending-email"), FakeRequest())
      status(result) mustBe Status.OK
    }

    "return NOT_FOUND when the path forwarding is not successful - doGet" in new TestCase {
      val result = call(controller.doGet("test/email"), FakeRequest())
      status(result) mustBe Status.NOT_FOUND
    }

    "return OK when the path forwarding is successful - doPost" in new TestCase {
      val result = call(controller.doPost("preferences/pending-email"), FakeRequest())
      status(result) mustBe Status.OK
    }

    "return NOT_FOUND when the path forwarding is not successful - doPost" in new TestCase {
      val result = call(controller.doPost("test/email"), FakeRequest())
      status(result) mustBe Status.NOT_FOUND
    }

    "return OK when the path forwarding is successful - doPut" in new TestCase {
      val result = call(controller.doPut("preferences/pending-email"), FakeRequest())
      status(result) mustBe Status.OK
    }

    "return NOT_FOUND when the path forwarding is not successful - doPut" in new TestCase {
      val result = call(controller.doPut("test/email"), FakeRequest())
      status(result) mustBe Status.NOT_FOUND
    }

    "throw exception if method not found" in new TestCase {
      intercept[RuntimeException] {
        controller.findMethod("BadMethod")
      }
    }
  }

  trait TestCase {
    val controller: PathForwardingController = app.injector.instanceOf[PathForwardingController]
    val expectedPathMapping: PathMapping = PathMapping(
      "/preferences/%s/pending-email",
      TaxIdExtraction(TaxIdExtractionMethod(ReadIdsFromAuth.name), None),
      "preferences"
    )

    implicit val mat: Materializer = app.injector.instanceOf[Materializer]

    when(
      pathMappingOperationsMock.doGet(ArgumentMatchers.eq(expectedPathMapping))(
        ArgumentMatchers.any[Request[AnyContent]]()
      )
    ).thenReturn(Future.successful(Ok("")))
    when(
      pathMappingOperationsMock.doPut(ArgumentMatchers.eq(expectedPathMapping))(
        ArgumentMatchers.any[Request[AnyContent]]()
      )
    ).thenReturn(Future.successful(Ok("")))
    when(
      pathMappingOperationsMock.doPost(ArgumentMatchers.eq(expectedPathMapping))(
        ArgumentMatchers.any[Request[AnyContent]]()
      )
    ).thenReturn(Future.successful(Ok("")))
  }
}

class PathForwardingControllerBadConfigSpec
    extends PlaySpec with GuiceOneAppPerTest with ScalaFutures with MockitoSugar with LogCapturing with MongoSupport {
  val pathMappingOperationsMock: PathMappingOperations = mock[PathMappingOperations]
  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[PathMappingOperations].toInstance(pathMappingOperationsMock))
      .overrides(bind[EntityIdResolver].toInstance(mock[EntityIdResolver]))
      .overrides(bind[PreferencesConnector].toInstance(mock[PreferencesConnector]))
      .overrides(bind[LockRepository].toInstance(new MongoLockRepository(mongoComponent, new CurrentTimestampSupport)))
      .configure("pathMapping" -> "false")
      .build()

  "PathForwardingControllerBadConfig" should {

    "return NOT FOUND when the configuration cannot be found - doGet" in new TestCase {
      val logger = Logger(controller.getClass).underlyingLogger.asInstanceOf[LogbackLogger]
      withCaptureOfLoggingFrom(logger) { logEvents =>
        val result = call(controller.doGet("preferences/pending-email"), FakeRequest())
        logEvents.head.getLevel must be(Level.ERROR)
        status(result) mustBe Status.NOT_FOUND
      }

    }
  }

  trait TestCase {
    val controller: PathForwardingController = app.injector.instanceOf[PathForwardingController]

    implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  }
}
