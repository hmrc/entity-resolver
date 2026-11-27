/*
 * Copyright 2024 HM Revenue & Customs
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

import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.entityresolver.models.Entity

case class LookupResult(
  masterEntity: Option[Entity],
  conflictingEntity: Option[Entity],
  needsConflictResolution: Boolean = true
)

object LookupResult {

  import ConflictResolutionUtils._

  def apply(entities: List[Entity], taxIdsInAuth: Set[TaxIdWithName]): LookupResult = {
    if (entities.size > 2) reportUnimplementedScenario(entities, taxIdsInAuth, "More than two entities found")

    val masterEntity: Option[Entity] = entities.find(entity => theEntitySharesTheUtrWithAuth(entity, taxIdsInAuth))

    if (masterEntity.isEmpty && entities.size == 2) {
      reportUnimplementedScenario(
        entities,
        taxIdsInAuth,
        "Two entities found for the current tax identifier but none is the utr"
      )
    }

    val conflictingEntity: Option[Entity] =
      entities.find(entity => !theEntitySharesTheUtrWithAuth(entity, taxIdsInAuth))

    val needsConflictResolution: Boolean = (masterEntity, conflictingEntity) match {
      case (Some(me), None) if me.taxIdentifiers == taxIdsInAuth => false
      case (None, Some(ce)) if ce.taxIdentifiers == taxIdsInAuth => false
      case _                                                     => true
    }
    new LookupResult(masterEntity, conflictingEntity, needsConflictResolution)
  }
}
