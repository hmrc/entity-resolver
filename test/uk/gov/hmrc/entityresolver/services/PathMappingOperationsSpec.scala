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

import java.util.UUID
import org.apache.pekko.actor.ActorSystem
import com.google.inject.name.Names
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{ any, eq as argEq, same }
import org.mockito.Mockito.{ verifyNoInteractions, when }
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.{ BindingKey, QualifierInstance, bind }
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{ JsValue, Json, Writes }
import play.api.mvc.{ AnyContent, AnyContentAsJson, Request, Result }
import play.api.mvc.Results.*
import play.api.test.{ FakeHeaders, FakeRequest }
import play.api.test.Helpers.*
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.entityresolver.config.GenericConnectorRegistry
import uk.gov.hmrc.entityresolver.connectors.*
import uk.gov.hmrc.entityresolver.models.*
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads }
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.DataEvent
import utils.Generate
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.entityresolver.util.HeaderUtils

import scala.concurrent.{ ExecutionContext, Future }

class PathMappingOperationsSpec extends PlaySpec with GuiceOneAppPerTest with ScalaFutures with IntegrationPatience {

  implicit val system: ActorSystem = ActorSystem("test")

  "doGet" must {
    "translate taxId to entityId and pass request on" in new TestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr)
      val entity = Entity(expectedEntityId, currentTaxIds)
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(currentTaxIds))
      when(
        entityIdResolver
          .resolveAny(same(currentTaxIds), any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(Some(entity)))

      private val response = Status(OK)("body").withHeaders(HeaderUtils.deprecation)
      when(
        genericConnector
          .doGet[Result](argEq(queryString))(argEq(expectedDestinationUrl))(any[HeaderCarrier], any[HttpReads[Result]])
      )
        .thenReturn(Future.successful(response))

      pathMappingOps.doGet(pathMappingWithTaxIdsFromAuth).futureValue must be(response)
    }

    "fail to translate taxId to entityId and return NOT_FOUND when no existing entity" in new TestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr)
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(currentTaxIds))
      when(
        entityIdResolver
          .resolveAny(same(currentTaxIds), any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(None))

      private val mappingResponse = pathMappingOps.doGet(pathMappingWithTaxIdsFromAuth)
      mappingResponse.futureValue.header.status must be(NOT_FOUND)
      contentAsJson(mappingResponse) mustBe Json.obj("reason" -> "PREFERENCES_NOT_FOUND")

      verifyNoInteractions(genericConnector)
    }

    "just pass request on when no taxId" in new TestCase {
      private val response = Status(OK)("body").withHeaders(HeaderUtils.deprecation)
      when(
        genericConnector.doGet[Result](argEq(queryString))(argEq(pathMappingWithoutTaxId.destinationUrl))(
          any[HeaderCarrier],
          any[HttpReads[Result]]
        )
      )
        .thenReturn(Future.successful(response))

      pathMappingOps.doGet(pathMappingWithoutTaxId).futureValue must be(response)
    }

    "return UNAUTHORISED when no given taxId for current session" in new TestCase {
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier]))
        .thenReturn(Future.successful(Set.empty[TaxIdWithName]))

      pathMappingOps.doGet(pathMappingWithTaxIdsFromAuth).futureValue must be(
        Unauthorized.withHeaders(HeaderUtils.deprecation)
      )

      verifyNoInteractions(entityIdResolver, genericConnector)
    }

    "not call auth when the tax identifier is taken from the path" in new TestCase {
      private val pathMapping = pathMappingWithTaxIdFromPath

      when(
        entityIdResolver
          .resolveAny(any[Set[TaxIdWithName]], any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(Some(Entity(expectedEntityId, Set(utr)))))

      private val response = Status(OK)("body").withHeaders(HeaderUtils.deprecation)
      when(
        genericConnector
          .doGet[Result](argEq(queryString))(argEq(expectedDestinationUrl))(any[HeaderCarrier], any[HttpReads[Result]])
      )
        .thenReturn(Future.successful(response))

      pathMappingOps.doGet(pathMapping).futureValue must be(response)

      verifyNoInteractions(authConnector)
    }

    "return NOT_FOUND if id taken from the path does not have a corresponding entity" in new TestCase {
      private val pathMapping = pathMappingWithTaxIdFromPath

      when(
        entityIdResolver
          .resolveAny(any[Set[TaxIdWithName]], any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(None))

      private val getResponse = pathMappingOps.doGet(pathMapping)
      getResponse.futureValue.header.status mustBe NOT_FOUND
      contentAsJson(getResponse) mustBe Json.obj("reason" -> "PREFERENCES_NOT_FOUND")

      verifyNoInteractions(genericConnector)
      verifyNoInteractions(authConnector)
    }

    "passThroughId must be passed through with no update in entity-resolver" in new TestCase {
      val id = PassThroughId(UUID.randomUUID.toString)
      override val expectedDestinationUrl = s"/preferences/$id"
      private val pathMapping = pathMappingWithoutTaxId.copy(destinationUrl = expectedDestinationUrl)

      private val response = Status(OK)("body").withHeaders(HeaderUtils.deprecation)
      when(
        genericConnector
          .doGet[Result](argEq(queryString))(argEq(expectedDestinationUrl))(any[HeaderCarrier], any[HttpReads[Result]])
      )
        .thenReturn(Future.successful(response))

      pathMappingOps.doGet(pathMapping).futureValue must be(response)

      verifyNoInteractions(authConnector, entityIdResolver)
    }

    "audit the entityId against known ids if asked to" in new TestCase {

      private val nino = Generate.nino
      val currentTaxIds: Set[TaxIdWithName] = Set(utr, nino)
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(currentTaxIds))
      when(
        entityIdResolver
          .resolveAny(same(currentTaxIds), any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(Some(Entity(expectedEntityId, currentTaxIds))))

      private val dataEventCaptor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])
      when(auditConnector.sendEvent(dataEventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(AuditResult.Success))

      private val response = Status(OK)("body").withHeaders(HeaderUtils.deprecation)
      when(
        genericConnector
          .doGet[Result](argEq(queryString))(argEq(expectedDestinationUrl))(any[HeaderCarrier], any[HttpReads[Result]])
      )
        .thenReturn(Future.successful(response))

      pathMappingOps.doGet(pathMappingWithTaxIdsFromAuth.copy(auditIds = true)).futureValue must be(response)

      dataEventCaptor.getValue must have(
        Symbol("auditSource")(appName),
        Symbol("auditType")("TxSucceeded"),
        Symbol("tags")(
          Map(
            "transactionName" -> "EntityId Accessed"
          )
        ),
        Symbol("detail")(
          Map(
            "sautr"    -> utr.value,
            "nino"     -> nino.value,
            "entityId" -> expectedEntityId.value
          )
        )
      )
    }

    "not audit the entityId against known ids if explicitly not asked to" in new TestCase {

      private val nino = Generate.nino
      val currentTaxIds: Set[TaxIdWithName] = Set(utr, nino)
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(currentTaxIds))
      when(
        entityIdResolver
          .resolveAny(same(currentTaxIds), any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(Some(Entity(expectedEntityId, currentTaxIds))))

      private val response = Status(OK)("body").withHeaders(HeaderUtils.deprecation)
      when(
        genericConnector
          .doGet[Result](argEq(queryString))(argEq(expectedDestinationUrl))(any[HeaderCarrier], any[HttpReads[Result]])
      )
        .thenReturn(Future.successful(response))

      pathMappingOps.doGet(pathMappingWithTaxIdsFromAuth.copy(auditIds = false)).futureValue must be(response)

      verifyNoInteractions(auditConnector)
    }
  }

  "doPost" must {
    "translate taxId to entityId and pass request on" in new TestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr)
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(currentTaxIds))
      when(
        entityIdResolver
          .resolveAny(same(currentTaxIds), any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(Some(Entity(expectedEntityId, currentTaxIds))))

      private val response = Status(OK)("body").withHeaders(HeaderUtils.deprecation)
      when(
        genericConnector
          .doPost[Result](argEq(body), argEq(queryString))(argEq(expectedDestinationUrl))(
            any[HeaderCarrier],
            any[HttpReads[Result]],
            any[Writes[JsValue]]
          )
      )
        .thenReturn(Future.successful(response))

      pathMappingOps.doPost(pathMappingWithTaxIdsFromAuth).futureValue must be(response)
    }

    "just pass request on when no taxId" in new TestCase {
      private val response = Status(OK)("body").withHeaders(HeaderUtils.deprecation)
      when(
        genericConnector.doPost[Result](argEq(body), argEq(queryString))(
          argEq(pathMappingWithoutTaxId.destinationUrl)
        )(any[HeaderCarrier], any[HttpReads[Result]], any[Writes[JsValue]])
      ).thenReturn(Future.successful(response))

      pathMappingOps.doPost(pathMappingWithoutTaxId).futureValue must be(response)
    }

    "return UNAUTHORISED when no given taxId for current session" in new TestCase {
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier]))
        .thenReturn(Future.successful(Set.empty[TaxIdWithName]))

      pathMappingOps.doPost(pathMappingWithTaxIdsFromAuth).futureValue must be(
        Unauthorized.withHeaders(HeaderUtils.deprecation)
      )

      verifyNoInteractions(entityIdResolver, genericConnector)
    }
  }

  "doPut" must {
    "translate taxId to entityId and pass request on" in new TestCase {
      val currentTaxIds: Set[TaxIdWithName] = Set(utr)
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier])).thenReturn(Future.successful(currentTaxIds))
      when(
        entityIdResolver
          .resolveAny(same(currentTaxIds), any[Boolean])(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future.successful(Some(Entity(expectedEntityId, currentTaxIds))))

      private val response = Status(OK)("body").withHeaders(HeaderUtils.deprecation)

      when(
        genericConnector
          .doPut[Result](argEq(body), argEq(queryString))(argEq(expectedDestinationUrl))(
            any[HeaderCarrier],
            any[HttpReads[Result]],
            any[Writes[JsValue]]
          )
      )
        .thenReturn(Future.successful(response))

      pathMappingOps.doPut(pathMappingWithTaxIdsFromAuth).futureValue must be(response)
    }

    "just pass request on when no taxId" in new TestCase {
      private val response = Status(OK)("body").withHeaders(HeaderUtils.deprecation)
      when(
        genericConnector.doPut[Result](argEq(body), argEq(queryString))(
          argEq(pathMappingWithoutTaxId.destinationUrl)
        )(any[HeaderCarrier], any[HttpReads[Result]], any[Writes[JsValue]])
      ).thenReturn(Future.successful(response))

      pathMappingOps.doPut(pathMappingWithoutTaxId).futureValue must be(response)
    }

    "return UNAUTHORISED when no given taxId for current session" in new TestCase {
      when(authConnector.currentTaxIdentifiers(any[HeaderCarrier]))
        .thenReturn(Future.successful(Set.empty[TaxIdWithName]))

      pathMappingOps.doPut(pathMappingWithTaxIdsFromAuth).futureValue must be(
        Unauthorized.withHeaders(HeaderUtils.deprecation)
      )

      verifyNoInteractions(entityIdResolver, genericConnector)
    }
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure("appName" -> "testApp")
      .configure("metrics.enabled" -> "false")
      .configure("auditing.enabled" -> "true")
      .bindings(bind[GenericConnector].toInstance(mock[GenericConnector]))
      .bindings(bind[EntityIdResolver].toInstance(mock[EntityIdResolver]))
      .bindings(bind[TaxIdentifierRetriever].toInstance(mock[TaxIdentifierRetriever]))
      .bindings(bind[GenericConnectorRegistry].toInstance(mock[GenericConnectorRegistry]))
      .overrides(bind[AuditConnector].toInstance(mock[AuditConnector]))
      .build()

  trait TestCase {

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val entityIdResolver: EntityIdResolver = app.injector.instanceOf[EntityIdResolver]
    val genericConnector: GenericConnector = app.injector.instanceOf[GenericConnector]
    val authConnector: TaxIdentifierRetriever = app.injector.instanceOf[TaxIdentifierRetriever]
    val genericConnectorRegistry: GenericConnectorRegistry = app.injector.instanceOf[GenericConnectorRegistry]
    val auditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]

    val utr: SaUtr = Generate.utr
    val service = "service"

    val expectedEntityId: EntityId = EntityId.generate()
    val expectedDestinationUrl = s"url/$expectedEntityId"

    val body: JsValue = Json.parse("""{"test": "test"}""")
    val queryString = "queryString=test"
    implicit val request: Request[AnyContent] =
      FakeRequest("GET", s"url?$queryString", FakeHeaders(), AnyContentAsJson(body))

    when(genericConnectorRegistry.get(service)).thenReturn(genericConnector)

    val resolveIdsTrueFlag = true

    val pathMappingWithTaxIdsFromAuth: PathMapping = PathMapping(
      serviceName = service,
      taxIdExtraction = TaxIdExtraction(ReadIdsFromAuth, None),
      destinationUrl = s"url/%s",
      resolveIds = resolveIdsTrueFlag
    )

    val pathMappingWithTaxIdFromPath: PathMapping = PathMapping(
      serviceName = service,
      taxIdExtraction = TaxIdExtraction(ReadIdFromPath, Some(utr)),
      destinationUrl = s"url/%s",
      resolveIds = resolveIdsTrueFlag
    )

    val pathMappingWithoutTaxId: PathMapping = PathMapping(
      serviceName = service,
      taxIdExtraction = TaxIdExtraction(PassThrough, None),
      destinationUrl = s"url/%s",
      resolveIds = resolveIdsTrueFlag
    )

    val pathMappingOps: PathMappingOperations = app.injector.instanceOf[PathMappingOperations]

    val appName: String =
      app.injector.instanceOf(BindingKey(classOf[String], Some(QualifierInstance(Names.named("appName")))))
  }

}
