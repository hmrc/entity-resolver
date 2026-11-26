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
