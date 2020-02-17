package com.eztier
package datasource

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

package object config {
  implicit val cassandraConnectionEnc: Encoder[CassandraConnectionConfig] = deriveEncoder
  implicit val cassandraEnc: Encoder[CassandraConfig] = deriveEncoder
}
