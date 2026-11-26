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
import play.api.libs.json.{ JsNumber, JsResultException }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ Nino, SaUtr }

class BasicEntitySpec extends PlaySpec {
  "BasicEntity" should {
    "return InvalidEntity if no tax IDs supplied" in {
      BasicEntity(entityId) must be(InvalidEntity(entityId))
    }
    "return Entity if tax IDs supplied" in {
      BasicEntity(entityId, utr) must be(Entity(entityId, Set[TaxIdWithName](utr)))
    }
    "Entity must have a tax ID" in {
      intercept[IllegalArgumentException] {
        Entity(entityId, Set.empty[TaxIdWithName])
      }.getMessage mustBe "requirement failed: Entity does need at least one tax identifier"
    }
    "Entity toStrings must have name and value for each tax ID" in {
      Entity.toStrings(Set[TaxIdWithName](utr, nino)) must be(Set("sautr: 1234", "nino: CE043355D"))
    }
    "Throw an exception for the EntityId if the Json is not a string" in {
      intercept[JsResultException] {
        JsNumber(1).as[EntityId]
      }
    }

  }

  val utrId = "1234"
  val ninoId = "CE043355D"
  val utr = SaUtr(utrId)
  val nino = Nino(ninoId)
  val entityId = EntityId("some val")
}
