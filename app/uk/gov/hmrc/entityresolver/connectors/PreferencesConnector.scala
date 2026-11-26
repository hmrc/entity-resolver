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

package uk.gov.hmrc.entityresolver.connectors

import play.api.Logger
import play.api.http.Status
import play.api.http.Status.*
import play.api.libs.json.*
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import play.api.mvc.Result
import play.api.mvc.Results.*
import uk.gov.hmrc.entityresolver.models.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads, HttpResponse }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.{ URI, URL }
import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

case class UnexpectedOptOutResponseException(entityId: EntityId, status: Int)
    extends RuntimeException(s"Entity $entityId returned a status code of $status")

object PreferencesConnector {
  val configKey = "preferences"
}

@Singleton
class PreferencesConnector @Inject() (
  http: HttpClientV2,
  servicesConfig: ServicesConfig,
  @Named("httpReadsResult") httpResultReads: HttpReads[Result]
)(implicit
  ec: ExecutionContext
) {

  private val logger = Logger(getClass)

  private lazy val serviceUrl: String = servicesConfig.baseUrl(PreferencesConnector.configKey)

  def updated(entityId: EntityId)(implicit hc: HeaderCarrier): Future[Boolean] =
    http
      .put(url(s"/preferences/$entityId/updated"))
      .withBody(Json.obj())
      .execute[HttpResponse]
      .map { httpResponse =>
        httpResponse.status match {
          case Status.NO_CONTENT =>
            true

          case Status.NOT_FOUND =>
            logger.info(s"Unable to find and update preference with entityId: $entityId")
            false

          case _ =>
            logger.warn(
              s"Unable to update preference with entityId: $entityId, status: ${httpResponse.status}, reason:" + httpResponse.body
            )
            false
        }
      }
      .recover { case e: Throwable =>
        logger.warn(s"Unable to update preference with entityId: $entityId, reason:" + e.getMessage, e)
        false
      }

  private def url(path: String): URL = new URI(s"$serviceUrl$path").toURL

  def getPreferencesStatus(entityId: EntityId)(implicit headerCarrier: HeaderCarrier): Future[Result] =
    http
      .get(url(s"/preferences/${entityId.value}"))
      .execute[HttpResponse]
      .map { result =>
        result.status match {
          case OK => Ok(result.body)
          case _ =>
            logger.error(
              s"unexpected get preferences status response for $entityId - ${result.status} ${result.body}"
            )
            NotFound
        }
      }

  def getPreferences(entityId: EntityId)(implicit headerCarrier: HeaderCarrier): Future[Option[HttpResponse]] =
    http
      .get(url(s"/preferences/${entityId.value}"))
      .execute[HttpResponse]
      .map { result =>
        result.status match {
          case OK => Some(result)
          case _ =>
            logger.error(
              s"unexpected get preferences response for $entityId - ${result.status} ${result.body}"
            )
            None
        }
      }

  def getPreferencesForOptOut(
    entityId: EntityId
  )(implicit headerCarrier: HeaderCarrier): Future[Option[PreferenceStatus]] =
    http
      .get(url(s"/preferences/${entityId.value}"))
      .execute[HttpResponse]
      .map { result =>
        result.status match {
          case Status.OK =>
            val generic = (result.json \ "termsAndConditions" \ "generic" \ "accepted")
              .asOpt[Boolean]

            val taxCredits = (result.json \ "termsAndConditions" \ "taxCredits" \ "accepted")
              .asOpt[Boolean]

            Some(PreferenceStatus(generic, taxCredits))

          case _ =>
            logger.error(
              s"unexpected get preferences opt out response for $entityId - ${result.status} ${result.body}"
            )

            None
        }
      }

  def optOutTerms(entityId: EntityId, terms: String)(implicit
    headerCarrier: HeaderCarrier
  ): Future[Boolean] = {
    val body = Json.parse(s"""{"$terms":{"accepted":false, "manualOptOut":true}}""")
    val updateUrl = s"/preferences-admin/optout/${entityId.value}"
    http
      .post(url(updateUrl))
      .withBody(body)
      .execute[Result](httpResultReads, ec)
      .map { result =>
        result.header.status match {
          case Status.OK       => true
          case Status.CONFLICT => false
          case _ =>
            logger.error(
              s"unexpected opt out terms response for $entityId/$terms- ${result.header.status} ${result.body}"
            )
            throw UnexpectedOptOutResponseException(entityId, result.header.status)
        }
      }
  }

  def optIn(entityId: EntityId, terms: TermsAndConditionsUpdate)(implicit hc: HeaderCarrier): Future[Result] =
    makeTermsUpdate(url(s"/preferences/${entityId.value}/optin"), terms)

  def optOut(entityId: EntityId, terms: TermsAndConditionsUpdate)(implicit hc: HeaderCarrier): Future[Result] =
    makeTermsUpdate(url(s"/preferences/${entityId.value}/optout"), terms)

  def changeEmailLanguage(entityId: EntityId, terms: TermsAndConditionsUpdate)(implicit
    hc: HeaderCarrier
  ): Future[Result] =
    makeTermsUpdate(url(s"/preferences/${entityId.value}/email-language"), terms)

  private def makeTermsUpdate(url: URL, terms: TermsAndConditionsUpdate)(implicit hc: HeaderCarrier) =
    http
      .post(url)
      .withBody(Json.toJson(terms))
      .execute[HttpResponse]
      .map { result =>
        result.status match {
          case OK      => Ok(result.body)
          case CREATED => Created(result.body)
          case _ =>
            logger.error(
              s"unexpected terms update response for  $url - ${result.status} ${result.body}"
            )
            BadRequest
        }
      }

}

case class EntityResolverRequest(entityId: EntityId)

object EntityResolverRequest {
  implicit val format: OFormat[EntityResolverRequest] =
    Json.format[EntityResolverRequest]
}
