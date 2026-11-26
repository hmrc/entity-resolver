/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsNumber, JsString, Json }
import uk.gov.hmrc.entityresolver.models.Language.{ English, Welsh }

class LanguageSpec extends PlaySpec {
  "Language" should {
    "default to english if not cy" in {
      JsString("error").as[Language] must be(English)
    }
    "default to english if json not a String" in {
      JsNumber(1).as[Language] must be(English)
    }
    "return welsh if CY" in {
      JsString("CY").as[Language] must be(Welsh)
    }
    "convert Language to Json String" in {
      Json.toJson[Language](English) must be(JsString("en"))
    }
  }
}
