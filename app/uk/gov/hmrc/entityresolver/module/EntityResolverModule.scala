/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.module

import com.google.inject.{ AbstractModule, Provides }
import play.api.Configuration
import play.api.mvc.Result
import play.api.mvc.Results.Status
import uk.gov.hmrc.domain.{ SerialisableTaxId, TaxIds }
import uk.gov.hmrc.entityresolver.EntityResolverMain
import uk.gov.hmrc.entityresolver.config.RunModeBridge
import uk.gov.hmrc.entityresolver.connectors.GenericConnector
import uk.gov.hmrc.entityresolver.metrics.{ MetricsLock, TaxIdMetrics }
import uk.gov.hmrc.entityresolver.models.EntityId
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{ HttpReads, HttpResponse }
import uk.gov.hmrc.mongo.lock.{ LockRepository, LockService, MongoLockRepository }
import uk.gov.hmrc.mongo.metrix.{ MetricOrchestrator, MetricSource, MongoMetricRepository }
import uk.gov.hmrc.mongo.{ MongoComponent, TimestampSupport }
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{ Named, Singleton }
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, MILLISECONDS }

class EntityResolverModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[EntityResolverMain]).asEagerSingleton()

  @Provides
  @Singleton
  def lockRepositoryProvider(mongo: MongoComponent, timestampSupport: TimestampSupport)(implicit
    ec: ExecutionContext
  ): LockRepository =
    new MongoLockRepository(mongo, timestampSupport)

  @Provides
  @Singleton
  def mongoMetricsRepositoryProvider(mongoComponent: MongoComponent)(implicit
    ec: ExecutionContext
  ): MongoMetricRepository = new MongoMetricRepository(mongoComponent)
  @Provides
  @Singleton
  @Named("genericConnectorRegistry")
  def genericConnectorsProvider(runModeBridge: RunModeBridge, http: HttpClientV2, servicesConfig: ServicesConfig)(
    implicit ec: ExecutionContext
  ): Map[String, GenericConnector] = {
    val microservicesConfig: Configuration = runModeBridge
      .getConfig("microservice.services")
      .getOrElse(throw new RuntimeException("No microservice.services config defined"))

    microservicesConfig.subKeys.map { serviceName =>
      serviceName ->
        new GenericConnector(http, servicesConfig.baseUrl(serviceName))
    }.toMap
  }

  // noinspection ConvertExpressionToSAM
  @Provides
  @Singleton
  @Named("httpReadsResult")
  def resultHttpReads: HttpReads[Result] = new HttpReads[Result] {
    override def read(method: String, url: String, response: HttpResponse): Result =
      Status(response.status)(response.body)
  }

  @Provides
  @Named("refreshInterval")
  @Singleton
  def refreshIntervalProvider(runModeBridge: RunModeBridge): Long =
    runModeBridge.getLongMillis("microservice.metrics.gauges.interval")

  @Provides
  @Singleton
  def metricsLockProvider(
    @Named("refreshInterval") refreshInterval: Long,
    lockRepository: LockRepository
  ): MetricsLock =
    MetricsLock("message-metrics", Duration(refreshInterval, MILLISECONDS), lockRepository)
  @Provides
  @Singleton
  def metricsSourcesProvider(taxIdMetrics: TaxIdMetrics): List[MetricSource] = List(taxIdMetrics)

  @Provides
  @Singleton
  def metricsOrchestratorProvider(
    sources: List[MetricSource],
    metricsLock: MetricsLock,
    mongoMetricRepository: MongoMetricRepository,
    metrics: Metrics
  ): MetricOrchestrator =
    new MetricOrchestrator(
      sources,
      LockService(metricsLock.lockRepository, metricsLock.lockId, metricsLock.ttl),
      mongoMetricRepository,
      metrics.defaultRegistry
    )

  @Provides
  @Singleton
  def entityIdGenerateFunctionProvider(): () => EntityId = () => EntityId.generate()

  @Provides
  @Singleton
  @Named("soleTaxIds")
  def supportedSoleTaxIdProvider(): Set[SerialisableTaxId] =
    TaxIds.defaultSerialisableIds.filter(taxId => Seq("sautr", "nino").contains(taxId.taxIdName)).toSet

  @Provides
  @Singleton
  @Named("taxIds")
  def supportedTaxIdProvider(): Set[SerialisableTaxId] =
    TaxIds.defaultSerialisableIds.filter(taxId => Seq("HMRC-MTD-IT").contains(taxId.taxIdName)).toSet

}
