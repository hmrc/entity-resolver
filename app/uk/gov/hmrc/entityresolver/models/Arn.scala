/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import uk.gov.hmrc.domain.{ Modulus23Check, SimpleObjectReads, SimpleObjectWrites, TaxIdentifier }

case class Arn(value: String) extends TaxIdentifier

object Arn {
  private val arnPattern = "^[A-Z]ARN[0-9]{7}$".r

  def isValid(arn: String): Boolean =
    arn match {
      case arnPattern(_*) => ArnCheck.isValid(arn)
      case _              => false
    }

  implicit val arnReads: SimpleObjectReads[Arn] = new SimpleObjectReads[Arn]("value", Arn.apply)
  implicit val arnWrites: SimpleObjectWrites[Arn] = new SimpleObjectWrites[Arn](_.value)
}

private object ArnCheck extends Modulus23Check {

  def isValid(arn: String): Boolean = {
    val suffix: String = arn.substring(1)
    calculateCheckCharacter(suffix) == arn.charAt(0)
  }
}
