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

import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Request }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr }
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.entityresolver.services.ManualOptOutService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

@Singleton
class ManualOptOutController @Inject() (
  entityRepository: EntityRepository,
  optOutService: ManualOptOutService,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def manualOptOut(regime: String, id: String): Action[AnyContent] = Action.async { implicit request =>
    createTaxId(regime, id)
      .map { errorOrTaxId =>
        errorOrTaxId
          .map { taxId =>
            processTaxId(taxId)
          }
          .getOrElse(
            Future
              .successful(InternalServerError(Json.obj("reason" -> s"unparseable request $regime/$id")))
          )
      }
      .getOrElse(
        Future.successful(NotFound(Json.obj("reason" -> s"The regime $regime supplied is not supported.")))
      )

  }

  private def processTaxId(
    taxId: TaxIdWithName
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]) = {
    def audit(transactionStatus: Boolean, reason: String) =
      optOutService.audit(transactionStatus, taxId, reason)

    entityRepository
      .findBy(taxId)
      .flatMap {
        case Some(entity) => optOutService.optOut(entity)
        case None         => Future.successful(PreconditionFailed)
      }
      .map { response =>
        response.header.status match {
          case OK                  => audit(transactionStatus = true, "Opted out")
          case CONFLICT            => audit(transactionStatus = true, "Already opted out")
          case NOT_FOUND           => audit(transactionStatus = true, "No preference")
          case PRECONDITION_FAILED => audit(transactionStatus = false, "No EntityId")
          case _                   =>
        }
        response
      }
  }

  private def createTaxId(regime: String, id: String): Option[Either[Throwable, TaxIdWithName]] =
    regime match {
      case "sa"   => Some(Right(SaUtr(id)))
      case "paye" => Some(Try(Nino(id)).toEither)
      case "itsa" => Some(Right(HmrcMtdItsa(id)))
      case _      => None
    }
}
