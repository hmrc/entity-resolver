/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import play.api.libs.json.{ Json, Reads }

final case class PrincipalUserIdResponse(principalUserIds: List[String])

object PrincipalUserIdResponse {

  implicit val reads: Reads[PrincipalUserIdResponse] = Json.reads[PrincipalUserIdResponse]

}
