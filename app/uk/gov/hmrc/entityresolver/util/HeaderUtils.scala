/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.util

object HeaderUtils {
  def deprecation: (String, String) =
    "Deprecation" ->
      "Proxying via entity-resolver will be retired, use direct preferences endpoint instead"
}
