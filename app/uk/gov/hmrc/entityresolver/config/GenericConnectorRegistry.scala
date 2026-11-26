/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.config

import javax.inject.{ Inject, Named, Singleton }
import uk.gov.hmrc.entityresolver.connectors.GenericConnector

@Singleton
class GenericConnectorRegistry @Inject() (
  @Named("genericConnectorRegistry") genericConnectorRegistry: Map[String, GenericConnector]
) {
  def get(serviceName: String): GenericConnector = genericConnectorRegistry(serviceName)
}
