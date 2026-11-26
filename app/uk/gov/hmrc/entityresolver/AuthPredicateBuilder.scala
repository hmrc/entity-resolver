/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver

import play.api.Configuration
import uk.gov.hmrc.auth.core.{ ConfidenceLevel, Enrolment }
import uk.gov.hmrc.auth.core.authorise.Predicate

case class AuthPredicateBuilder(controllerName: String, controllerConfigs: Option[Configuration]) {

  import AuthPredicateBuilder._

  private lazy val globalConfidenceLevel: AuthResult[ConfidenceLevel] =
    resolveControllerConfigs.flatMap(confidence)

  def confidenceLevel(config: Configuration): Option[ConfidenceLevel] =
    config
      .getOptional[Int]("confidenceLevel")
      .flatMap(ConfidenceLevel.fromInt(_).toOption)

  def resolveControllerConfigs: AuthResult[Configuration] =
    controllerConfigs.toRight(NoControllersConfigDefined())

  def confidence(config: Configuration): AuthResult[ConfidenceLevel] =
    confidenceLevel(config) match {
      case Some(c) => Right(c)
      case None    => globalConfidenceLevel
    }

  def enrolment(config: Configuration): AuthResult[Enrolment] =
    config
      .getOptional[String]("enrolment")
      .map(Enrolment(_))
      .toRight(NoEnrolmentDefined(controllerName))

  def authPredicate(config: Configuration): AuthResult[Predicate] =
    for {
      enrolment  <- enrolment(config)
      confidence <- confidence(config)
    } yield enrolment and confidence

  def authParams(config: Configuration): AuthResult[Configuration] =
    config
      .getOptional[Configuration](s"$controllerName.authParams")
      .toRight(AuthParamsNotDefined(controllerName))

  def authConfig: AuthResult[Predicate] =
    for {
      config     <- resolveControllerConfigs
      authParams <- authParams(config)
      predicates <- authPredicate(authParams)
    } yield predicates

}

object AuthPredicateBuilder {

  type AuthResult[A] = Either[AuthError, A]

  sealed trait AuthError {
    val msg: String
  }

  final case class NoConfidenceLevel(controllerName: String) extends AuthError {
    val msg: String =
      s"No Confidence level defined for controller: $controllerName or globally, which needs authentication"
  }

  final case class NoEnrolmentDefined(controllerName: String) extends AuthError {
    val msg: String = s"No enrolment defined for controller: $controllerName, which needs authentication"
  }

  final case class AuthParamsNotDefined(controllerName: String) extends AuthError {
    val msg: String = s"No authParams defined for controller: $controllerName, which needs authentication"
  }

  final case class NoControllersConfigDefined(msg: String = "") extends AuthError

}
