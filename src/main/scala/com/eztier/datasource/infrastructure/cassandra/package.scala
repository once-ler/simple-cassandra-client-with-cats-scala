package com.eztier.datasource.infrastructure

import cats.effect.{Async, Blocker, Concurrent, ConcurrentEffect, ContextShift, IO, Resource, Sync, Timer}
import io.circe.config.{parser => ConfigParser}

package object cassandra {

  import com.eztier.datasource.config._

  def createSimpleCassandraClientResource[F[_] : Async : ContextShift : ConcurrentEffect : Timer] =
    for {
      conf <- Resource.liftF(ConfigParser.decodePathF[F, AppConfig]("datasource"))
      cnConf = conf.cassandra.connection
      cs = CassandraSession[F](cnConf.host, cnConf.port, cnConf.user, cnConf.password).getSessionSync
      cl = CassandraClient(cs)
    } yield cl

}
