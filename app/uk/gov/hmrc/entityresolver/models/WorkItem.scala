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

import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.domain.Nino

import java.time.Instant

case class NinoItem(nino: Nino, formType: String, suppressible: Boolean)

case class EntityIdItem(entityId: EntityId, suppressible: Boolean)

case class NoticeOfCodingWorkItem[A](
  id: String,
  updatedAt: Instant,
  receivedAt: Instant,
  availableAt: Instant,
  status: String,
  failureCount: Int,
  statusUrl: String,
  item: A
) {
  def replaceItem[B](otherItem: B): NoticeOfCodingWorkItem[B] = copy(item = otherItem)
}

object WorkItemFormats {
  implicit val ninoItemFormats: OFormat[NinoItem] = Json.format[NinoItem]
  implicit val entityIdItemFormats: OFormat[EntityIdItem] = Json.format[EntityIdItem]

  implicit def format[A: Format]: Format[NoticeOfCodingWorkItem[A]] =
    ((__ \ "id").format[String] ~
      (__ \ "updatedAt").format[Instant] ~
      (__ \ "receivedAt").format[Instant] ~
      (__ \ "availableAt").format[Instant] ~
      (__ \ "status").format[String] ~
      (__ \ "failureCount").format[Int] ~
      (__ \ "statusUrl").format[String] ~
      (__ \ "item").format[A])(NoticeOfCodingWorkItem.apply, item => Tuple.fromProductTyped(item))
}
