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

package uk.gov.hmrc.entityresolver

import org.apache.pekko.actor.ActorSystem
import com.codahale.metrics.SharedMetricRegistries
import play.api.inject.guice.GuiceApplicationBuilder
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatest.concurrent.{ Eventually, IntegrationPatience, ScalaFutures }
import org.scalatest.time.{ Millis, Seconds, Span }
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.{ Application, Environment, Logger, Mode }
import play.api.http.Status.*
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.{ AhcWSClient, AhcWSClientConfig, StandaloneAhcWSClient }
import uk.gov.hmrc.domain.*
import uk.gov.hmrc.entityresolver.UrlHelper.-/
import uk.gov.hmrc.entityresolver.models.EntityId
import uk.gov.hmrc.entityresolver.repository.EntityRepository
import uk.gov.hmrc.entityresolver.util.AuthUtil
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import utils.Generate

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

trait ControllerISpec
    extends PlaySpec with Eventually with BeforeAndAfterAll with ScalaFutures with IntegrationPatience
    with GuiceOneServerPerSuite with BeforeAndAfterEach with MockitoSugar with CleanMongoCollectionSupport {

  implicit val system: ActorSystem = ActorSystem()
  implicit val httpClient: WSClient = new AhcWSClient(StandaloneAhcWSClient(AhcWSClientConfig()))

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(
      timeout = Span(20, Seconds),
      interval = Span(500, Millis) // Optional: Adjust the polling interval
    )

  private lazy val logger = Logger(getClass)

  override def fakeApplication(): Application = {
    logger.info(s"""Starting application with additional config:
                   |  ${configMap.mkString("\n  ")}
          """.stripMargin)
    // If applicationMode is not set, use Mode.Test (the default for GuiceApplicationBuilder)
    GuiceApplicationBuilder(environment = Environment.simple(mode = applicationMode.getOrElse(Mode.Test)))
      .configure(configMap)
      .build()
  }

  def testName: String =
    getClass.getSimpleName

  // If applicationMode is set, default to Mode.Dev, to preserve earlier behaviour
  def applicationMode: Option[Mode] =
    Some(Mode.Dev)

  protected lazy val testId =
    TestId(testName)

  protected lazy val externalServicePorts: Map[String, Int] =
    Map(
      "auth-login-api"        -> 8585,
      "identity-verification" -> 9927,
      "preferences"           -> 8025
    )

  private lazy val mongoConfig =
    Map(s"mongodb.uri" -> mongoUri)

  private lazy val configMap =
    mongoConfig ++ additionalConfig

  def resource(path: String): String =
    s"http://localhost:$port/${-/(path)}"

  def externalResource(serviceName: String, path: String): String = {
    val port =
      externalServicePorts.getOrElse(serviceName, throw new IllegalArgumentException(s"Unknown service '$serviceName'"))
    s"http://localhost:$port/${-/(path)}"
  }

  val authLoginUrl: String = externalResource("auth-login-api", "/government-gateway/session/login")

  val deleteCachedNinosUrl: String = externalResource("identity-verification", "/test-only/nino")

  // def cleanMongoCollection: CleanMongoCollection = app.injector.instanceOf[CleanMongoCollection]

  override def beforeEach(): Unit = {
    AuthUtil.deleteCachedNinos(deleteCachedNinosUrl)
    SharedMetricRegistries.clear()
  }

  def verificationTokenFor(entityId: EntityId)(implicit headers: (String, String)): String = {
    val tokenResponse = httpClient
      .url(preferencesResource(s"/test-only/preferences-admin/${entityId.value}/verification-token"))
      .withHttpHeaders(headers)
      .get()
      .futureValue
    tokenResponse.status must be(OK)
    tokenResponse.body
  }

  trait TestCaseWithUtrAndNino {
    val randomNino: Nino = Generate.nino
    val randomUtr: SaUtr = Generate.utr
    implicit def getAuthHeader(authBaseUrl: String): (String, String) =
      AuthUtil.buildNinoSaUserToken(randomNino, randomUtr, authBaseUrl)

    val entityRepository: EntityRepository = app.injector.instanceOf[EntityRepository]
  }

  trait TestCaseWithNinoOnly {
    val randomNino: Nino = Generate.nino
    implicit def getAuthHeader(authBaseUrl: String): (String, String) =
      AuthUtil.buildNinoUserToken(randomNino, authBaseUrl)

    val entityRepository: EntityRepository = app.injector.instanceOf[EntityRepository]
  }

  trait TestCaseWithUtrOnly {
    val randomUtr: SaUtr = Generate.utr
    implicit def getAuthHeader(authBaseUrl: String): (String, String) =
      AuthUtil.buildSaUserToken(randomUtr, authBaseUrl)
  }

  trait TestCaseWithCtUtrOnly {
    val randomCtUtr: CtUtr = Generate.ctUtr
    implicit def getAuthHeader(authBaseUrl: String): (String, String) =
      AuthUtil.buildCtUserToken(randomCtUtr, authBaseUrl)
  }

  trait TestCaseWithItsaAndNino {
    val randomItsa: HmrcMtdItsa = Generate.itsa
    val randomNino: Nino = Generate.nino
    implicit def getAuthHeader(authBaseUrl: String): (String, String) =
      AuthUtil.buildItsaUserToken(randomNino, randomItsa, authBaseUrl)

    val entityRepository: EntityRepository = app.injector.instanceOf[EntityRepository]
  }

  def additionalConfig: Map[String, Any] =
    Map(
      "play.http.router" -> "testOnlyDoNotUseInAppConf.Routes",
      "metrics.jvm"      -> false,
      "auditing.enabled" -> false
    )

  def preferencesResource(path: String): String = externalResource("preferences", path)
}

object UrlHelper {
  def -/(uri: String) =
    if (uri.startsWith("/")) uri.drop(1) else uri
}

case class TestId(testName: String) {
  val runId =
    DateTimeFormatter.ofPattern("HHmmssSSS").format(LocalDateTime.now())

  override val toString =
    s"${testName.toLowerCase.take(30)}-$runId"
}
