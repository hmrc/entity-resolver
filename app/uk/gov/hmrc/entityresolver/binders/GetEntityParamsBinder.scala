/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.binders

import play.api.mvc.QueryStringBindable

case class GetEntityParamsBinder(
  taxRegime: Option[String],
  taxId: Option[String],
  entityId: Option[String],
  resolve: Option[Boolean],
  resolveIds: Option[Boolean] = None
) {
  def getResolveIds: Boolean = resolveIds.getOrElse(false)
}

object GetEntityParamsBinder {

  implicit def queryStringBindable(implicit
    stringBinder: QueryStringBindable[String]
  ): QueryStringBindable[GetEntityParamsBinder] =
    new QueryStringBindable[GetEntityParamsBinder] {
      override def bind(
        key: String,
        params: Map[String, Seq[String]]
      ): Option[Either[String, GetEntityParamsBinder]] = {
        val taxRegime = params.get("taxRegime").flatMap(_.headOption)
        val taxId = params.get("taxId").flatMap(_.headOption)
        val entityId = params.get("entityId").flatMap(_.headOption)
        val resolve = params.get("resolve").flatMap(_.headOption.flatMap(_.toBooleanOption))
        val resolveIds = params.get("resolveIds").flatMap(_.headOption.flatMap(_.toBooleanOption))

        Some(Right(GetEntityParamsBinder(taxRegime, taxId, entityId, resolve, resolveIds)))
      }

      override def unbind(key: String, params: GetEntityParamsBinder): String =
        Seq(
          params.taxRegime.map("taxRegime=" + _),
          params.taxId.map("taxId=" + _),
          params.entityId.map("entityId=" + _),
          params.resolve.map("resolve=" + _),
          params.resolveIds.map("resolveIds=" + _)
        ).flatten.mkString("&")
    }
}
