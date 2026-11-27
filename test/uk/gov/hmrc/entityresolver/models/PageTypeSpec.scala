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
import play.api.libs.json.{ JsResultException, JsString, Json }
import uk.gov.hmrc.entityresolver.models.PageType.{ IPage, ReOptInPage, TCPage }

class PageTypeSpec extends PlaySpec {

  "PageType" must {
    """be successfully deserialized from string "IPage" """ in {
      JsString("IPage").as[PageType] must be(IPage)
    }
    """be successfully deserialized from string "TCPage" """ in {
      JsString("TCPage").as[PageType] must be(TCPage)
    }

    """be successfully deserialized from string "ReOptInPage" """ in {
      JsString("ReOptInPage").as[PageType] must be(ReOptInPage)
    }

    """throw NoSuchElementException when deserialized from invalid string """.stripMargin in {
      intercept[JsResultException] {
        JsString("foobar").as[PageType]
      }
    }

    """be serialized to JsString("IPage")""" in {
      Json.toJson(IPage) must be(JsString("IPage"))
    }

    """be serialized to JsString("TCPage")""" in {
      Json.toJson(TCPage) must be(JsString("TCPage"))
    }

    """be serialized to JsString("ReOptInPage")""" in {
      Json.toJson(ReOptInPage) must be(JsString("ReOptInPage"))
    }

  }

}
