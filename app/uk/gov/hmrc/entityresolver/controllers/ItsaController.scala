/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.controllers

import play.api.Logging
import play.api.libs.json.*
import play.api.mvc.{ Action, ControllerComponents, Result }
import uk.gov.hmrc.auth.core.{ AuthConnector, AuthorisedFunctions }
import uk.gov.hmrc.domain.HmrcMtdItsa
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.entityresolver.connectors.PreferencesConnector
import uk.gov.hmrc.entityresolver.models.*
import uk.gov.hmrc.entityresolver.models.ItsaUpdateResult.*
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.entityresolver.services.PathMappingOperations
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.audit.model.EventTypes.{ Failed, Succeeded }
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{ Inject, Named }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class ItsaController @Inject() (
  entityRepository: EntityRepository,
  preferencesConnector: PreferencesConnector,
  auditConnector: AuditConnector,
  cc: ControllerComponents,
  @Named("appName") appName: String
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) with Logging {

  def updateItsaId(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[ItsaUpdateRequest] match {
      case JsSuccess(updateRequest, _) =>
        processUpdate(updateRequest)
          .map {
            case UpdateSuccess(p) =>
              Ok(Json.obj("response" -> "MTD ITSA ID value updated successfully", "preference" -> p))
            case UpdateAlreadyExists =>
              Ok(Json.obj("response" -> "NotModified", "details" -> "ItsaId is already updated"))
            case EntityNotFound =>
              NotFound(
                Json.obj("error" -> "Entity not found", "details" -> "No matching record for provided NINO and UTR")
              )
            case PreferencesNotFound =>
              NotFound(
                Json.obj(
                  "error"   -> "Preferences not found",
                  "details" -> "No matching record for provided NINO and UTR"
                )
              )

            case MultipleEntitiesFound =>
              Conflict(
                Json.obj(
                  "error"   -> "Multiple records found",
                  "details" -> "Multiple records match the provided identifiers; cannot safely update"
                )
              )
            case ItsaIdConflict =>
              Conflict(
                Json.obj(
                  "error"   -> "Invalid request",
                  "details" -> "ItsaId is linked to different taxIds"
                )
              )
          }
          .recover { case ex =>
            logger.error("Unexpected error during ITSA update", ex)
            InternalServerError(
              Json.obj(
                "error"   -> "Unexpected error occurred",
                "details" -> "An unexpected error occurred. Please try again later."
              )
            )
          }

      case JsError(errors) =>
        val errorMessages = errors.flatMap { case (path, validationErrors) =>
          validationErrors.map(e => s"${path.toJsonString}: ${e.message}")
        }
        auditItsaUpdate(taxIds = Set.empty[TaxIdWithName], error = Some(s"${errorMessages.mkString}"))
        Future.successful(BadRequest(Json.obj("error" -> "Payload validation failed", "details" -> errorMessages)))
    }
  }

  def processUpdate(request: ItsaUpdateRequest)(implicit hc: HeaderCarrier): Future[ItsaUpdateResult] = {
    val taxIds = Set(request.nino, request.sautr)

    entityRepository.findByAny(taxIds).flatMap {
      case Nil =>
        auditItsaUpdate(request.taxIds, "<N/A>", Some(EntityNotFound.toString))
        Future.successful(EntityNotFound)

      case entity :: Nil =>
        logger.info(s"Fetched entity record for ID: ${entity.id}")
        preferencesConnector.getPreferences(entity.id).flatMap {
          case Some(preferences) => handleEntityUpdate(entity.id, request, preferences.json)
          case _ =>
            auditItsaUpdate(request.taxIds, entity.id.value, Some(PreferencesNotFound.toString))
            Future.successful(PreferencesNotFound)
        }

      case entities =>
        val errMessage = s"Multiple entity records found for tax IDs: $taxIds"
        logger.info(errMessage)
        auditItsaUpdate(request.taxIds, entities.mkString, Some(errMessage))
        Future.successful(MultipleEntitiesFound)
    }
  }

  private def handleEntityUpdate(
    entityId: EntityId,
    request: ItsaUpdateRequest,
    preferences: JsValue
  ): Future[ItsaUpdateResult] =
    entityRepository.findBy(request.mtditsaid).flatMap {
      case None =>
        entityRepository
          .updateTaxIds(entityId, request.taxIds)
          .map { _ =>
            auditItsaUpdate(request.taxIds, entityId.value)
            UpdateSuccess(preferences)
          }

      case Some(existingEntity) if existingEntity.id == entityId =>
        auditItsaUpdate(request.taxIds, existingEntity.id.value, Some("ItsaId is already updated"))
        Future.successful(UpdateAlreadyExists)

      case Some(e) =>
        auditItsaUpdate(request.taxIds, entityId.value, Some(s"$ItsaIdConflict - ${e.id}"))
        Future.successful(ItsaIdConflict)
    }
  private def auditItsaUpdate(
    taxIds: Set[TaxIdWithName],
    entityId: String = "",
    error: Option[String] = None
  ): Future[AuditResult] = {

    val detail = taxIds.map(taxId => taxId.name -> taxId.value).toMap ++ Map("entityId" -> entityId)

    val (auditType, transactionName, details) = error.fold((Succeeded, "ItsaId Update Succeeded", detail)) { e =>
      (Failed, "ItsaId Update Failed", detail + ("error" -> e))
    }

    auditConnector.sendEvent(
      DataEvent(
        appName,
        auditType = auditType,
        tags = Map("transactionName" -> transactionName),
        detail = details
      )
    )
  }

}
