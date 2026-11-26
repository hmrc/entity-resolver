/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

case class PreferenceStatus(
  maybeGeneric: Option[Boolean],
  maybeTaxCredits: Option[Boolean]
)
