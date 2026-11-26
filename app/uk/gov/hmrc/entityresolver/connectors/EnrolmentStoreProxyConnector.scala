/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.connectors

import play.api.http.Status.{ NO_CONTENT, OK }
import play.api.libs.json.{ JsSuccess, Json }
import uk.gov.hmrc.entityresolver.models.{ EnrolmentKey, PrincipalUserIdResponse }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse }
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import uk.gov.hmrc.http.HttpReads.Implicits.*
import java.net.URI

@Singleton
class EnrolmentStoreProxyConnector @Inject() (
  httpClient: HttpClientV2,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext) {
  def serviceBaseUrl: String = s"${servicesConfig.baseUrl("enrolment-store-proxy")}/enrolment-store-proxy"

  def getCredentialsId(
    enrolmentKey: EnrolmentKey
  )(implicit hc: HeaderCarrier): Future[Either[String, Option[PrincipalUserIdResponse]]] =
    httpClient
      .get(
        new URI(s"$serviceBaseUrl/enrolment-store/enrolments/${enrolmentKey.asString}/users?type=principal").toURL
      )
      .execute[HttpResponse]
      .map { resp =>
        resp.status match {
          case NO_CONTENT       => Right(None)
          case OK               => parseResponse(resp.body)
          case unexpectedStatus => Left(s"Got unexpected status $unexpectedStatus")
        }
      }

  private def parseResponse(body: String): Either[String, Option[PrincipalUserIdResponse]] =
    Try(Json.parse(body)) match {
      case Success(b) =>
        b.validate[PrincipalUserIdResponse] match {
          case JsSuccess(ev, _) => Right(Some(ev))
          case _                => Left("invalid of json body")
        }
      case Failure(_) => Left("Not a json error")
    }

}
