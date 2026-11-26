/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.*

class PreferenceResponseSpec extends PlaySpec {

  "PreferenceResponse" should {

    "deserialize valid JSON correctly" in {
      val json = Json.parse("""
                              |{
                              |  "email": {
                              |    "isVerified": true,
                              |    "hasBounces": false
                              |  },
                              |  "digital": true
                              |}""".stripMargin)

      val result = json.validate[PreferenceResponse]
      result.get.isDigitalStatus mustBe true
    }

    "fail deserialization with invalid json" in {
      val json = Json.parse("""
                              |{
                              |  "email": {
                              |    "isVerified": true,
                              |    "hasBounces": false
                              |  }
                              |}
                              |""".stripMargin)

      val result = json.validate[PreferenceResponse]
      result.isError mustBe true
      result.fold(
        errors => errors.exists(_._1.toString equals "/digital") mustBe true,
        _ => fail("Expected validation error")
      )
    }
  }
}
