/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
