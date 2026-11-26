/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsString, Json }

class ArnSpec extends PlaySpec {
  "Arn" should {
    "convert Arn to Json" in {
      Json.toJson(Arn("AARN1234567")) must be(JsString("AARN1234567"))
    }
    "read from Json to Arn" in {
      JsString("AARN1234567").as[Arn] must be(Arn("AARN1234567"))
    }
  }
}
