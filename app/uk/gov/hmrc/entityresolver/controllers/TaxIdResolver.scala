/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.controllers

import javax.inject.{ Inject, Singleton }
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Result, Results }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr }
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Instant
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class TaxIdResolver @Inject() (entityRepository: EntityRepository, cc: ControllerComponents)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {

  def findBy(entityId: EntityId): Action[AnyContent] = Action.async {
    resultFrom(entityRepository.findBy(entityId))
  }

  def byUtr(utr: SaUtr): Action[AnyContent] = findByTaxIdentifier(utr)

  def byNino(nino: Nino): Action[AnyContent] = findByTaxIdentifier(nino)

  def byItsa(itsa: HmrcMtdItsa): Action[AnyContent] = findByTaxIdentifier(itsa)

  def findByTaxIdentifier(taxID: TaxIdWithName): Action[AnyContent] = Action.async {
    resultFrom(entityRepository.findBy(taxID))
  }

  private def getDeprecationDate =
    Instant.parse("2024-12-01T00:00:00.00Z").getEpochSecond

  private def getDeprecationHeader =
    "Deprecation" -> s"@$getDeprecationDate"

  private def resultFrom(input: Future[Option[Entity]])(implicit ec: ExecutionContext): Future[Result] = input.map {
    case Some(e) =>
      Results.Ok(Json.toJson(e)).withHeaders(getDeprecationHeader)
    case _ =>
      NotFound.withHeaders(getDeprecationHeader)
  }
}
