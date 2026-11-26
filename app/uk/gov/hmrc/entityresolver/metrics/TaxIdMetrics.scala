/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.metrics

import javax.inject.{ Inject, Named, Singleton }
import uk.gov.hmrc.domain.SerialisableTaxId
import uk.gov.hmrc.entityresolver.repository._
import uk.gov.hmrc.mongo.metrix.MetricSource

import scala.concurrent.{ ExecutionContext, Future }
@Singleton
class TaxIdMetrics @Inject() (
  @Named("soleTaxIds") supportedSoleTaxIds: Set[SerialisableTaxId],
  @Named("taxIds") supportedTaxIds: Set[SerialisableTaxId]
) extends MetricSource {

  def metrics(implicit ec: ExecutionContext): Future[Map[String, Int]] =
    Future.successful(Map.empty)
  // DC-5305 disable metrics refresh for entity collection
  // refresh(entityMetricsRepository)

  def soleCountIdentifier(taxId: SerialisableTaxId): String = s"entity.sole.${taxId.taxIdName.toLowerCase()}.count"

  def countIdentifier(taxId: SerialisableTaxId): String = s"entity.${taxId.taxIdName.toLowerCase()}.count"

  def refresh(entityMetricsRepository: EntityRepository)(implicit ec: ExecutionContext): Future[Map[String, Int]] = {

    def countSoleByTaxId(taxId: SerialisableTaxId): Future[Map[String, Int]] =
      entityMetricsRepository.countBySoleTaxId(taxId).map { count =>
        Map[String, Int](soleCountIdentifier(taxId) -> count)
      }

    def countByTaxId(taxId: SerialisableTaxId): Future[Map[String, Int]] =
      entityMetricsRepository.countByTaxId(taxId).map { count =>
        Map[String, Int](countIdentifier(taxId) -> count)
      }

    val totals: List[Future[Map[String, Int]]] =
      entityMetricsRepository.countAll().map { count =>
        Map[String, Int]("entity.total" -> count.toInt)
      } :: supportedSoleTaxIds.toList.map {
        countSoleByTaxId
      } ++ supportedTaxIds.toList.map {
        countByTaxId
      }

    Future.sequence(totals).map {
      _.fold(Map.empty) {
        _ ++ _
      }
    }
  }
}
