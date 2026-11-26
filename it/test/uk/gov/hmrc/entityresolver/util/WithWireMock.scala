/*
 * Copyright 2019 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.util

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }

trait WithWireMock extends BeforeAndAfterAll with BeforeAndAfterEach { suite: Suite =>

  def dependenciesPort = 5088
  lazy val wireMockServer = new WireMockServer(wireMockConfig().port(dependenciesPort))

  // IMPORTANT: Entity-resolver endpoint for dependent service call back
  lazy val wireMockServerEntityResolver = new WireMockServer(wireMockConfig().port(8015))

  override def beforeAll(): Unit = {
    super.beforeAll()
    wireMockServer.start()
    wireMockServerEntityResolver.start()
  }
  override def beforeEach(): Unit = {
    super.beforeEach()
    wireMockServer.resetMappings()
    wireMockServer.resetRequests()
    wireMockServerEntityResolver.resetMappings()
    wireMockServerEntityResolver.resetRequests()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    wireMockServer.stop()
    wireMockServerEntityResolver.stop()
    wireMockServer.shutdown()
    wireMockServerEntityResolver.shutdown()
  }

  def stubEntityResolverTaxIdUtrNino(): StubMapping =
    wireMockServerEntityResolver.stubFor(
      get(urlMatching("/entity-resolver?.*"))
        .willReturn(okJson(s"""{ "_id": "1", "nino": "AB112233A", "sautr": "123456"}"""))
    )

  def stubEntityResolverTaxIdNino(): StubMapping =
    wireMockServerEntityResolver.stubFor(
      get(urlMatching("/entity-resolver?.*"))
        .willReturn(okJson(s"""{ "_id": "1", "nino": "AB112233A"}"""))
    )
}
