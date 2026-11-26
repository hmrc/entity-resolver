/*
 * Copyright 2026 HM Revenue & Customs
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
