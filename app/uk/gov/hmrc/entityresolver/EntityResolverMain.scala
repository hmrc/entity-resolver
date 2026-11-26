/*
 * Copyright 2026 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver

import org.apache.pekko.actor.{ ActorSystem, Cancellable }
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.mongo.metrix.MetricOrchestrator

import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.duration.DurationLong
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EntityResolverMain @Inject() (
  @Named("refreshInterval") refreshInterval: Long,
  metricOrchestrator: MetricOrchestrator,
  actorSystem: ActorSystem,
  lifecycle: ApplicationLifecycle
)(implicit val ec: ExecutionContext) {

  val logger: Logger = Logger(getClass)

  lifecycle.addStopHook(() =>
    Future {
      mc.cancel()
      actorSystem.terminate()
      metricOrchestrator
    }
  )

  // $COVERAGE-OFF$
  val mc: Cancellable = actorSystem.scheduler.scheduleAtFixedRate(60 seconds, refreshInterval milliseconds) { () =>
    metricOrchestrator
      .attemptMetricRefresh()
      .map(_.log())
      .recover { case e: RuntimeException =>
        logger.error(s"An error occurred processing metrics: ${e.getMessage}", e)
      }
  }
  // $COVERAGE-ON$
}
