/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.config

import javax.inject.{ Inject, Singleton }
import play.api.Configuration

import scala.concurrent.duration._

@Singleton
class RunModeBridge @Inject() (configuration: Configuration) {

  def getLongMillis(suffix: String): Long =
    configuration.getMillis(s"$suffix")

  def getConfig(suffix: String): Option[Configuration] =
    configuration.getOptional[Configuration](s"$suffix")
}
