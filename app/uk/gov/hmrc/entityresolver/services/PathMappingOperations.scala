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

import javax.inject.{ Inject, Named, Singleton }
import play.api.libs.json.{ JsNull, Json }
import play.api.mvc.Results.*
import play.api.mvc.{ AnyContent, Request, Result }
import uk.gov.hmrc.domain.TaxIds.*
import uk.gov.hmrc.entityresolver.config.GenericConnectorRegistry
import uk.gov.hmrc.entityresolver.connectors.TaxIdentifierRetriever
import uk.gov.hmrc.entityresolver.models.*
import uk.gov.hmrc.entityresolver.util.HeaderUtils
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads }
import uk.gov.hmrc.play.audit.http.connector.{ AuditConnector, AuditResult }
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.audit.model.EventTypes.Succeeded
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class PathMappingOperations @Inject() (
  @Named("appName") appName: String,
  entityIdResolver: EntityIdResolver,
  genericConnectorRegistry: GenericConnectorRegistry,
  auditConnector: AuditConnector,
  taxIndentifierRetriever: TaxIdentifierRetriever,
  @Named("httpReadsResult") resultHttpReads: HttpReads[Result]
)(implicit ec: ExecutionContext) {

  implicit val resultReader: HttpReads[Result] = resultHttpReads

  private val logger = Logger(getClass.getName)

  def doGet(mapping: PathMapping)(implicit request: Request[AnyContent]): Future[Result] =
    authoriseAndCall(mapping)(genericConnector(mapping).doGet[Result](request.rawQueryString))
      .map(_.withHeaders(HeaderUtils.deprecation))

  def doPost(mapping: PathMapping)(implicit request: Request[AnyContent]): Future[Result] =
    authoriseAndCall(mapping)(
      genericConnector(mapping).doPost[Result](request.body.asJson.getOrElse(JsNull), request.rawQueryString)
    ).map(_.withHeaders(HeaderUtils.deprecation))

  def doPut(mapping: PathMapping)(implicit request: Request[AnyContent]): Future[Result] =
    authoriseAndCall(mapping)(
      genericConnector(mapping).doPut[Result](request.body.asJson.getOrElse(JsNull), request.rawQueryString)
    ).map(_.withHeaders(HeaderUtils.deprecation))

  private def genericConnector(mapping: PathMapping) = genericConnectorRegistry.get(mapping.serviceName)

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

  private def authoriseAndCall(
    mapping: PathMapping
  )(connectorCall: String => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {

    def maybeCallConnector(entityId: Option[String]): Future[Result] =
      connectorCall(entityId.fold(mapping.destinationUrl)(id => mapping.destinationUrl.format(id)))

    def callConnector(entityId: String): Future[Result] = connectorCall(mapping.destinationUrl.format(entityId))

    def fetchCurrentTaxIds: Future[Set[TaxIdWithName]] = taxIndentifierRetriever.currentTaxIdentifiers

    def resolveIdToPass(currentTaxIds: Set[TaxIdWithName]): Future[Option[String]] =
      entityIdResolver.resolveAny(currentTaxIds, mapping.resolveIds).map {
        case Some(entity) =>
          if (mapping.auditIds) {
            auditEntityIdAccessed(currentTaxIds, entity.id)
          }
          Some(entity.id.value)
        case None =>
          auditEntityIdAsNone(currentTaxIds)
          None
      }

    mapping.taxIdExtraction.method match {
      case PassThrough => maybeCallConnector(mapping.taxIdExtraction.idInPath.map(_.value))
      case ReadIdsFromAuth =>
        fetchCurrentTaxIds flatMap {
          case ids if ids.isEmpty => Future.successful(Unauthorized)
          case ids =>
            resolveIdToPass(ids) flatMap {
              case Some(entityId) => callConnector(entityId)
              case None =>
                logger.warn("Unable to resolve entityId, returning PREFERENCES_NOT_FOUND")
                Future.successful(NotFound(Json.obj("reason" -> "PREFERENCES_NOT_FOUND")))
            }
        }
      case ReadIdFromPath =>
        extractValidTaxId(mapping, resolveIdToPass _).flatMap {
          case Some(entityId) => callConnector(entityId)
          case None           => Future.successful(NotFound(Json.obj("reason" -> "PREFERENCES_NOT_FOUND")))
        }
      case _ =>
        Future.successful(InternalServerError(Json.obj("reason" -> "unexpected TaxIdExtractionMethod")))
    }
  }

  private def extractValidTaxId(mapping: PathMapping, resolveIdToPass: (Set[TaxIdWithName]) => Future[Option[String]]) =
    mapping.taxIdExtraction.idInPath.fold[Future[Option[String]]](Future.successful(None)) { id =>
      resolveIdToPass(Set(id))
    }

  private implicit def toHeaderCarrier(implicit request: Request[AnyContent]): HeaderCarrier =
    HeaderCarrierConverter.fromRequest(request)
}
