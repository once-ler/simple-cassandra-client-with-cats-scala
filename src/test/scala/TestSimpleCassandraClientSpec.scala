package com.eztier.datasource
package infrastructure.cassandra
package test

import java.time.Instant
import java.util.{Date, UUID}

import cats.data._
import cats.implicits._
import cats.effect.{IO, Sync}
import com.datastax.driver.core.utils.UUIDs
import fs2.{Chunk, Pipe, Stream}
import com.datastax.driver.core.{ResultSet, Row, Session, SimpleStatement, Statement}
import org.specs2.mutable.Specification
import com.eztier.datasource.infrastructure.cassandra.{CassandraClient, CassandraSession}

case class CaResourceModified
(
  Environment: String,
  Store: String,
  Type: String,
  StartTime: Date,
  Id: String,
  Oid: String,
  Uid: UUID,
  Current: String
)

case class CaResourceProcessed
(
  Environment: String,
  Store: String,
  Type: String,
  Purpose: String,
  StartTime: Date,
  Id: String,
  Oid: String,
  Uid: UUID,
  Current: String
)

class TestSimpleCassandraClientSpec extends Specification {
  val ec = scala.concurrent.ExecutionContext.global
  implicit val timer = IO.timer(ec)
  implicit val cs = IO.contextShift(ec)

  "Simple Cassandra Client" should {

    "Create a table" in {

      val res = createSimpleCassandraClientResource[IO].use {
        case db =>

          val u = db.createAsync[CaResourceModified]("dwh")("Environment", "Store", "Type")("Uid", "StartTime")(("Uid" -> None), ("StartTime" -> None))

          val r = u.unsafeRunSync()

          IO.pure(r)
      }.unsafeRunSync()

      res.isInstanceOf[ResultSet]
    }

    "Create another table" in {

      val res = createSimpleCassandraClientResource[IO].use {
        case db =>

          val u = db.createAsync[CaResourceProcessed]("dwh")("Environment", "Store", "Type", "Purpose")("Uid", "StartTime")(("Uid" -> -1.some), ("StartTime" -> -1.some))

          val r = u.unsafeRunSync()

          IO.pure(r)
      }.unsafeRunSync()

      res.isInstanceOf[ResultSet]
    }

    "Insert a row" in {
      val res = createSimpleCassandraClientResource[IO].use {
        case db =>

          val a = CaResourceModified(
            Environment = "development",
            Store = "IKEA",
            Type = "Sales",
            StartTime = Date.from(Instant.now),
            Id = "ABC123",
            Oid = "5dd81233ae6d16d797be3915e52ac94b",
            Uid = UUIDs.timeBased(),
            Current = <root><inserted><row>
              <oid>5dd81233ae6d16d797be3915e52ac94b</oid>
              <type>Sales</type>
              <ID>ABC123</ID>
              <dateModified>1595638722248</dateModified>
              </row></inserted></root>.toString()
          )

          val u = db.batchInsertAsync(Chunk.vector(Vector(a)), "dwh", "ca_resource_modified")

          val r = u.unsafeRunSync()

          IO.pure(r)
      }.unsafeRunSync()

      res.isInstanceOf[ResultSet]
    }

    "Read a table" in {
      val res = createSimpleCassandraClientResource[IO].use {
        case db =>
          val u = db.readAsync("select * from dwh.ca_resource_processed limit 1")
            .flatMap { a =>
              val uid = a.getUUID("uid")

              Stream.emit(uid).covary[IO]
            }

          val r = u.compile.toList.unsafeRunSync()

          IO.pure(r)
      }.unsafeRunSync()

      res.isInstanceOf[List[UUID]]
    }

  }

}
