/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.repository

import com.mongodb.client.model.Filters
import org.bson.conversions.Bson
import org.mongodb.scala.ReadPreference.secondaryPreferred
import org.mongodb.scala.model.*
import org.mongodb.scala.result.DeleteResult
import org.mongodb.scala.{ FindObservable, ObservableFuture, SingleObservableFuture }
import org.reactivestreams.Publisher
import play.api.Logger
import uk.gov.hmrc.domain.TaxIds.TaxIdWithName
import uk.gov.hmrc.domain.{ SaUtr, SerialisableTaxId }
import uk.gov.hmrc.entityresolver.models.{ Entity, EntityId }
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{ Inject, Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
@Singleton
class EntityRepository @Inject() (
  @Named("soleTaxIds") supportedTaxIds: Set[SerialisableTaxId],
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[Entity](
      mongoComponent,
      "entity",
      Entity.formats,
      Seq(
        IndexModel(Indexes.ascending("sautr"), IndexOptions().name("sautrUnique").unique(true).sparse(true)),
        IndexModel(Indexes.ascending("nino"), IndexOptions().name("ninoUnique").unique(true).sparse(true)),
        IndexModel(
          Indexes.ascending("HMRC-MTD-IT"),
          IndexOptions().name("HmrcMtdItUnique").sparse(true).background(true)
        ),
        IndexModel(Indexes.ascending("nino", "sautr"), IndexOptions().unique(true).sparse(false).background(true)),
        IndexModel(Indexes.ascending("sautr", "nino"), IndexOptions().unique(true).sparse(false).background(true)),
        IndexModel(
          Indexes.ascending("ctutr"),
          IndexOptions().name("ctutrIndex").unique(false).sparse(true).background(true)
        ),
        IndexModel(
          Indexes.ascending("atedutr"),
          IndexOptions().name("atedutrIndex").unique(false).sparse(true).background(true)
        ),
        IndexModel(
          Indexes.ascending("awrsutr"),
          IndexOptions().name("awrsutrIndex").unique(false).sparse(true).background(true)
        ),
        IndexModel(
          Indexes.ascending("vrn"),
          IndexOptions().name("vrnIndex").unique(false).sparse(true).background(true)
        ),
        IndexModel(
          Indexes.ascending("uar"),
          IndexOptions().name("uarIndex").unique(false).sparse(true).background(true)
        ),
        IndexModel(
          Indexes.ascending("org"),
          IndexOptions().name("orgIndex").unique(false).sparse(true).background(true)
        ),
        IndexModel(
          Indexes.ascending("agentbusinessutr"),
          IndexOptions().name("agentbusinessutrIndex").unique(false).sparse(true).background(true)
        ),
        IndexModel(
          Indexes.ascending("psaid"),
          IndexOptions().name("psaidIndex").unique(false).sparse(true).background(true)
        ),
        IndexModel(
          Indexes.ascending("HMRC-OBTDS-ORG"),
          IndexOptions().name("hmrcobtdsorgIndex").unique(false).sparse(true).background(true)
        ),
        IndexModel(
          Indexes.ascending("HMRC-MTD-VAT"),
          IndexOptions().name("hmrcmtdvatIndex").unique(false).sparse(true).background(true)
        )
      )
    ) {

  def countAll(): Future[Long] =
    collection.withReadPreference(secondaryPreferred()).countDocuments().toFuture()

  def countITSAIDs(): Future[Long] =
    collection.withReadPreference(secondaryPreferred()).countDocuments(Filters.exists("HMRC-MTD-IT")).toFuture()

  def findITSAIDs(batchSize: Int): Publisher[Entity] =
    collection.withReadPreference(secondaryPreferred()).find(Filters.exists("HMRC-MTD-IT")).batchSize(batchSize)

  def countBySoleTaxId(taxId: SerialisableTaxId)(implicit ec: ExecutionContext): Future[Int] = {
    val excludedTaxIds = supportedTaxIds.filterNot(_ == taxId).map(taxId => Filters.exists(taxId.taxIdName, false))
    val existsTaxIdFilter = excludedTaxIds.toSeq.+:(Filters.exists(taxId.taxIdName))
    collection
      .withReadPreference(secondaryPreferred())
      .countDocuments(Filters.and(existsTaxIdFilter: _*))
      .toFuture()
      .map(_.toInt)
  }

  def countByTaxId(taxId: SerialisableTaxId)(implicit ec: ExecutionContext): Future[Int] =
    collection
      .withReadPreference(secondaryPreferred())
      .countDocuments(Filters.exists(taxId.taxIdName))
      .toFuture()
      .map(_.toInt)

  import uk.gov.hmrc.entityresolver.models.Entity

  private val logger = Logger(getClass.getName)

  def updateOrCreateEntity(entity: Entity): Future[Entity] = {
    val filters: Seq[Bson] = entity.taxIdentifiers.map(taxId => Filters.eq(taxId.name, taxId.value)).toSeq

    collection
      .find(Filters.or(filters: _*))
      .first()
      .toFuture()
      .flatMap(
        Option(_) match {
          case Some(ent) =>
            Future(ent)
          case None =>
            collection.insertOne(entity).toFuture().map(_ => entity)
        }
      )
  }

  def confirm(entity: Entity)(implicit ec: ExecutionContext): Future[Entity] =
    updateOrCreateEntity(entity).recoverWith {
      case e if e.getMessage.contains("11000") =>
        findByAny(entity.taxIdentifiers) map {
          case x :: Nil => x
          case other =>
            throw new RuntimeException(
              s"Could not create new entity due to duplicate key but found ${other.size} existing entities when trying to recover"
            )
        }
    }

  def remove(entityId: EntityId)(implicit ec: ExecutionContext): Future[Boolean] =
    collection.deleteMany(Filters.eq("_id", entityId.value)).toFuture().map(_.getDeletedCount > 0)

  def unset(entityId: EntityId, unsetIdName: String)(implicit ec: ExecutionContext): Future[Boolean] =
    collection
      .updateOne(Filters.eq("_id", entityId.value), Updates.unset(unsetIdName))
      .toFuture()
      .map(_.getModifiedCount > 0)

  def removeAll(): Future[DeleteResult] =
    collection.deleteMany(Filters.empty()).toFuture()

  def findBy(entityId: EntityId)(implicit ec: ExecutionContext): Future[Option[Entity]] =
    collection.find(Filters.eq("_id", entityId.value)).first().toFuture().map(Option(_))

  def findBy(taxId: TaxIdWithName)(implicit ec: ExecutionContext): Future[Option[Entity]] =
    collection.find(Filters.eq(taxId.name, taxId.value)).toFuture().map {
      case Nil           => None
      case entity :: Nil => Some(entity)
      case entities =>
        throw new IllegalStateException(s"${entities.size} entity objects found for ${taxId.value} ${taxId.name}")
    }

  def findByAny(taxIds: Set[TaxIdWithName]): Future[Seq[Entity]] = {
    val query = taxIds.map(taxId => Filters.eq(taxId.name, taxId.value)).toSeq
    collection.find(Filters.or(query: _*)).toFuture()
  }

  def updateTaxIds(entityId: EntityId, taxIds: Set[TaxIdWithName], overrideIds: Boolean = false)(implicit
    ec: ExecutionContext
  ): Future[Boolean] = {

    val keepExistingIds: Seq[Bson] = taxIds.map(id => Updates.set(id.name, id.value)).toSeq
    val replaceIds: Set[TaxIdWithName] => Seq[Bson] = _.map(id => Updates.unset(id.name)).toSeq

    taxIds.toList match {
      case Nil => Future.successful(false)
      case _ =>
        collection
          .find(
            Filters.eq("_id", entityId.value)
          )
          .first()
          .toFuture()
          .flatMap(Option(_) match {
            case Some(ent) if overrideIds =>
              collection
                .updateOne(
                  Filters.eq("_id", ent.id.value),
                  Updates.combine(replaceIds(ent.taxIdentifiers.diff(taxIds)) ++ keepExistingIds: _*),
                  UpdateOptions().upsert(false)
                )
                .toFuture()
                .map(_.getModifiedCount > 0)
            case _ =>
              collection
                .updateOne(Filters.eq("_id", entityId.value), keepExistingIds, UpdateOptions().upsert(false))
                .toFuture()
                .map(_.getModifiedCount > 0)
          })
          .recoverWith { case ex =>
            logger.error(s"cannot update tax identifiers for entityId: $entityId due to: ${ex.getMessage}")
            Future.successful(false)
          }
    }
  }
}
