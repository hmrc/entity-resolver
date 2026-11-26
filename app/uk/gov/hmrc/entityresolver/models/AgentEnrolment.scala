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

import play.api.libs.json.{ Json, Reads }

final case class AgentEnrolment(arn: String, itsaId: String, nino: String, sautr: String)

object AgentEnrolment {
  implicit val reads: Reads[AgentEnrolment] = Json.reads[AgentEnrolment]
}

case class EnrolmentKey(serviceName: String, key: String, value: String) {
  def asString: String =
    s"$serviceName~$key~$value"

  def serviceNameForError: String =
    serviceName match {
      case "HMRC-MTD-IT" => "MTD_IT"
      case _             => serviceName
    }
}

object EnrolmentKey {
  def parse(input: String): Either[String, EnrolmentKey] =
    input.split('~') match {
      case Array(serviceName, key, value) => Right(EnrolmentKey(serviceName, key, value))
      case _                              => Left("Unable to bind an EnrolmentKey")
    }
}
