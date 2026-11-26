/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.binders

import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ SerialisableTaxId, TaxIds }
import uk.gov.hmrc.entityresolver.models._

import scala.util.Try
import scala.util.matching.Regex

case class MappingConfiguration(
  regex: Regex,
  serviceName: String,
  taxIdExtraction: TaxIdExtractionConfiguration,
  destinationUrlPattern: String,
  auditIds: Boolean = false,
  resolveIds: Boolean = false
) {

  def matches(path: String): Boolean = path match {
    case regex(taxId)             => taxId.nonEmpty
    case _ if regex.regex == path => true
    case _                        => false
  }

  private val knownTaxIds: Seq[SerialisableTaxId] = TaxIds.defaultSerialisableIds

  def extractTaxId(path: String): Option[TaxIdWithName] =
    path match {
      case regex(taxId) if taxIdExtraction.idName.nonEmpty && taxIdExtraction.method == ReadIdFromPath.name =>
        Try(knownTaxIds.filter(id => id.taxIdName == taxIdExtraction.idName.get).head.build(taxId)).toOption
      case regex(taxId) if taxIdExtraction.method == PassThrough.name =>
        Some(PassThroughId(taxId))
      case _ if regex.regex == path || taxIdExtraction.method == ReadIdsFromAuth.name =>
        None
      case _ =>
        throw new IllegalArgumentException(s"cannot extract taxId from $path using $regex")
    }
}

case class TaxIdExtractionConfiguration(method: String, idName: Option[String])

object TaxIdExtractionConfiguration {
  def create(method: String, idName: Option[String]): TaxIdExtractionConfiguration = {
    if (method == ReadIdFromPath.name && idName.isEmpty) {
      throw new RuntimeException(s"$method method for taxIdExtraction must also contain an idType")
    }
    new TaxIdExtractionConfiguration(method, idName)
  }
}
