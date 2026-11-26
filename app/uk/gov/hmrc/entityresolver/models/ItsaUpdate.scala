/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import play.api.libs.json.*
import play.api.libs.functional.syntax.*
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr, TaxIdentifier }

case class ItsaUpdateRequest(nino: Nino, sautr: SaUtr, mtditsaid: HmrcMtdItsa) {
  def taxIds: Set[TaxIdWithName] = Set(nino, sautr, mtditsaid)
}

object ItsaUpdateRequest {

  val itsaUpdateReads: Reads[ItsaUpdateRequest] = (
    (__ \ "nino").read[Nino] and
      (__ \ "sautr").read[SaUtr] and
      (__ \ "mtditsaid").read[HmrcMtdItsa]
  )(ItsaUpdateRequest.apply _)

  val itsaUpdateWrites: Writes[ItsaUpdateRequest] = Json.writes[ItsaUpdateRequest]

  given Format[ItsaUpdateRequest] = Format(itsaUpdateReads, itsaUpdateWrites)
}

sealed trait ItsaUpdateResult

object ItsaUpdateResult {
  final case class UpdateSuccess(preferences: JsValue) extends ItsaUpdateResult
  case object UpdateAlreadyExists extends ItsaUpdateResult
  case object ItsaIdConflict extends ItsaUpdateResult
  case object EntityNotFound extends ItsaUpdateResult
  case object MultipleEntitiesFound extends ItsaUpdateResult
  case object PreferencesNotFound extends ItsaUpdateResult
}
