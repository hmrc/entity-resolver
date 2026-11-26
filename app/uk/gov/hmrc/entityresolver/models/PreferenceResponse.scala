/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import play.api.libs.json.{ Json, Reads }

case class PreferenceResponse(
  email: Option[EmailPreference],
  digital: Boolean
) {
  def isDigitalStatus: Boolean = {
    val isEmailVerified = email.map(_.isVerified).exists(identity)
    val hasBounces = email.map(_.hasBounces).exists(identity)
    digital && isEmailVerified && !hasBounces
  }
}

object PreferenceResponse {
  implicit val reads: Reads[PreferenceResponse] = Json.reads[PreferenceResponse]
}

case class EmailPreference(
  isVerified: Boolean,
  hasBounces: Boolean
)

object EmailPreference {
  implicit val reads: Reads[EmailPreference] = Json.reads[EmailPreference]
}
