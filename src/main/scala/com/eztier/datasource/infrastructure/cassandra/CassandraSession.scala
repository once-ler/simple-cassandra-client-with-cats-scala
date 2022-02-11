package com.eztier.datasource
package infrastructure.cassandra

import cats.effect.{Async, Sync}
import java.net.InetSocketAddress
import scala.collection.JavaConverters._
import com.datastax.driver.core.{Cluster, Session, CloseFuture}
import com.datastax.driver.core.policies.ConstantReconnectionPolicy
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class CassandraSession[F[_]: Async : Sync](endpoints: String, port: Int, user: Option[String] = None, pass: Option[String] = None)
  extends WithBlockingThreadPool {

  private val contactPoints = endpoints.split(',').map {
    a =>
      if (a.contains(":")) {
        val addr = a.split(":")
        new InetSocketAddress(addr(0), addr(1).toInt).getAddress
      } else new InetSocketAddress(a, port).getAddress
  }.toSeq.asJavaCollection

  private val cluster = {
    val clusterBuilder = Cluster.builder
      .addContactPoints(contactPoints)
      .withPort(port)
      .withReconnectionPolicy(new ConstantReconnectionPolicy(5000))

    user match {
      case Some(u) if pass.isDefined =>
        clusterBuilder.withCredentials(u, pass.get)
        ()
      case _ => ()
    }

    clusterBuilder.build()
  }

  def getSession: F[Session] =
    Async[F].async {
      (cb: Either[Throwable, Session] => Unit) =>

        val f: Future[Session] = cluster.connectAsync()

        f.onComplete {
          case Success(s) => cb(Right(s))
          case Failure(e) => cb(Left(e))
        }
    }

  def getSessionSync = cluster.connect()

  def closeSessionSync() = cluster.close()

}

object CassandraSession {
  def apply[F[_]: Async : Sync](endpoints: String, port: Int, user: Option[String] = None, pass: Option[String] = None) =
    new CassandraSession[F](endpoints, port, user, pass)
}
