/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

case class TaxIdExtractionMethod(name: String)

object ReadIdsFromAuth extends TaxIdExtractionMethod("ReadIdsFromAuth")
object PassThrough extends TaxIdExtractionMethod("PassThrough")
object ReadIdFromPath extends TaxIdExtractionMethod("ReadIdFromPath")
