/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.entityresolver.util

import org.scalatest.Assertions.fail
import play.api.http.HeaderNames
import play.api.libs.json.Reads._
import play.api.libs.json.{ Json, _ }
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Environment, Logger }
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain._
import play.api.libs.ws.WSBodyWritables.writeableOf_String
import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }

@Singleton
case class AuthorityBuilder(json: JsObject, authProvider: TestAuthorisationProvider) {

  private def jsonTransformer(jsonEnrolment: JsValue) =
    (__ \ "enrolments").json.update(of[JsArray].map { case JsArray(arr) => JsArray(arr :+ jsonEnrolment) })

  def taxIdToEnrolment(t: TaxIdentifier): AuthorityBuilder = t match {
    case SaUtr(utr)        => genericEnrolemnt("IR-SA", "UTR", utr)
    case CtUtr(utr)        => genericEnrolemnt("IR-CT", "UTR", utr)
    case Vrn(vrn)          => genericEnrolemnt("HMCE-VATDEC-ORG", "VATRegNo", vrn)
    case HmrcObtdsOrg(ern) => genericEnrolemnt("HMRC-OBTDS-ORG", "EtmpRegistrationNumber", ern)
    case HmrcMtdVat(vrn)   => genericEnrolemnt("HMRC-MTD-VAT", "VRN", vrn)
    case _                 => fail("Unknown taxId")
  }

  def genericEnrolemnt(key1: String, key2: String, value: String): AuthorityBuilder = {
    val entitlement =
      s"""
         |    {
         |      "key": "$key1",
         |      "identifiers": [
         |        {
         |          "key": "$key2",
         |          "value": "$value"
         |        }
         |      ],
         |      "state": "Activated"
         |    }
    """.stripMargin
    val jsonEnrolment = Json.parse(entitlement)
    val newJson = json.transform(jsonTransformer(jsonEnrolment)).asOpt.get
    AuthorityBuilder(newJson, authProvider)
  }

  def withSaUtr(saUtr: SaUtr): AuthorityBuilder = taxIdToEnrolment(saUtr)

  def withCtUtr(ctUtr: CtUtr): AuthorityBuilder = taxIdToEnrolment(ctUtr)

  def withVrn(vrn: Vrn): AuthorityBuilder = taxIdToEnrolment(vrn)

  def withFhdds(identifier: HmrcObtdsOrg): AuthorityBuilder = taxIdToEnrolment(identifier)

  def withMtdVat(identifier: HmrcMtdVat): AuthorityBuilder = taxIdToEnrolment(identifier)

  def withNino(nino: Nino): AuthorityBuilder = {
    val newJson = json ++ Json.obj("nino" -> nino.value, "affinityGroup" -> "Individual")
    AuthorityBuilder(newJson, authProvider)
  }

  def bearerTokenHeader(): Future[Option[(String, String)]] =
    authProvider.createBearerToken(json).map(x => x.map(h => (HeaderNames.AUTHORIZATION, h)))

  private def await[A](future: Future[A])(implicit timeout: Duration) = Await.result(future, timeout)

  def bearerTokenHeader()(implicit timeout: Duration): (String, String) =
    await(bearerTokenHeader()) match {
      case Some(h) => h
      case None =>
        Logger("bearerTokenHeader").error(
          "No BearerToken Header returned from Auth-Login-Api. Have you correctly configured your test data to sent to the auth?"
        )
        ("", "")
    }
}

@Singleton
class TestAuthorisationProvider @Inject() (
  val authConnector: AuthConnector,
  httpClient: WSClient,
  val runModeConfiguration: Configuration,
  val environment: Environment
) {

  lazy val authPort = 8500
  lazy val ggAuthPort = 8585

  def governmentGatewayAuthority(): AuthorityBuilder = AuthorityBuilder(authRequestJson, this)
  val authRequest: String =
    s"""
       |{
       |  "credId": "a-cred-id",
       |  "confidenceLevel": 300,
       |  "credentialStrength": "strong",
       |  "enrolments": [
       |  ],
       |  "usersName": "Lisa Nicole Brennan",
       |  "email": "lisa.brennan@some.domain.com",
       |  "description": "SJ Junior",
       |  "agentFriendlyName": "Johhny English",
       |  "agentCode": "Oh Oh Seven",
       |  "agentId": "007",
       |  "affinityGroup": "Individual",
       |  "itmpData": {
       |    "givenName": "Lisa",
       |    "middleName": "Nicole",
       |    "familyName": "Brennan",
       |    "birthdate": "1988-01-04",
       |    "address": {
       |      "line1": "10 The Causeway",
       |      "line2": "Little London",
       |      "line3": "Blackheath",
       |      "line4": "Surrey",
       |      "postCode": "GU99 ZBH",
       |      "countryName": "United Kingdom",
       |      "countryCode": "GB"
       |    }
       |  }
       |}
       """.stripMargin

  val authRequestJson: JsObject = Json.parse(authRequest).validate[JsObject].asOpt.get

  def createBearerToken(json: JsObject): Future[Option[String]] = buildUserToken(Json.stringify(json))

  private def buildUserToken(payload: String): Future[Option[String]] =
    httpClient
      .url(s"http://localhost:$ggAuthPort/government-gateway/session/login")
      .withHttpHeaders(("Content-Type", "application/json"))
      .post(payload)
      .map(response => response.header(HeaderNames.AUTHORIZATION))

}
