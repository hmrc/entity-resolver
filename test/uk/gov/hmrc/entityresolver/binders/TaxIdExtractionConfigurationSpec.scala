/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.binders

import org.scalatestplus.play.PlaySpec

class TaxIdExtractionConfigurationSpec extends PlaySpec {

  "When using ReadIdFromPath method an idType has to be provided" in {
    intercept[RuntimeException] {
      TaxIdExtractionConfiguration.create("ReadIdFromPath", None)
    }.getMessage mustBe "ReadIdFromPath method for taxIdExtraction must also contain an idType"
  }

}
