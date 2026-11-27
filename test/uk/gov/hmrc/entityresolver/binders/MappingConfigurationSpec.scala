/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.entityresolver.binders

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.entityresolver.models.PassThroughId
import scala.util.matching.Regex

class MappingConfigurationSpec extends PlaySpec {
  "MappingConfiguration" should {
    "return true if path in regex" in {
      getMappingConf().matches("pref") must be(true)
    }
    "return None regex == path and Read IDs from Auth" in {
      getMappingConf("pref".r).extractTaxId("pref") must be(None)
    }
    "throw exception if unable to extract id from path" in {
      intercept[IllegalArgumentException] {
        getMappingConf(idExtract = passThrough).extractTaxId("blah")
      }
    }
    "pass through the taxID" in {
      getMappingConf(idExtract = passThrough).extractTaxId("pref") must be(Some(PassThroughId("pref")))
    }
    "extract correct taxid" in {
      getMappingConf(idExtract = readFromPath).extractTaxId("pref") must be(Some(SaUtr("pref")))
    }
  }
  val idExtract: TaxIdExtractionConfiguration = TaxIdExtractionConfiguration.create("ReadIdsFromAuth", Some("sautr"))
  val passThrough = TaxIdExtractionConfiguration.create("PassThrough", Some("sautr"))
  val readFromPath = TaxIdExtractionConfiguration.create("ReadIdFromPath", Some("sautr"))
  def getMappingConf(r: Regex = "(pref)".r, idExtract: TaxIdExtractionConfiguration = idExtract): MappingConfiguration =
    MappingConfiguration(r, "pref", idExtract, "")

}
