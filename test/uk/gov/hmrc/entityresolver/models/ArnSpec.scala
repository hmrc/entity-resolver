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
