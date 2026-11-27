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
