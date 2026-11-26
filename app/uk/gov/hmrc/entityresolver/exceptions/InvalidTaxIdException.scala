/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.exceptions

class InvalidTaxIdException(msg: String) extends RuntimeException(msg)

class InvalidTaxRegimeException(msg: String) extends RuntimeException(msg)
