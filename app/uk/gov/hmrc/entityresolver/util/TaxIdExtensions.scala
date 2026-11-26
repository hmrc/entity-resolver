/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.util

import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr, TaxIds }
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.entityresolver.exceptions.InvalidTaxRegimeException

object TaxIdExtensions {
  implicit class StringExtensions(val s: String) {
    def toTaxId(regime: String, taxIdValue: String): TaxIdWithName =
      regime match {
        case Regime.PAYE.value => Nino.apply(taxIdValue)
        case Regime.SA.value   => SaUtr.apply(taxIdValue)
        case Regime.ITSA.value => HmrcMtdItsa.apply(taxIdValue)
        case _                 => throw new InvalidTaxRegimeException(s"Invalid tax regime: $regime")
      }
  }
  enum Regime(val value: String) {
    case PAYE extends Regime("paye")
    case SA extends Regime("sa")
    case ITSA extends Regime("itsa")
  }
}
