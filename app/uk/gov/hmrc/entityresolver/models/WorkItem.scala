/*
 * Copyright 2024 HM Revenue & Customs
 *
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
