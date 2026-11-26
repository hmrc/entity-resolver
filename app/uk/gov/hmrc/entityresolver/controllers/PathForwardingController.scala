/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.controllers

import com.typesafe.config.Config
import play.api.libs.json.Json
import play.api.mvc.{ Action, AnyContent, ControllerComponents, Results }
import play.api.{ Configuration, Logger }
import uk.gov.hmrc.entityresolver.binders.{ MappingConfiguration, TaxIdExtractionConfiguration }
import uk.gov.hmrc.entityresolver.models.*
import uk.gov.hmrc.entityresolver.services.PathMappingOperations
import uk.gov.hmrc.entityresolver.util.HeaderUtils
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{ Inject, Singleton }
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

@Singleton
class PathForwardingController @Inject() (
  pathMappingOperations: PathMappingOperations,
  cc: ControllerComponents,
  configuration: Configuration
) extends BackendController(cc) {

  private val logger = Logger(getClass.getName)

  @deprecated("Please use direct routes to preferences endpoints instead of using entity-resolver proxying")
  def doGet(path: String): Action[AnyContent] = Action.async { implicit request =>
    pathToPathMapping(path).map(pathMappingOperations.doGet).getOrElse {
      logger.error(s"[doGet] - Either Incorrect or No path mapping exists for $path")
      Future.successful(
        Results
          .NotFound(Json.obj("reason" -> s"Either Incorrect or No path mapping exists for $path"))
          .withHeaders(HeaderUtils.deprecation)
      )
    }
  }

  @deprecated("Please use direct routes to preferences endpoints instead of using entity-resolver proxying")
  def doPost(path: String): Action[AnyContent] = Action.async { implicit request =>
    pathToPathMapping(path).map(pathMappingOperations.doPost).getOrElse {
      logger.error(s"[doPost] - Either Incorrect or No path mapping exists for $path")
      Future.successful(
        Results
          .NotFound(Json.obj("reason" -> s"Either Incorrect or No path mapping exists for $path"))
          .withHeaders(HeaderUtils.deprecation)
      )
    }
  }

  @deprecated("Please use direct routes to preferences endpoints instead of using entity-resolver proxying")
  def doPut(path: String): Action[AnyContent] = Action.async { implicit request =>
    pathToPathMapping(path).map(pathMappingOperations.doPut).getOrElse {
      logger.error(s"[doPut] - Either Incorrect or No path mapping exists for $path")
      Future.successful(
        Results
          .NotFound(Json.obj("reason" -> s"Either Incorrect or No path mapping exists for $path"))
          .withHeaders(HeaderUtils.deprecation)
      )
    }
  }

  private[this] def pathToPathMapping(path: String): Option[PathMapping] =
    Try(mappingRegistry) match {
      case Success(value) => value.find(_.matches(path)).map(toPathMapping(path))
      case Failure(exception) =>
        logger.error(s"[PathMappingError] - error in path mapping for $path due to ${exception.getMessage}")
        None
    }

  def findMethod(name: String): TaxIdExtractionMethod =
    List(ReadIdsFromAuth, ReadIdFromPath, PassThrough)
      .find(_.name == name)
      .getOrElse(throw new RuntimeException(s"taxIdExtraction.method has an unsupported value: $name"))

  private def toPathMapping(path: String)(mappingConf: MappingConfiguration) = {
    val pathMapping = PathMapping(
      destinationUrl = mappingConf.destinationUrlPattern,
      taxIdExtraction = TaxIdExtraction(
        findMethod(mappingConf.taxIdExtraction.method),
        mappingConf.extractTaxId(path)
      ),
      serviceName = mappingConf.serviceName,
      auditIds = mappingConf.auditIds,
      resolveIds = mappingConf.resolveIds
    )
    logger.debug(s"path: '$path' mapped to '${pathMapping.destinationUrl}' in the '${pathMapping.serviceName}' service")
    pathMapping
  }

  private[this] def mappingRegistry: Set[MappingConfiguration] = {
    def extractConfig(config: Config): MappingConfiguration = {
      val regex = config.getString("regex").r
      val serviceName = config.getString("serviceName")
      val taxIdExtraction = TaxIdExtractionConfiguration.create(
        Try(config.getString("taxIdExtraction.method")).getOrElse(ReadIdsFromAuth.name),
        Try(config.getString("taxIdExtraction.idType")).toOption
      )
      val destinationUrlPattern = config.getString("destinationUrlPattern")
      val auditIds = Try(config.getBoolean("auditIds")).getOrElse(false)
      val resolveIds = Try(config.getBoolean("resolveIds")).getOrElse(false)

      MappingConfiguration(
        regex,
        serviceName,
        taxIdExtraction,
        destinationUrlPattern,
        auditIds,
        resolveIds
      )
    }

    import scala.jdk.CollectionConverters._
    configuration.underlying
      .getConfigList("pathMapping")
      .asScala
      .toSet
      .map(extractConfig)
  }
}
