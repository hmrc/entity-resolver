/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

object EnrolmentValidationError {

  sealed class ValidationError(val message: String, val statusCode: Int)

  final case class InvalidArnError(override val message: String, override val statusCode: Int)
      extends ValidationError(message, statusCode)

  final case class InvalidEnrolmentKeyError(override val message: String, override val statusCode: Int)
      extends ValidationError(message, statusCode)

  final case class EntityCheckError(override val message: String, override val statusCode: Int)
      extends ValidationError(message, statusCode)

  final case class EACDEnrolmentCheckError(override val message: String, override val statusCode: Int)
      extends ValidationError(message, statusCode)

  final case class CredentialsCheckError(override val message: String, override val statusCode: Int)
      extends ValidationError(message, statusCode)

  final case class LinkedPreferenceCheckError(override val message: String, override val statusCode: Int)
      extends ValidationError(message, statusCode)

}
