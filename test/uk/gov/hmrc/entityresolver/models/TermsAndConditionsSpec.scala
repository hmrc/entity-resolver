/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.entityresolver.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsResultException, Json }
import utils.TestData.{ FIVE, TWO }

class TermsAndConditionsSpec extends PlaySpec {

  "TermsAndConditionsUpdate.from" should {
    "update Terms and condition with TaxCreditsTerms" in new Setup {
      TermsAndConditionsUpdate.from((TaxCreditsTerms, TermsAccepted(true)), None, None) must be(
        termsAndConditionsUpdateWithTaxCredits
      )
    }

    "update Terms and condition with GenericTerms" in new Setup {
      TermsAndConditionsUpdate.from((GenericTerms, TermsAccepted(true)), None, None) must be(
        termsAndConditionsUpdateWithGenericTerms
      )
    }
  }

  "TermsAndConditionsUpdate.format" should {
    import TermsAndConditionsUpdate.format

    "read the json correctly" in new Setup {
      Json
        .parse(termsAndConditionsUpdateJsonString)
        .as[TermsAndConditionsUpdate] mustBe termsAndConditionsUpdateWithTaxCredits
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(termsAndConditionsUpdateInvalidJsonString).as[TermsAndConditionsUpdate]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(termsAndConditionsUpdateWithTaxCredits) mustBe Json.parse(termsAndConditionsUpdateJsonString)
    }
  }

  "OptInPage.optInPageReads" should {
    import OptInPage.optInPageReads

    "read the json correctly" in new Setup {
      Json.parse(optInPageJsonString).as[OptInPage] mustBe optInPage
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(optInPageInvalidJsonString).as[OptInPage]
      }

      intercept[JsResultException] {
        Json.parse(optInPageInvalidPageTypeJsonString).as[OptInPage]
      }
    }
  }

  "OptInPage.optInPageWrites" should {
    "write the object correctly" in new Setup {
      Json.toJson(optInPage) mustBe Json.parse(optInPageJsonString)
    }
  }

  trait Setup {
    val termsAndConditionsUpdateWithTaxCredits: TermsAndConditionsUpdate =
      TermsAndConditionsUpdate(generic = None, taxCredits = Some(TermsAccepted(true)), email = None, None, None, None)

    val termsAndConditionsUpdateWithGenericTerms: TermsAndConditionsUpdate =
      TermsAndConditionsUpdate(generic = Some(TermsAccepted(true)), taxCredits = None, email = None, None, None, None)

    val termsAndConditionsUpdateJsonString: String = """{"taxCredits":{"accepted":true}}""".stripMargin
    val termsAndConditionsUpdateInvalidJsonString: String = """{"taxCredits":{"accepted1":true}}""".stripMargin

    val version: Version = Version(major = TWO, minor = FIVE)

    val optInPage: OptInPage = OptInPage(version = version, cohort = FIVE, pageType = PageType.IPage)

    val optInPageJsonString: String = """{"version":{"major":2,"minor":5},"cohort":5,"pageType":"IPage"}""".stripMargin

    val optInPageInvalidJsonString: String = """{"version":{"major":2,"minor":5},"pageType":"IPage"}""".stripMargin

    val optInPageInvalidPageTypeJsonString: String =
      """{"version":{"major":2,"minor":5},"cohort":5,"pageType":"UnknownPage"}""".stripMargin
  }
}
