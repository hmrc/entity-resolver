/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.libs.json._
import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr }

class ItsaUpdateRequestSpec extends PlaySpec {

  "ItsaUpdateRequest JSON Format" should {

    "deserialize valid JSON correctly" in {
      val json = Json.parse("""
                              |{
                              |  "nino": "SJ123456A",
                              |  "sautr": "1234567890",
                              |  "mtditsaid": "XAIT12345678901"
                              |}
                              |""".stripMargin)

      val result = json.validate[ItsaUpdateRequest]
      result.isSuccess mustBe true

      val request = result.get
      request.nino.value mustBe "SJ123456A"
      request.sautr.value mustBe "1234567890"
      request.mtditsaid.value mustBe "XAIT12345678901"
    }

    "fail deserialization with invalid NINO" in {
      val json = Json.parse("""
                              |{
                              |  "nino": "SJ12345",
                              |  "sautr": "1234567890",
                              |  "mtditsaid": "XAIT12345678901"
                              |}
                              |""".stripMargin)

      val result = json.validate[ItsaUpdateRequest]
      result.isError mustBe true
      result.fold(
        errors => errors.exists(_._2.exists(_.message.contains("SJ12345 is not a valid nino"))) mustBe true,
        _ => fail("Expected validation error")
      )
    }

    "serialize correctly to JSON" in {
      val request = ItsaUpdateRequest(
        nino = Nino("SJ123456A"),
        sautr = SaUtr("1234567890"),
        mtditsaid = HmrcMtdItsa("XAIT12345678901")
      )

      val json = Json.toJson(request)
      (json \ "nino").as[String] mustBe "SJ123456A"
      (json \ "sautr").as[String] mustBe "1234567890"
      (json \ "mtditsaid").as[String] mustBe "XAIT12345678901"
    }
  }

  "ItsaUpdateRequest.taxIds" should {
    "return all tax identifiers in a Set" in {
      val request = ItsaUpdateRequest(
        nino = Nino("SJ123456A"),
        sautr = SaUtr("1234567890"),
        mtditsaid = HmrcMtdItsa("XAIT12345678901")
      )

      val ids = request.taxIds
      ids must contain allOf (Nino("SJ123456A"), SaUtr("1234567890"), HmrcMtdItsa("XAIT12345678901"))
    }
  }
}
