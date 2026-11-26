/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.controllers.testonly

import javax.inject.{ Inject, Named, Singleton }
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, ControllerComponents }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.entityresolver.config.GenericConnectorRegistry
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.audit.model.EventTypes.Succeeded
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AdminController @Inject() (
  @Named("appName") appName: String,
  entityRepository: EntityRepository,
  genericConnectorRegistry: GenericConnectorRegistry,
  cc: ControllerComponents,
  auditConnector: AuditConnector
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def auditEntityIdCreated(taxId: TaxIdWithName, entityId: EntityId, contextDescriptor: String): Future[AuditResult] =
    auditConnector.sendEvent(
      DataEvent(
        appName,
        auditType = Succeeded,
        tags = Map("transactionName" -> s"EntityId Created - $contextDescriptor"),
        detail = Map(taxId.name -> taxId.value, "entityId" -> entityId.value)
      )
    )

  def entityIdForUtr(utr: SaUtr): Action[AnyContent] = entityIdFor(utr)

  def entityIdForNino(nino: Nino): Action[AnyContent] = entityIdFor(nino)

  def validateEntityId(entityId: String): Action[AnyContent] = Action.async {
    entityRepository.findBy(EntityId(entityId)).map {
      case Some(_) => Ok
      case None    => NotFound
    }
  }

  def deleteAll(): Action[AnyContent] = Action.async { _ =>
    entityRepository.removeAll().map(result => Ok(Json.obj("deleted" -> result.getDeletedCount)))
  }

  def deletePreferenceByUtr(utr: SaUtr): Action[AnyContent] = deletePreference(utr)

  def deletePreferenceByNino(nino: Nino): Action[AnyContent] = deletePreference(nino)

  def deletePreference(utr: TaxIdWithName): Action[AnyContent] = Action.async { implicit request =>
    entityRepository.findBy(utr).flatMap {
      case Some(entity) =>
        genericConnectorRegistry
          .get("preferences")
          .doDelete(s"/preferences-admin/${entity.id.value}")
          .flatMap(_ => entityRepository.remove(entity.id))
          .map(_ => Ok)
      case _ => Future.successful(NoContent)
    }
  }

  private def entityIdFor(taxId: TaxIdWithName): Action[AnyContent] = Action.async {
    entityRepository.findBy(taxId).flatMap {
      case Some(entity) => Future.successful(Ok(entity.id.value))
      case _ =>
        entityRepository
          .confirm(Entity(EntityId.generate(), taxId))
          .map { entity =>
            auditEntityIdCreated(taxId, entity.id, "AdminController")
            Created(entity.id.value)
          }
    }
  }

  def deleteEntityRecord(entityId: String): Action[AnyContent] = Action.async {
    entityRepository.remove(EntityId(entityId)).map {
      case true  => Ok
      case false => NotFound
    }
  }
}
