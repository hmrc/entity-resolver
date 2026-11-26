/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{ Request, * }
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.entityresolver.connectors.PreferencesConnector
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId, PreferenceStatus }
import uk.gov.hmrc.http.*
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.{ ExecutionContext, Future }

class ManualOptOutServiceSpec extends PlaySpec with GuiceOneAppPerTest with ScalaFutures with MockitoSugar {

  "ManualOptOutService" must {
    "return OK when get preferences returns opted-in for Generic and TaxCredits Unknown then opt out the user" in new TestCase {
      when(mockPreferencesConnector.getPreferencesForOptOut(entityId))
        .thenReturn(Future.successful(Some(PreferenceStatus(maybeGeneric = Some(true), maybeTaxCredits = None))))

      when(mockPreferencesConnector.optOutTerms(entityId, "generic"))
        .thenReturn(Future.successful(true))

      manualOptOutService.optOut(entity).futureValue mustBe Results.Ok
    }

    "return Conflict when get preferences returns opted-out from Generic and TaxCredits Unknown then it should return conflicted" in new TestCase {
      when(mockPreferencesConnector.getPreferencesForOptOut(entityId))
        .thenReturn(Future.successful(Some(PreferenceStatus(maybeGeneric = Some(false), maybeTaxCredits = None))))

      manualOptOutService.optOut(entity).futureValue mustBe Results.Conflict

    }

    "return OK when get preferences returns opted-in for TaxCredits and Generic then opt out the user" in new TestCase {
      when(mockPreferencesConnector.getPreferencesForOptOut(entityId))
        .thenReturn(Future.successful(Some(PreferenceStatus(maybeGeneric = Some(true), maybeTaxCredits = Some(true)))))

      when(mockPreferencesConnector.optOutTerms(entityId, "generic"))
        .thenReturn(Future.successful(true))

      when(mockPreferencesConnector.optOutTerms(entityId, "taxCredits"))
        .thenReturn(Future.successful(true))

      manualOptOutService.optOut(entity).futureValue mustBe Results.Ok

    }

    "return OK when get preferences returns opted-in for Generic only then opt out the user" in new TestCase {
      when(mockPreferencesConnector.getPreferencesForOptOut(entityId))
        .thenReturn(Future.successful(Some(PreferenceStatus(maybeGeneric = Some(true), maybeTaxCredits = Some(false)))))

      when(mockPreferencesConnector.optOutTerms(entityId, "generic"))
        .thenReturn(Future.successful(true))

      manualOptOutService.optOut(entity).futureValue mustBe Results.Ok
    }

    "return OK when get preferences returns opted-in for TaxCredits only then opt out the user" in new TestCase {
      when(mockPreferencesConnector.getPreferencesForOptOut(entityId))
        .thenReturn(Future.successful(Some(PreferenceStatus(maybeGeneric = Some(false), maybeTaxCredits = Some(true)))))

      when(mockPreferencesConnector.optOutTerms(entityId, "taxCredits"))
        .thenReturn(Future.successful(true))

      manualOptOutService.optOut(entity).futureValue mustBe Results.Ok
    }

    "return NOT_FOUND when get preferences returns not found" in new TestCase {
      when(mockPreferencesConnector.getPreferencesForOptOut(entityId))
        .thenReturn(Future.successful(None))

      manualOptOutService.optOut(entity).futureValue mustBe Results.NotFound
    }

    "return CONFLICT when get preferences returns opted-out for both" in new TestCase {
      when(mockPreferencesConnector.getPreferencesForOptOut(entityId))
        .thenReturn(
          Future.successful(Some(PreferenceStatus(maybeGeneric = Some(false), maybeTaxCredits = Some(false))))
        )

      manualOptOutService.optOut(entity).futureValue mustBe Results.Conflict
    }

    "return INTERNAL_SERVER_ERROR when get preferences fails" in new TestCase {
      when(mockPreferencesConnector.getPreferencesForOptOut(entityId))
        .thenReturn(
          Future.failed(new InternalServerException("Preference not found"))
        )

      manualOptOutService.optOut(entity).futureValue mustBe Results.InternalServerError
    }

    "return INTERNAL_SERVER_ERROR when get preferences returns opted-in then opt out fails" in new TestCase {
      when(mockPreferencesConnector.getPreferencesForOptOut(entityId))
        .thenReturn(
          Future.successful(Some(PreferenceStatus(maybeGeneric = Some(true), maybeTaxCredits = Some(true))))
        )

      when(mockPreferencesConnector.optOutTerms(entityId, "generic"))
        .thenReturn(Future.failed(new RuntimeException("error")))

      manualOptOutService.optOut(entity).futureValue mustBe Results.InternalServerError

    }

    "audit TxSucceeded for UTR" in new AuditTestCase {
      await(manualOptOutService.audit(wasSuccessful = true, utr, "Opted out"))

      dataEventCaptor.getValue must have(
        Symbol("auditSource")("entity-resolver"),
        Symbol("auditType")("TxSucceeded"),
        Symbol("tags")(
          Map(
            "transactionName" -> "Manual Opt-Out: Opted out"
          )
        ),
        Symbol("detail")(
          Map(
            "sautr" -> utr.value
          )
        )
      )
    }
    "audit TxSucceeded for NINO" in new AuditTestCase {
      await(manualOptOutService.audit(wasSuccessful = true, nino, "Opted out"))

      dataEventCaptor.getValue must have(
        Symbol("auditSource")("entity-resolver"),
        Symbol("auditType")("TxSucceeded"),
        Symbol("tags")(
          Map(
            "transactionName" -> "Manual Opt-Out: Opted out"
          )
        ),
        Symbol("detail")(
          Map(
            "nino" -> nino.value
          )
        )
      )
    }

    "audit UTR Failed if failed to opt out" in new AuditTestCase {
      await(manualOptOutService.audit(wasSuccessful = false, utr, "No preference"))

      dataEventCaptor.getValue must have(
        Symbol("auditSource")("entity-resolver"),
        Symbol("auditType")("TxFailed"),
        Symbol("tags")(
          Map(
            "transactionName" -> "Manual Opt-Out: No preference"
          )
        ),
        Symbol("detail")(
          Map(
            "sautr" -> utr.value
          )
        )
      )
    }

    "audit NINO Failed if failed to opt out" in new AuditTestCase {
      await(manualOptOutService.audit(wasSuccessful = false, nino, "No preference"))

      dataEventCaptor.getValue must have(
        Symbol("auditSource")("entity-resolver"),
        Symbol("auditType")("TxFailed"),
        Symbol("tags")(
          Map(
            "transactionName" -> "Manual Opt-Out: No preference"
          )
        ),
        Symbol("detail")(
          Map(
            "nino" -> nino.value
          )
        )
      )
    }
  }

  trait AuditTestCase extends TestCase {
    val dataEventCaptor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])
    when(mockAuditConnector.sendEvent(dataEventCaptor.capture())(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(AuditResult.Success))
  }

  trait TestCase {
    val entityId = EntityId("1234")
    val utr: SaUtr = SaUtr("5678")
    val nino = Nino("CE043355D")
    val entity = Entity(entityId, utr)

    implicit val hc: HeaderCarrier = HeaderCarrier()
    implicit val request: Request[AnyContent] = FakeRequest()

    val manualOptOutService: ManualOptOutService = app.injector.instanceOf[ManualOptOutService]
    val mockPreferencesConnector: PreferencesConnector = app.injector.instanceOf[PreferencesConnector]
    val mockAuditConnector: AuditConnector = app.injector.instanceOf[AuditConnector]
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[PreferencesConnector].toInstance(mock[PreferencesConnector]))
      .overrides(bind[AuditConnector].toInstance(mock[AuditConnector]))
      .configure("appName" -> "entity-resolver")
      .configure("metrics.enabled" -> "false")
      .build()

}
