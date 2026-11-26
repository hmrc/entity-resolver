/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.connectors

import javax.inject.{ Inject, Singleton }
import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ Nino, SaUtr }
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class TaxIdentifierRetriever @Inject() (val authConnector: core.AuthConnector)(implicit ec: ExecutionContext)
    extends AuthorisedFunctions {

  def taxIdentifiers(saUtr: Option[String], nino: Option[String]): Set[TaxIdWithName] =
    Set(saUtr.map(SaUtr.apply), nino.map(Nino.apply)).flatMap(_.toSeq)

  def currentTaxIdentifiers(implicit hc: HeaderCarrier): Future[Set[TaxIdWithName]] =
    authorised().retrieve(Retrievals.saUtr and Retrievals.nino) { case ~(saUtrOpt, ninoOpt) =>
      Future.successful(taxIdentifiers(saUtrOpt, ninoOpt))
    }

}
