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

import play.api.libs.json.Json
import play.api.libs.json._

sealed trait TermsType

case object GenericTerms extends TermsType

case object TaxCreditsTerms extends TermsType

case class TermsAccepted(accepted: Boolean, optInPage: Option[OptInPage] = None)

object TermsAccepted {
  implicit val format: OFormat[TermsAccepted] = Json.format[TermsAccepted]
}

case class TermsAndConditionsUpdate(
  generic: Option[TermsAccepted],
  taxCredits: Option[TermsAccepted],
  email: Option[String],
  returnText: Option[String],
  returnUrl: Option[String],
  language: Option[Language] = None
)

object TermsAndConditionsUpdate {
  implicit val format: OFormat[TermsAndConditionsUpdate] = Json.format[TermsAndConditionsUpdate]

  def from(
    terms: (TermsType, TermsAccepted),
    email: Option[String],
    language: Option[Language] = None
  ): TermsAndConditionsUpdate = terms match {
    case (GenericTerms, accepted: TermsAccepted) =>
      TermsAndConditionsUpdate(generic = Some(accepted), None, email = email, None, None, language)
    case (TaxCreditsTerms, accepted: TermsAccepted) =>
      TermsAndConditionsUpdate(generic = None, taxCredits = Some(accepted), email = email, None, None, language)
  }
}

enum PageType {
  case IPage, TCPage, ReOptInPage
}

object PageType {
  implicit val format: Format[PageType] = new Format[PageType] {
    def reads(json: JsValue): JsResult[PageType] = json match {
      case JsString("IPage")       => JsSuccess(IPage)
      case JsString("TCPage")      => JsSuccess(TCPage)
      case JsString("ReOptInPage") => JsSuccess(ReOptInPage)
      case _                       => JsError("Invalid Page Type")
    }

    def writes(pageType: PageType): JsValue = JsString(pageType.toString)
  }
}

case class Version(major: Int, minor: Int)

object Version {
  implicit val versionFormat: OFormat[Version] = Json.format[Version]
}

case class OptInPage(version: Version, cohort: Int, pageType: PageType)

object OptInPage {
  implicit val optInPageWrites: Writes[OptInPage] = Json.writes[OptInPage]
  implicit val optInPageReads: Reads[OptInPage] = Json.reads[OptInPage]
}
