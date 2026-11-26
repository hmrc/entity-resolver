/*
 * Copyright 2023 HM Revenue & Customs
 *
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
