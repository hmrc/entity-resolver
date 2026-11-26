/*
 * Copyright 2024 HM Revenue & Customs
 *
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
