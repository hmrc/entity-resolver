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
