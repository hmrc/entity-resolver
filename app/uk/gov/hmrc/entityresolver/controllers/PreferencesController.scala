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

import cats.data.EitherT
import cats.instances.future.*
import cats.syntax.either.*
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr }
import uk.gov.hmrc.entityresolver.connectors.{ EnrolmentStoreProxyConnector, PreferencesConnector, TaxIdentifierRetriever }
import uk.gov.hmrc.entityresolver.models.EnrolmentValidationError.*
import uk.gov.hmrc.entityresolver.models.*
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.entityresolver.services.PathMappingOperations
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.*
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class PreferencesController @Inject() (
  pathMappingOperations: PathMappingOperations,
  preferencesConnector: PreferencesConnector,
  taxIdentifierRetriever: TaxIdentifierRetriever,
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  entityRepository: EntityRepository,
  val authConnector: AuthConnector,
  cc: ControllerComponents,
  auditConnector: AuditConnector
)(implicit ec: ExecutionContext)
    extends BackendController(cc) with AuthorisedFunctions {
  private val logger = Logger(getClass.getName)

  def capture(): Action[AnyContent] = Action.async { implicit request =>
    taxIdentifierRetriever.currentTaxIdentifiers flatMap {
      case taxIds if taxIds.isEmpty => Future.successful(Unauthorized("TAX_IDENTIFIERS_NOT_FOUND"))
      case taxIds =>
        entityRepository.findByAny(taxIds) flatMap { entities =>
          entities.size match {
            case 0 => Future.successful(NotFound(Json.obj("reason" -> "PREFERENCES_NOT_FOUND")))
            case 1 =>
              val entityId = entities.head.id
              logger.warn(s"Fetch the preference record for $entityId")
              pathMappingOperations.auditEntityIdAccessed(taxIds, entityId)
              preferencesConnector.getPreferencesStatus(entityId)
            case _ =>
              logger.warn(s"Multiple preference records are found for $taxIds")
              Future.successful(Conflict(Json.obj("reason" -> "MULTIPLE_PREFERENCES_FOUND")))
          }
        }
    }
  }

  def checkAndDelete(entityId: EntityId, identifier: String): Action[AnyContent] = Action.async {
    entityRepository
      .findBy(entityId)
      .flatMap { entityOption =>
        entityOption.fold(reply(NOT_FOUND, "INVALID_ENTITY")) { entity =>
          (entity.getSaUtr, entity.getItsa) match {
            case (Some(_), Some(_)) if identifier == "sautr" || identifier == "HMRC-MTD-IT" =>
              entityRepository
                .updateTaxIds(entityId, entity.taxIdentifiers.filterNot(_.name == identifier), overrideIds = true)
                .flatMap { _ =>
                  reply(OK, "UNSET_MARK_DE_ENROLMENT")
                }
            case _ =>
              entityRepository.remove(entityId).flatMap { _ =>
                reply(OK, "DELETE_PREFERENCES")
              }
          }
        }
      }
      .recoverWith { case _ =>
        reply(BAD_REQUEST, "INTERNAl_ERROR")
      }
  }

  // See the case definition on https://jira.tools.tax.service.gov.uk/browse/DC-3504
  def confirm(entityId: EntityId, itsa: HmrcMtdItsa): Action[AnyContent] = Action.async { implicit request =>
    authorised((AffinityGroup.Organisation or AffinityGroup.Individual) and ConfidenceLevel.L200)
      .retrieve(Retrievals.saUtr and Retrievals.nino) { case authTokenSaUtr ~ authTokenNino =>
        entityRepository
          .findBy(entityId)
          .flatMap { maybeEntityById =>
            maybeEntityById.fold(reply(NOT_FOUND, "Invalid entity id or entity id has expired")) { entityById =>
              (entityById.getItsa, entityById.getSaUtr, entityById.getNino, authTokenSaUtr, authTokenNino) match {
                // case 1.5
                case (Some(entityItsa), _, _, _, _) if entityItsa != itsa.value =>
                  reply(UNAUTHORIZED, "entityId already has a different itsaId linked to it in entity resolver")

                // case 1.8
                case (_, Some(entitySaUtr), Some(entityNino), None, Some(authNino)) =>
                  auditConnector.sendExplicitAudit(
                    auditType = "ITSA-SIGN-UP-CONFIRM",
                    detail = Map(
                      "itsa"            -> itsa.value,
                      "authTokenSaUtr"  -> "None",
                      "sautrFromEntity" -> entitySaUtr,
                      "authTokenNino"   -> authNino,
                      "ninoFromEntity"  -> entityNino,
                      "message" -> "SAUTR does not exist in the Auth token but exists in Entity Resolver. Nino will be matched"
                    )
                  )
                  if (entityNino != authNino) {
                    reply(
                      UNAUTHORIZED,
                      "SAUTR does not exist in the Auth token but exists in Entity Resolver & auth nino doesn't match with entity nino"
                    )
                  } else {
                    finalCheck(authTokenSaUtr, entityById, entityId, itsa)
                  }

                case (_, None, Some(entityNino), Some(authSaUtr), Some(authNino)) =>
                  auditConnector.sendExplicitAudit(
                    auditType = "ITSA-SIGN-UP-CONFIRM",
                    detail = Map(
                      "itsa"            -> itsa.value,
                      "authTokenSaUtr"  -> authSaUtr,
                      "sautrFromEntity" -> "None",
                      "authTokenNino"   -> authNino,
                      "ninoFromEntity"  -> entityNino,
                      "message" -> "SAUTR does not exist in the Entity resolver but exists in Auth token. Nino will be matched"
                    )
                  )
                  if (entityNino != authNino) {
                    reply(
                      UNAUTHORIZED,
                      "SAUTR does not exist in the Entity resolver but exists in Auth token & auth nino doesn't match with entity nino"
                    )
                  } else {
                    finalCheck(authTokenSaUtr, entityById, entityId, itsa)
                  }

                // case 1.9
                case (_, _, Some(_), _, None) =>
                  reply(UNAUTHORIZED, "NINO does not exist in the Auth token but exists in Entity Resolver")

                // case 1.2
                case (_, Some(entitySaUtr), _, Some(authSaUtr), _) if entitySaUtr != authSaUtr =>
                  reply(UNAUTHORIZED, "SAUTR in Auth token is linked to a different entityId in entity resolver")

                // case 1.7
                case (_, _, Some(entityNino), _, Some(authNino)) if entityNino != authNino =>
                  reply(UNAUTHORIZED, "NINO in Auth token is different from  NINO in entity resolver")

                case (_, _, Some(authSaUtr), _, _) =>
                  entityRepository.findBy(SaUtr(authSaUtr)).flatMap { entityBySaUtr =>
                    // case 1.3
                    if (entityBySaUtr.exists(_.id != entityId)) {
                      reply(UNAUTHORIZED, "SAUTR in Auth token is linked to a different entityId in entity resolver")
                    } else {
                      finalCheck(authTokenSaUtr, entityById, entityId, itsa)
                    }
                  }

                case _ =>
                  finalCheck(authTokenSaUtr, entityById, entityId, itsa)
              }
            }
          }
          .map(logAndReturn)
      }
      .recoverWith { case e: AuthorisationException =>
        reply(UNAUTHORIZED, e.getMessage)
      }
  }

  private def finalCheck(
    maybeAuthSaUtr: Option[String],
    entityById: Entity,
    entityId: EntityId,
    itsa: HmrcMtdItsa
  )(implicit hc: HeaderCarrier): Future[Result] =
    entityRepository.findBy(itsa).flatMap { maybeEntityByItsa =>
      (maybeEntityByItsa.map(_.id), maybeAuthSaUtr, entityById.getSaUtr) match {

        case (Some(entityIdLinkedToItsaEntity), _, _) if entityIdLinkedToItsaEntity != entityId =>
          // case 1.6
          reply(UNAUTHORIZED, "itsaId is already linked to a different entityId in entity resolver")

        // case 1.4
        case (_, None, None) =>
          checkPrefAndLinkItsaId(entityById, itsa)

        // case 1.1
        case (_, Some(authSaUtr), Some(entitySaUtr)) if authSaUtr == entitySaUtr =>
          checkPrefAndLinkItsaId(entityById, itsa)

        // case 1.8
        case (_, None, Some(entitySaUtr)) =>
          checkPrefAndLinkItsaId(entityById, itsa)

        // case 1.8
        case (_, Some(authSaUtr), None) =>
          checkPrefAndLinkItsaId(entityById, itsa)

        case _ =>
          // shouldn't happen
          reply(INTERNAL_SERVER_ERROR, "Unexpected setting")
      }
    }

  private def checkPrefAndLinkItsaId(entityById: Entity, itsa: HmrcMtdItsa)(implicit hc: HeaderCarrier) =
    checkLinkedPreference(entityById, itsa.value).foldF(
      err => reply(err.statusCode, err.message),
      success => Future.successful(success)
    )

  private def linkEntityToItsaId(entity: Entity, itsa: HmrcMtdItsa): Future[Boolean] =
    entityRepository
      .updateTaxIds(entity.id, entity.taxIdentifiers + itsa)

  private def reply(code: Int, reason: String): Future[Result] =
    Future.successful(withResponse(code, reason))

  private def logAndReturn(result: Result): Result = {
    val res: String = result.body match {
      case HttpEntity.Strict(data, _) => data.utf8String
      case _                          => "Empty"
    }
    logger.info(s"ITSA Signup confirm returned with $res")
    result
  }

  private def withResponse(code: Int, reason: String, preference: Option[JsValue] = None): Result = {
    val preferenceJson =
      preference match {
        case Some(p) => Json.obj("preference" -> p)
        case _       => Json.obj()
      }
    Status(code).apply(Json.obj("reason" -> reason) ++ preferenceJson)
  }

  // See case definition there https://jira.tools.tax.service.gov.uk/browse/DC-3521
  def enrolment(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    authorised(AffinityGroup.Agent) {
      withJsonBody[AgentEnrolment] { enrolments =>
        (for {
          _                  <- EitherT.fromEither(validateAgentIdArn(enrolments.arn))
          saUtrEnrolmentKey  <- EitherT.fromEither(validateUtrEnrolmentKeys(enrolments.sautr))
          itsaIdEnrolmentKey <- EitherT.fromEither(validateItsaEnrolmentKeys(enrolments.itsaId))
          eByNino <- checkEntityAssociatedToTaxIdentifier(
                       Nino(enrolments.nino),
                       SaUtr(saUtrEnrolmentKey.value),
                       HmrcMtdItsa(itsaIdEnrolmentKey.value)
                     )
          _      <- checkEnrolmentsEACD(saUtrEnrolmentKey, itsaIdEnrolmentKey)
          result <- checkLinkedPreference(eByNino, itsaIdEnrolmentKey.value)
        } yield result).value flatMap {
          case Right(v) =>
            sendAuditMessage(enrolments)
            Future.successful(v)
          case Left(err) =>
            sendAuditMessage(
              enrolments,
              "AgentEnrolmentError",
              Map("errorcode" -> err.statusCode.toString, "errordescription" -> err.message)
            )
            reply(code = err.statusCode, reason = err.message)
        }
      }
    }.recoverWith { case _: AuthorisationException =>
      reply(UNAUTHORIZED, "Agent not Authorised")
    }
  }

  private def sendAuditMessage(
    enrolments: AgentEnrolment,
    auditType: String = "AgentEnrolmentConfirmed",
    error: Map[String, String] = Map.empty
  )(implicit hc: HeaderCarrier): Unit = {
    val detail: Map[String, String] = hc.toAuditDetails(
      "regime" -> "ITSA",
      "utr"    -> enrolments.sautr,
      "nino"   -> enrolments.nino,
      "itsa"   -> enrolments.itsaId,
      "agent"  -> enrolments.arn
    ) ++ error
    auditConnector.sendExplicitAudit(auditType, detail)
  }

  private def validateAgentIdArn(id: String): Either[ValidationError, Boolean] =
    Either.cond(Arn.isValid(id), true, InvalidArnError("Agent not Authorised", statusCode = UNAUTHORIZED))

  private def validateUtrEnrolmentKeys(utr: String): Either[ValidationError, EnrolmentKey] =
    EnrolmentKey
      .parse(utr)
      .leftMap(_ => InvalidEnrolmentKeyError("Failed to parse utr enrolment key", statusCode = BAD_REQUEST))

  private def validateItsaEnrolmentKeys(itsa: String): Either[ValidationError, EnrolmentKey] =
    EnrolmentKey
      .parse(itsa)
      .leftMap(_ => InvalidEnrolmentKeyError("Failed to parse itsa enrolment key", statusCode = BAD_REQUEST))

  private def checkEntityAssociatedToTaxIdentifier(
    nino: TaxIdWithName,
    utr: TaxIdWithName,
    itsa: TaxIdWithName
  ): EitherT[Future, ValidationError, Entity] =
    EitherT(for {
      entityNino <- entityRepository.findBy(nino)
      entityUtr  <- entityRepository.findBy(utr)
    } yield (entityNino, entityUtr) match {
      case (Some(eByNino), Some(eByUtr)) if eByNino.id == eByUtr.id =>
        if (eByNino.taxIdentifiers.contains(itsa)) {
          Left(EntityCheckError("MTD-IT already exists", statusCode = UNAUTHORIZED))
        } else {
          Right(eByNino)
        }
      case _ =>
        // case 3.5
        Left(
          EntityCheckError(
            "The NINO and SAUTR are not linked to the same EntityId in entity resolver",
            statusCode = UNAUTHORIZED
          )
        )
    })

  private def checkEnrolmentsEACD(
    utrEnrolmentKey: EnrolmentKey,
    itsaEnrolmentKey: EnrolmentKey
  )(implicit hc: HeaderCarrier): EitherT[Future, ValidationError, Boolean] =
    for {
      userIdByUtr  <- getAndValidateUserId(utrEnrolmentKey)
      userIdByItsa <- getAndValidateUserId(itsaEnrolmentKey)
      cond <- EitherT.fromEither(
                Either.cond(
                  userIdByUtr == userIdByItsa,
                  true,
                  new ValidationError("Agent not Authorised", statusCode = UNAUTHORIZED)
                )
              )
    } yield cond

  private def getAndValidateUserId(
    enrolmentKey: EnrolmentKey
  )(implicit hc: HeaderCarrier): EitherT[Future, ValidationError, String] =
    EitherT(enrolmentStoreProxyConnector.getCredentialsId(enrolmentKey).map {
      case Right(Some(userId)) if userId.principalUserIds.lengthCompare(1) == 0 =>
        Right(userId.principalUserIds.head)
      case _ => // case 3.3 and case 3.4
        Left(
          CredentialsCheckError(s"${enrolmentKey.serviceNameForError} Identifier not found", statusCode = UNAUTHORIZED)
        )
    })

  private def checkLinkedPreference(entity: Entity, itsaId: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, ValidationError, Result] = {
    val result = for {
      maybePreference  <- preferencesConnector.getPreferences(entity.id)
      linkEntityResult <- linkEntityToItsaId(entity, HmrcMtdItsa(itsaId))
    } yield maybePreference match {
      case Some(response) =>
        Either.cond(
          linkEntityResult,
          withResponse(OK, "ITSA ID successfully added", Some(response.json)),
          LinkedPreferenceCheckError("Failed to link ITSAID, may already exist", statusCode = UNAUTHORIZED)
        )
      case response => Left(LinkedPreferenceCheckError("No preferences found", statusCode = UNAUTHORIZED))
    }
    EitherT(result)
  }

}
