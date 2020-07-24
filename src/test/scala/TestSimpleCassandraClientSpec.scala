package com.eztier.datasource
package infrastructure.cassandra
package test

import java.util.{Date, UUID}

import cats.data._
import cats.implicits._
import cats.effect.{IO, Sync}
import fs2.{Pipe, Stream}
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

class TestSimpleCassandraClientSpec extends Specification {
  val ec = scala.concurrent.ExecutionContext.global
  implicit val timer = IO.timer(ec)
  implicit val cs = IO.contextShift(ec)

  "Simple Cassandra Client" should {

    "Create usable client" in {

      val cqlEndpoints = "127.0.0.1"
      val cqlPort: Int = 9042
      val user = "cassandra"
      val pass = "cassandra"


      val res = createSimpleCassandraClientResource[IO].use {
        case db =>

          val u = db.createAsync[CaResourceModified]("dwh", "ca_resource_modified".some)("Environment", "Store", "Type")("StartTime", "Id")(("StartTime" -> None), ("Id" -> None))

          val r = u.unsafeRunSync()

          IO.pure(r)
      }.unsafeRunSync()

      res.isInstanceOf[ResultSet]
    }

  }

}
