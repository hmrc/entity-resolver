/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import uk.gov.hmrc.domain.{ SimpleName, TaxIdentifier }

case class PassThroughId(value: String) extends TaxIdentifier with SimpleName {
  val name = "passThroughId"
}
