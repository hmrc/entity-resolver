/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.controllers

import play.api.Logger
import play.api.mvc.Results.BadRequest
import play.api.mvc.{ Action, AnyContent, ControllerComponents, QueryStringBindable, Results }
import uk.gov.hmrc.entityresolver.binders.GetEntityParamsBinder
import uk.gov.hmrc.entityresolver.services.EntityService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

class EntityController @Inject() (
  entityService: EntityService,
  cc: ControllerComponents
) extends BackendController(cc) {

  private val logger = Logger(getClass.getName)

  // NOTE ResolveIds option is only used for optin/optout and email langauge.
  // They were originally specified by the path-mapping.conf.
  //      The purpose is to create a new entity if one does not already exist.
  def findEntity(b: GetEntityParamsBinder): Action[AnyContent] = Action.async { implicit request =>
    logger.debug(s"findEntity with parameters: $b")

    (b.taxRegime, b.taxId, b.entityId, b.resolve) match {
      case (None, None, Some(eid), None)    => entityService.findByEntityId(eid)
      case (Some(tr), Some(ti), None, None) => entityService.findByProvidedTaxId(tr, ti)
      case (None, None, None, None)         => entityService.findByTokenTaxId(true, b.getResolveIds)
      case (None, None, None, Some(res))    => entityService.findByTokenTaxId(res, b.getResolveIds)
      case _                                => Future.successful(BadRequest("Invalid querystring format"))
    }
  }

  // For ITSA optin/optout/email-language
  // Always resolves, resolveIds always true
  def findRegimeEntity(): Action[AnyContent] = Action.async { implicit request =>
    logger.debug(s"findRegimeEntity")
    entityService.findByTokenTaxId(resolve = true, resolveIds = true)
  }
}
