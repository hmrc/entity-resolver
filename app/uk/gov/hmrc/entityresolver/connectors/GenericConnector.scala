/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.connectors

import play.api.libs.json.{ JsValue, Writes }
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads, HttpResponse }
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.*
import java.net.{ URI, URL }
import scala.concurrent.{ ExecutionContext, Future }

class GenericConnector(http: HttpClientV2, serviceBaseUrl: String)(implicit ec: ExecutionContext) {
  private[this] def url(serviceUrl: String, path: String, queryString: String): URL =
    queryString match {
      case "" => new URI(s"$serviceUrl$path").toURL
      case _  => new URI(s"$serviceUrl$path?$queryString").toURL
    }

  def doGet[T](queryString: String)(
    destinationUrl: String
  )(implicit hc: HeaderCarrier, reads: HttpReads[T]): Future[T] =
    http.get(url(serviceBaseUrl, destinationUrl, queryString)).execute[T]

  def doPost[T](body: JsValue, queryString: String)(
    destinationUrl: String
  )(implicit hc: HeaderCarrier, reads: HttpReads[T], writes: Writes[JsValue]): Future[T] =
    http.post(url(serviceBaseUrl, destinationUrl, queryString)).withBody(body).execute[T]

  def doPut[T](body: JsValue, queryString: String)(
    destinationUrl: String
  )(implicit hc: HeaderCarrier, reads: HttpReads[T], writes: Writes[JsValue]): Future[T] =
    http.put(url(serviceBaseUrl, destinationUrl, queryString)).withBody(body).execute[T]

  def doDelete(destinationUrl: String)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.delete(url(serviceBaseUrl, destinationUrl, "")).execute[HttpResponse]
}
