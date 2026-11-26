/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.metrics

import uk.gov.hmrc.mongo.lock.{ LockRepository, TimePeriodLockService }

import scala.concurrent.duration.Duration

final case class MetricsLock(lockId: String, ttl: Duration, lockRepository: LockRepository)
    extends TimePeriodLockService
