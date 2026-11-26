/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.metrics

import com.codahale.metrics.MetricRegistry
import com.mongodb.client.model.Filters
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ SerialisableTaxId, TaxIds }
import uk.gov.hmrc.entityresolver.connectors.PreferencesConnector
import uk.gov.hmrc.entityresolver.models.Entity
import uk.gov.hmrc.entityresolver.repository._
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import utils.Generate
import org.mongodb.scala.SingleObservableFuture
import scala.concurrent.ExecutionContext.Implicits._

class TaxIdMetricsSpec
    extends PlaySpec with MongoSupport with MockitoSugar with ScalaFutures with BeforeAndAfterEach
    with GuiceOneAppPerTest {
  self =>

  val testTaxIds: Set[SerialisableTaxId] =
    TaxIds.defaultSerialisableIds.filter(taxId => Seq("sautr", "nino", "ctutr").contains(taxId.taxIdName)).toSet

  override def fakeApplication(): Application = {
    val mockMetricsRegistry: MetricRegistry = mock[MetricRegistry]
    val mockMetrics: Metrics = mock[Metrics]
    when(mockMetrics.defaultRegistry).thenReturn(mockMetricsRegistry)

    new GuiceApplicationBuilder()
      .overrides(bind[PreferencesConnector].toInstance(mock[PreferencesConnector]))
      .overrides(bind[AuditConnector].toInstance(mock[AuditConnector]))
      .overrides(bind[Set[SerialisableTaxId]].qualifiedWith("soleTaxIds").toInstance(testTaxIds))
      .configure("appName" -> "test-app", "metrics.enabled" -> "false")
      .build()
  }

  "metrics" should {
    def entity(taxIds: TaxIdWithName*) = Entity(Generate.entityId, taxIds.toSet)

    "cause update of the counts" in {
      val entityRepository = app.injector.instanceOf[EntityRepository]
      entityRepository.collection.deleteMany(Filters.empty()).toFuture().futureValue
      val taxIdMetrics = app.injector.instanceOf[TaxIdMetrics]
      taxIdMetrics.refresh(entityRepository).futureValue

      val _: Unit = List(
        entity(Generate.utr),
        entity(Generate.utr),
        entity(Generate.nino),
        entity(Generate.nino, Generate.utr),
        entity(Generate.nino, Generate.itsa)
      ).foreach { e =>
        entityRepository.confirm(e).futureValue
      }

//      val storedMetrics = taxIdMetrics.metrics.futureValue

//      storedMetrics("entity.sole.sautr.count") mustBe 2
//      storedMetrics("entity.sole.nino.count") mustBe 2
//      storedMetrics("entity.sole.ctutr.count") mustBe 0
//      storedMetrics("entity.hmrc-mtd-it.count") mustBe 1
//      storedMetrics("entity.total") mustBe 5
    }
  }
}
