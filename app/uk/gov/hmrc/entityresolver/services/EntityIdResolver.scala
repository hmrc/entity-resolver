/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.services

import play.api.Logger
import play.api.libs.json.{ Format, Json }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ Nino, SaUtr, TaxIds }
import uk.gov.hmrc.entityresolver.connectors.PreferencesConnector
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.EventTypes.Succeeded
import uk.gov.hmrc.play.audit.model.{ DataEvent, EventTypes, ExtendedDataEvent }

import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EntityIdResolver @Inject() (
  @Named("appName") appName: String,
  entityRepository: EntityRepository,
  preferencesConnector: PreferencesConnector,
  auditConnector: AuditConnector,
  generateEntityId: () => EntityId
)(implicit ec: ExecutionContext) {

  import TaxIds._

  implicit val formats: Format[TaxIds] = TaxIds.format(defaultSerialisableIds: _*)

  private val logger = Logger(getClass.getName)

  def auditAs(caseName: String, lookupResult: LookupResult, authTaxIds: Set[TaxIdWithName])(
    entity: Entity
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Entity = {

    auditConnector.sendExtendedEvent(
      ExtendedDataEvent(
        appName,
        EventTypes.Succeeded,
        tags = hc.toAuditTags(caseName, "N/A") + ("transactionName" -> "conflict"),
        detail = Json.obj(
          "case"                -> caseName,
          "userLoggedInAs"      -> Json.toJson(TaxIds(authTaxIds)),
          "conflictingEntities" -> Json.toJson(Seq(lookupResult.masterEntity, lookupResult.conflictingEntity).flatten)
        )
      )
    )
    entity
  }

  def auditEntityIdCreated(
    taxIds: Set[TaxIdWithName],
    entity: Entity,
    contextDescriptor: String
  ): Future[AuditResult] =
    auditConnector.sendEvent(
      DataEvent(
        appName,
        auditType = Succeeded,
        tags = Map("transactionName" -> s"EntityId Created - $contextDescriptor"),
        detail = taxIds.map(taxId => taxId.name -> taxId.value).toMap ++ Map("entityId" -> entity.id.value)
      )
    )

  def resolveAny(taxIds: Set[TaxIdWithName], resolveIds: Boolean)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Entity]] = {

    import ConflictResolutionUtils._

    def resolveConflictOnSingleEntityAndReturnTheNewOne(
      entity: Entity,
      authTaxIds: Set[TaxIdWithName]
    ): Future[Entity] =
      for {
        _ <- removeAuthIdsFromConflictingEntity(entity, authTaxIds)
        created <- entityRepository.confirm(Entity(EntityId.generate(), authTaxIds)) map (ent => {
                     auditEntityIdCreated(taxIds, ent, "Resolving On Conflict")
                     ent
                   })
      } yield created

    def removeAuthIdsFromConflictingEntity(entity: Entity, authTaxIds: Set[TaxIdWithName]) = {
      val newTaxIDs = entity.taxIdentifiers.diff(authTaxIds)
      entityRepository.updateTaxIds(entity.id, newTaxIDs, overrideIds = true)
    }

    def updateTaxIds(entity: Entity): Future[Entity] =
      for {
        _             <- preferencesConnector.updated(entity.id)
        updatedEntity <- entityRepository.updateTaxIds(entity.id, taxIds).map(_ => entity)
      } yield updatedEntity

    def resolveOnEmpty: Future[Option[Entity]] =
      if (resolveIds) {
        logger.warn("ResolveIds is enabled, creating a new entity")
        entityRepository.confirm(Entity(generateEntityId(), taxIds)) map (entity => {
          auditEntityIdCreated(taxIds, entity, "Resolving On Empty")
          Some(entity)
        })
      } else {
        logger.warn("ResolveIds is disabled, returning None")
        Future.successful(None)
      }

    // Please see DC-324 for the CASEs definitions
    def resolve(entities: List[Entity]): Future[Option[Entity]] =
      LookupResult(entities, taxIds) match {
        case lr @ LookupResult(Some(masterEntity), Some(conflictingEntity), true)
            if findUtr(conflictingEntity.taxIdentifiers).isDefined =>
          removeAuthIdsFromConflictingEntity(conflictingEntity, taxIds).map(_ =>
            masterEntity
          ) flatMap updateTaxIds map (entity => Some(auditAs("1", lr, taxIds)(entity)))

        case lr @ LookupResult(Some(masterEntity), Some(conflictingEntity), true) =>
          entityRepository.remove(conflictingEntity.id).map(_ => masterEntity) flatMap updateTaxIds map (entity =>
            Some(auditAs("3", lr, taxIds)(entity))
          )

        case lr @ LookupResult(None, Some(conflictingEntity), true)
            if findUtr(conflictingEntity.taxIdentifiers).isDefined && findUtr(taxIds)
              .fold(false)(utrInAuth => !conflictingEntity.taxIdentifiers.contains(utrInAuth)) =>
          resolveConflictOnSingleEntityAndReturnTheNewOne(conflictingEntity, taxIds) map (entity =>
            Some(auditAs("2", lr, taxIds)(entity))
          )

        case lr @ LookupResult(None, Some(conflictingEntity), true) if findUtr(taxIds).isDefined =>
          Future.successful(conflictingEntity) flatMap updateTaxIds map (entity =>
            Some(auditAs("4", lr, taxIds)(entity))
          )

        case lr @ LookupResult(None, Some(conflictingEntity), true) =>
          Future.successful(conflictingEntity) map (entity => Some(auditAs("7", lr, taxIds)(entity)))

        case lr @ LookupResult(Some(masterEntity), None, true) if taxIds.size > masterEntity.taxIdentifiers.size =>
          Future.successful(masterEntity) flatMap updateTaxIds map (entity => Some(auditAs("5", lr, taxIds)(entity)))

        case lr @ LookupResult(Some(masterEntity), None, true) if taxIds.size < masterEntity.taxIdentifiers.size =>
          Future.successful(masterEntity) map (entity => Some(auditAs("8", lr, taxIds)(entity)))

        case lr @ LookupResult(Some(masterEntity), None, true) =>
          Future.successful(masterEntity) flatMap updateTaxIds map (entity => Some(auditAs("6", lr, taxIds)(entity)))

        case LookupResult(Some(masterEntity), None, false) =>
          Future.successful(Some(masterEntity)) // NO AUDITING

        case LookupResult(None, Some(conflictingEntity), false) =>
          Future.successful(Some(conflictingEntity)) // NO AUDITING

        case _ =>
          Future.successful(None)
      }

    val result = entityRepository.findByAny(taxIds)
    result flatMap { entities =>
      if (entities.isEmpty) {
        resolveOnEmpty
      } else {
        resolve(entities.toList)
      }
    }
  }

  private def resolveTaxIdentifierForEntityId(entityId: EntityId)(implicit
    ec: ExecutionContext
  ): Future[Option[Set[TaxIdWithName]]] =
    entityRepository.findBy(entityId).map(_.map(_.taxIdentifiers))

  def resolveUtrForEntityId(entityId: EntityId)(implicit ec: ExecutionContext): Future[Option[SaUtr]] =
    resolveTaxIdentifierForEntityId(entityId) map {
      case Some(taxIds) => taxIds.collectFirst { case t: SaUtr => t }
      case _            => None
    }

  def resolveNinoForEntityId(entityId: EntityId)(implicit ec: ExecutionContext): Future[Option[Nino]] =
    resolveTaxIdentifierForEntityId(entityId) map {
      case Some(taxIds) => taxIds.collectFirst { case t: Nino => t }
      case _            => None
    }
}

object ConflictResolutionUtils {
  def findUtr(ids: Set[TaxIdWithName]): Option[SaUtr] = ids.collectFirst { case saUtr: SaUtr => saUtr }

  def theEntitySharesTheUtrWithAuth(entity: Entity, authTaxIds: Set[TaxIdWithName]): Boolean =
    findUtr(authTaxIds).fold(false)(authUtr => entity.taxIdentifiers.contains(authUtr))

  def reportUnimplementedScenario(entities: List[Entity], authTaxIds: Set[TaxIdWithName], message: String): Unit =
    throw new RuntimeException(
      s"Unimplemented scenario! $message. Auth: [${authTaxIds.mkString(",")}]; Entities: [${entities.mkString(",")}]"
    )
}
