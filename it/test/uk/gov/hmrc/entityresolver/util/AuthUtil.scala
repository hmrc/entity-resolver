/*
 * Copyright 2019 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.util

import org.apache.pekko.actor.ActorSystem
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Seconds, Span }
import play.api.libs.json.{ Json, Reads }
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.{ AhcWSClient, AhcWSClientConfig, StandaloneAhcWSClient }
import uk.gov.hmrc.domain.{ CtUtr, HmrcMtdItsa, Nino, SaUtr }
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String

object AuthUtil extends ScalaFutures {

  implicit val deserialiser: Reads[GatewayToken] = Json.reads[GatewayToken]
  implicit val system: ActorSystem = ActorSystem()
  implicit val httpClient: WSClient = new AhcWSClient(StandaloneAhcWSClient(AhcWSClientConfig()))

  case class GatewayToken(gatewayToken: String)

  private def getGgNinoUserPayload(nino: Nino) =
    s"""
       | {
       |  "credId": "1234",
       |  "affinityGroup": "Organisation",
       |  "confidenceLevel": 200,
       |  "credentialStrength": "strong",
       |  "nino": "${nino.nino}",
       |  "enrolments": []
       |  }
     """.stripMargin

  private def getGgSaUserPayload(utr: String) =
    s"""
       | {
       |  "credId": "1235",
       |  "affinityGroup": "Organisation",
       |  "confidenceLevel": 200,
       |  "credentialStrength": "strong",
       |  "enrolments": [
       |      {
       |        "key": "IR-SA",
       |        "identifiers": [
       |          {
       |            "key": "UTR",
       |            "value": "$utr"
       |          }
       |        ],
       |        "state": "Activated"
       |      }
       |    ]
       |  }
     """.stripMargin

  private def getGgItsaUserPayload(nino: Nino, itsa: String) =
    s"""
       | {
       |  "credId": "1235",
       |  "affinityGroup": "Organisation",
       |  "confidenceLevel": 200,
       |  "credentialStrength": "strong",
       |  "nino": "${nino.nino}",
       |  "enrolments": [
       |      {
       |        "key": "HMRC-MTD-IT",
       |        "identifiers": [
       |          {
       |            "key": "MTDBSA",
       |            "value": "$itsa"
       |          }
       |        ],
       |        "state": "Activated"
       |      }
       |    ]
       |  }
     """.stripMargin

  private def getGgNinoSaUserPayload(nino: Nino, saUtr: SaUtr) =
    s"""
       | {
       |  "credId": "1235",
       |  "affinityGroup": "Organisation",
       |  "confidenceLevel": 200,
       |  "credentialStrength": "strong",
       |  "nino": "${nino.nino}",
       |  "enrolments": [
       |      {
       |        "key": "IR-SA",
       |        "identifiers": [
       |          {
       |            "key": "UTR",
       |            "value": "${saUtr.utr}"
       |          }
       |        ],
       |        "state": "Activated"
       |      }
       |    ]
       |  }
     """.stripMargin

  private def buildUserToken(authLoginUrl: String, payload: String): (String, String) = {
    val response = httpClient
      .url(authLoginUrl)
      .withHttpHeaders(("Content-Type", "application/json"))
      .post(payload)
      .futureValue(timeout(Span(20, Seconds)))

    ("Authorization", response.header("Authorization").get)
  }

  def deleteCachedNinos(deleteCachedNinoUrl: String): Unit =
    httpClient
      .url(deleteCachedNinoUrl)
      .withHttpHeaders("Content-Type" -> "application/json")
      .delete()
      .futureValue(timeout(Span(20, Seconds)))

  def buildNinoUserToken(nino: Nino, authLoginUrl: String): (String, String) =
    buildUserToken(authLoginUrl, getGgNinoUserPayload(nino))

  def buildItsaUserToken(nino: Nino, itsa: HmrcMtdItsa, authLoginUrl: String): (String, String) =
    buildUserToken(authLoginUrl, getGgItsaUserPayload(nino, itsa.value))

  def buildSaUserToken(saUtr: SaUtr, authLoginUrl: String): (String, String) =
    buildUserToken(authLoginUrl, getGgSaUserPayload(saUtr.utr))

  def buildCtUserToken(ctUtr: CtUtr, authLoginUrl: String): (String, String) =
    buildUserToken(authLoginUrl, getGgSaUserPayload(ctUtr.utr))

  def buildNinoSaUserToken(nino: Nino, saUtr: SaUtr, authLoginUrl: String): (String, String) =
    buildUserToken(authLoginUrl, getGgNinoSaUserPayload(nino, saUtr))

}
