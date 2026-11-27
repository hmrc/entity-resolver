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

package uk.gov.hmrc.entityresolver.services

import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results.{ BadRequest, Conflict, InternalServerError, NotFound, Ok, Unauthorized }
import uk.gov.hmrc.domain.HmrcMtdItsa
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.entityresolver.connectors.TaxIdentifierRetriever
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.audit.model.EventTypes.Succeeded
import uk.gov.hmrc.entityresolver.util.TaxIdExtensions.StringExtensions

import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

@Singleton
class EntityService @Inject() (
  entityIdResolver: EntityIdResolver,
  entityRepository: EntityRepository,
  auditConnector: AuditConnector,
  pathMappingOperations: PathMappingOperations,
  taxIdentifierRetriever: TaxIdentifierRetriever,
  @Named("appName") appName: String
)(implicit ec: ExecutionContext) {

  private val logger = Logger(getClass.getName)

  def findByEntityId(entityId: String): Future[Result] =
    entityRepository
      .findBy(EntityId(entityId))
      .map {
        case Some(e) => Ok(Json.toJson(e))
        case _       => NotFound
      }

  def findByProvidedTaxId(taxRegime: String, taxId: String): Future[Result] =
    Try {
      taxId.toTaxId(taxRegime, taxId)
    } match {
      case Failure(exception) => Future.successful(BadRequest(exception.getMessage))
      case Success(value)     => resultFrom(entityRepository.findBy(value))
    }

  // This method extracts the taxids from the users auth token.
  // Additionally, there are two modes of operation.
  // - resolve
  //   true  - resolve conflicting entities, this case arises if there are multiple documents for the same user.
  //   false - no resolution happens, this is for ITSA functionality
  // - resolveIds ; Used to tell the resolver to create a new entity if none found.
  def findByTokenTaxId(resolve: Boolean, resolveIds: Boolean)(implicit
    hc: HeaderCarrier
  ): Future[Result] =
    taxIdentifierRetriever.currentTaxIdentifiers flatMap {
      case taxIds if taxIds.isEmpty => Future.successful(Unauthorized("TAX_IDENTIFIERS_NOT_FOUND"))
      case taxIds if resolve        => findWithResolution(taxIds, resolveIds)
      case taxIds                   => findWithoutResolution(taxIds) // calls /preferences/capture functionality
    }

  // Use the resolution process to resolve any conflicts or duplicate entities
  private def findWithResolution(taxIds: Set[TaxIdWithName], resolveIds: Boolean)(implicit
    hc: HeaderCarrier
  ): Future[Result] =
    entityIdResolver.resolveAny(taxIds, resolveIds).flatMap {
      case Some(entity) =>
        auditEntityIdAccessed(taxIds, entity.id) // deliberate async future, per current functionality
        Future.successful(Ok(Json.toJson(entity)))
      case None =>
        auditEntityIdAsNone(taxIds) // deliberate async future, per current functionality
        Future.successful(NotFound)
    }

  // Do NOT use the resolution process
  private def findWithoutResolution(taxIds: Set[TaxIdWithName]): Future[Result] =
    taxIds match {
      case taxIds if taxIds.isEmpty => Future.successful(Unauthorized("TAX_IDENTIFIERS_NOT_FOUND"))
      case taxIds =>
        entityRepository.findByAny(taxIds) flatMap { entities =>
          entities.size match {
            case 0 => Future.successful(NotFound(Json.obj("reason" -> "PREFERENCES_NOT_FOUND")))
            case 1 =>
              val entity = entities.head
              logger.warn(s"Fetch the preference record for ${entity.id}")
              pathMappingOperations.auditEntityIdAccessed(taxIds, entity.id)
              Future.successful(Ok(Json.toJson(entity)))
            case _ =>
              logger.warn(s"Multiple preference records are found for $taxIds")
              Future.successful(Conflict(Json.obj("reason" -> "MULTIPLE_PREFERENCES_FOUND")))
          }
        }
    }

  def auditEntityIdAccessed(currentTaxIds: Set[TaxIdWithName], entityId: EntityId): Future[AuditResult] =
    auditConnector.sendEvent(
      DataEvent(
        appName,
        auditType = Succeeded,
        tags = Map("transactionName" -> "EntityId Accessed"),
        detail = currentTaxIds.map(taxId => taxId.name -> taxId.value).toMap ++ Map("entityId" -> entityId.value)
      )
    )

  def auditEntityIdAsNone(currentTaxIds: Set[TaxIdWithName]): Future[AuditResult] =
    auditConnector.sendEvent(
      DataEvent(
        appName,
        auditType = Succeeded,
        tags = Map("transactionName" -> "No EntityId Accessed"),
        detail = currentTaxIds.map(taxId => taxId.name -> taxId.value).toMap
      )
    )

  private def resultFrom(input: => Future[Option[Entity]]): Future[Result] =
    Try {
      input.map {
        case Some(e) => Ok(Json.toJson(e))
        case _       => NotFound
      }
    } match {
      case Success(value)     => value
      case Failure(exception) => Future.successful(InternalServerError(exception.getMessage))
    }
}
