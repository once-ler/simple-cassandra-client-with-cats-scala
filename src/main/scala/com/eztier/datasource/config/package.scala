package com.eztier
package datasource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

package object config {
  implicit val appEnc: Encoder[AppConfig] = deriveEncoder
  implicit val appDec: Decoder[AppConfig] = deriveDecoder
  implicit val cassandraConnectionEnc: Encoder[CassandraConnectionConfig] = deriveEncoder
  implicit val cassandraConnectionDec: Decoder[CassandraConnectionConfig] = deriveDecoder
  implicit val cassandraEnc: Encoder[CassandraConfig] = deriveEncoder
  implicit val cassandraDec: Decoder[CassandraConfig] = deriveDecoder
}
