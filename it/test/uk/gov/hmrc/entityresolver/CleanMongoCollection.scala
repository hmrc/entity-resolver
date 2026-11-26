/*
 * Copyright 2022 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver

import org.mongodb.scala.model.Filters
import org.mongodb.scala.{ Document, MongoCollection }
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import uk.gov.hmrc.mongo.test.MongoSupport
import org.mongodb.scala.SingleObservableFuture
import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CleanMongoCollection @Inject() () extends MongoSupport {

  lazy val collections: Seq[MongoCollection[Document]] = Seq(
    mongoComponent.database.getCollection("entity"),
    mongoComponent.database.getCollection("auth"),
    mongoComponent.database.getCollection("user-details"),
    mongoComponent.database.getCollection("preferences")
  )

  def cleanCollections(): Unit =
    await(Future.traverse(collections)(_.deleteMany(Filters.empty()).toFuture()))
}
