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

package uk.gov.hmrc.entityresolver.services

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.TaxIds._
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import utils.Generate

class ConflictResolutionUtilsSpec extends PlaySpec {

  "findUtr" should {

    "return the utr if present in the input" in {
      val utr = Generate.utr
      val taxIdentifiers: Set[TaxIdWithName] = Set(utr, Generate.nino)
      ConflictResolutionUtils.findUtr(taxIdentifiers) mustBe Some(utr)
    }

    "return nothing if the utr is not present in the input" in {
      val taxIdentifiers: Set[TaxIdWithName] = Set(Generate.nino)
      ConflictResolutionUtils.findUtr(taxIdentifiers) mustBe None
    }
  }

  "theEntitySharesTheUtrWithAuth" should {
    "return true if the utr from entity is the same as from the auth record" in {
      val utr = Generate.utr
      val entity = Entity(EntityId.generate(), utr)
      val taxIdsFromAuth: Set[TaxIdWithName] = Set(utr, Generate.nino)
      ConflictResolutionUtils.theEntitySharesTheUtrWithAuth(entity, taxIdsFromAuth) mustBe true
    }
    "return false if the utr from entity is not the same as from the auth record" in {
      val utr = Generate.utr
      val entity = Entity(EntityId.generate(), utr)

      val differentUtr = Generate.utr
      val taxIdsFromAuth: Set[TaxIdWithName] = Set(differentUtr, Generate.nino)
      ConflictResolutionUtils.theEntitySharesTheUtrWithAuth(entity, taxIdsFromAuth) mustBe false
    }
    "return false if no utr is found from the entity" in {
      val nino = Generate.nino
      val entity = Entity(EntityId.generate(), nino)
      val taxIdsFromAuth: Set[TaxIdWithName] = Set(nino, Generate.utr)
      ConflictResolutionUtils.theEntitySharesTheUtrWithAuth(entity, taxIdsFromAuth) mustBe false
    }
    "return false if the entity has the utr but the auth doesn't" in {
      val nino = Generate.nino
      val entity = Entity(EntityId.generate(), Generate.utr, nino)
      val taxIdsFromAuth: Set[TaxIdWithName] = Set(nino)
      ConflictResolutionUtils.theEntitySharesTheUtrWithAuth(entity, taxIdsFromAuth) mustBe false
    }
  }
}
