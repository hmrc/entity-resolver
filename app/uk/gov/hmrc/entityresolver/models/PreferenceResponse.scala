/*
 * Copyright 2026 HM Revenue & Customs
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
