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

package uk.gov.hmrc.entityresolver.models

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain._

sealed trait BasicEntity {
  val id: EntityId
}

case class InvalidEntity(id: EntityId) extends BasicEntity

object BasicEntity {
  def apply(id: EntityId, taxIdentifiers: TaxIdWithName*): BasicEntity = apply(id, taxIdentifiers.toSet)

  def apply(id: EntityId, taxIdentifiers: Set[TaxIdWithName]): BasicEntity = taxIdentifiers match {
    case s if s.isEmpty => InvalidEntity(id)
    case s              => Entity(id, s)
  }
}

case class Entity(id: EntityId, taxIdentifiers: Set[TaxIdWithName]) extends BasicEntity {
  require(taxIdentifiers.nonEmpty, "Entity does need at least one tax identifier")
}

object Entity {
  def apply(entityId: EntityId, taxIdentifiers: TaxIdWithName*): Entity = Entity(entityId, taxIdentifiers.toSet)

  val toStrings: Set[TaxIdWithName] => Set[String] = _.map(taxId => s"${taxId.name}: ${taxId.value}")

  val reads = (
    (__ \ "_id").read[EntityId] and
      TaxIds.reads(TaxIds.defaultSerialisableIds.toSet[SerialisableTaxId])
  )((entityId, taxIds) => Entity.apply(entityId, taxIds.values))

  val writes = new Writes[Entity] {
    override def writes(entity: Entity): JsValue =
      Json.obj("_id" -> entity.id.value) ++ JsObject(entity.taxIdentifiers.map { id =>
        id.name -> Json.toJson(id.value)
      }.toSeq)
  }

  implicit val formats: Format[Entity] = Format(reads, writes)

  implicit class EntityOps(val entity: Entity) extends AnyVal {

    def getNino: Option[String] =
      entity.taxIdentifiers.collectFirst { case Nino(nino) => nino }

    def getSaUtr: Option[String] =
      entity.taxIdentifiers.collectFirst { case SaUtr(saUtr) => saUtr }

    def getItsa: Option[String] =
      entity.taxIdentifiers.collectFirst { case HmrcMtdItsa(itsa) => itsa }
  }
}
