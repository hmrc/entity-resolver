/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.*

class EmailPreferenceSpec extends PlaySpec {

  "EmailPreference" should {

    "deserialize valid JSON correctly" in {
      val json = Json.parse("""
                              |{
                              |  "isVerified": true,
                              |  "hasBounces": false
                              |}""".stripMargin)

      val result = json.validate[EmailPreference]
      result.get mustBe EmailPreference(true, false)
    }

    "fail deserialization with invalid json" in {
      val json = Json.parse("""
                              |{
                              |  "hasBounces": false
                              |}
                              |""".stripMargin)

      val result = json.validate[EmailPreference]
      result.isError mustBe true
      result.fold(
        errors => errors.exists(_._1.toString equals "/isVerified") mustBe true,
        _ => fail("Expected validation error")
      )
    }
  }
}
