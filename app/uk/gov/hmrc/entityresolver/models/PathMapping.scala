/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import uk.gov.hmrc.domain.TaxIds.TaxIdWithName

case class TaxIdExtraction(method: TaxIdExtractionMethod, idInPath: Option[TaxIdWithName])
case class PathMapping(
  destinationUrl: String,
  taxIdExtraction: TaxIdExtraction,
  serviceName: String,
  auditIds: Boolean = false,
  resolveIds: Boolean = false
)
