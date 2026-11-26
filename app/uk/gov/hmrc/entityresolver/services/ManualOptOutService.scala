/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.services

import play.api.mvc.{ AnyContent, Request, Result, Results }
import play.api.{ Logger, mvc }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.entityresolver.connectors.PreferencesConnector
import uk.gov.hmrc.entityresolver.models.{ Entity, PreferenceStatus }
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.audit.model.EventTypes.*

import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class ManualOptOutService @Inject() (
  preferencesConnector: PreferencesConnector,
  auditConnector: AuditConnector,
  @Named("appName") appName: String
)(implicit ec: ExecutionContext) {

  private val logger = Logger(getClass.getName)

  def optOut(entity: Entity)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    val eventualMaybePreferenceStatus: Future[Option[PreferenceStatus]] =
      preferencesConnector.getPreferencesForOptOut(entity.id)

    eventualMaybePreferenceStatus
      .flatMap {
        case Some(preferenceStatus) =>
          for {
            maybeGenericResult    <- optOutTerms("generic", preferenceStatus.maybeGeneric, entity)
            maybeTaxCreditsResult <- optOutTerms("taxCredits", preferenceStatus.maybeTaxCredits, entity)
          } yield calculateOptOutResult(maybeGenericResult, maybeTaxCreditsResult)
        case _ => Future.successful(Results.NotFound)
      }
      .recover { case error =>
        logger.error(s"failed getting preference status for ${entity.id}", error)
        Results.InternalServerError
      }

  }

  private def optOutTerms(
    terms: String,
    maybeOptedIn: Option[Boolean],
    entity: Entity
  )(implicit hc: HeaderCarrier): Future[Option[Boolean]] =
    maybeOptedIn match {
      case Some(optedIn) =>
        if (optedIn) {
          preferencesConnector.optOutTerms(entity.id, terms).map(Some.apply)
        } else {
          Future.successful(None)
        }
      case None =>
        Future.successful(None)
    }

  private def calculateOptOutResult(
    maybeGenericResult: Option[Boolean],
    maybeTaxCreditsResult: Option[Boolean]
  ): mvc.Results.Status =
    if (maybeGenericResult.contains(true) || maybeTaxCreditsResult.contains(true)) {
      Results.Ok
    } else {
      Results.Conflict
    }

  def audit(wasSuccessful: Boolean, taxId: TaxIdWithName, reason: String)(implicit hc: HeaderCarrier): Future[Any] =
    auditConnector.sendEvent(
      DataEvent(
        auditSource = appName,
        auditType = if (wasSuccessful) Succeeded else Failed,
        tags = Map("transactionName" -> s"Manual Opt-Out: $reason"),
        detail = Map(taxId.name -> taxId.value)
      )
    )
}
