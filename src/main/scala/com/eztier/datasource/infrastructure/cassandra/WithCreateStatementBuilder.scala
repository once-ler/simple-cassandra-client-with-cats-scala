package com.eztier.datasource
package infrastructure.cassandra

import java.util.{Date, UUID}

import com.datastax.driver.core.SimpleStatement
import com.datastax.driver.core.utils.UUIDs

import scala.Option
import scala.reflect.runtime.universe._

trait WithCreateStatementBuilder {

  def camelToUnderscores(name: String) = "[A-Z\\d]".r.replaceAllIn(name.charAt(0).toLower.toString + name.substring(1), {m =>
    "_" + m.group(0).toLowerCase()
  })

  def underscoreToCamel(name: String) = "_([a-z\\d])".r.replaceAllIn(name, {m =>
    m.group(1).toUpperCase()
  })

  def camelToSpaces(name: String) = "[A-Z\\d]".r.replaceAllIn(name, (m => " " + m.group(0)))

  private def classAccessors[T: TypeTag]: List[MethodSymbol] = typeOf[T].members.collect {
    case m: MethodSymbol if m.isCaseAccessor => m
  }.toList

  private def tryMatchType(o: Type) = {
    o match {
      case a if a =:= typeOf[String] => "text"
      case a if a =:= typeOf[Date] => "timestamp"
      case a if a =:= typeOf[java.math.BigDecimal] => "decimal"
      case a if a =:= typeOf[Float] => "float"
      case a if a =:= typeOf[Double] => "double"
      case a if a =:= typeOf[Long] => "bigint"
      case a if a =:= typeOf[Int] => "int"
      case a if a =:= typeOf[Short] => "smallint"
      case a if a =:= typeOf[Byte] => "tinyint"
      case a if a =:= typeOf[Boolean] => "boolean"
      case a if a =:= typeOf[UUID] => "timeuuid"
      case a if (a =:= typeOf[Seq[String]] || a =:= typeOf[List[String]] || a =:= typeOf[Vector[String]]) => "list<text>"
      case a if a =:= typeOf[Seq[Date]] => "list<timestamp>"
      case a if a =:= typeOf[Seq[BigDecimal]] => "list<decimal>"
      case a if a =:= typeOf[Seq[Double]] => "list<double>"
      case a if a =:= typeOf[Seq[Float]] => "list<float>"
      case a if a =:= typeOf[Seq[Long]] => "list<bigint>"
      case a if a =:= typeOf[Seq[Int]] => "list<int>"
      case a if a =:= typeOf[Seq[Short]] => "list<smallint>"
      case a if a =:= typeOf[Seq[Byte]] => "list<tinyint>"
      case a if a =:= typeOf[Seq[Boolean]] => "list<boolean>"
      case a if a =:= typeOf[Seq[UUID]] => "list<timeuuid>"
      case a if a =:= typeOf[Map[String, String]] => "map<text,text>"
      case _ => ""
    }
  }

  private def toCaType[T](implicit typeTag: TypeTag[T]) = {
    val members = classAccessors[T]

    val m = (Map[String, String]() /: members) {
      (a, f) =>
        val o: Type = f.info.resultType
        val n = f.name.toString
        val v = o.typeArgs

        val s: String =
          if (o <:< typeOf[Option[Any]])
            tryMatchType(v(0))
          else
            tryMatchType(o)

        a + (camelToUnderscores(n) -> s)
    }

    m
  }

  private def getCreateStmtString[T](keySpace: String, tableName: String)(implicit typeTag: TypeTag[T]): Seq[String] = {
    /*
    val o = typeTag.tpe.resultType

    val t = tableName match {
      case Some(a) => a
      case _ => o.typeSymbol.name.toString
    }
    */

    val m = toCaType[T]

    val f = (Array[String]() /: m) {
      (a, n) =>
        a :+ (n._1 + " " + n._2)
    }

    Seq(s"create table if not exists ${keySpace}.${camelToUnderscores(tableName)} (${f.mkString(",")});")
  }

  def getCreateStmt[T: TypeTag](keySpace: String)(partitionKeys: String*)(clusteringKeys: String*)(orderBy: (String, Option[Int])*): SimpleStatement = {
    val o = typeTag.tpe.resultType
    val n = o.typeSymbol.name.toString
    val tableName = camelToUnderscores(n)

    val t: Seq[String] = getCreateStmtString[T](keySpace, tableName)

    val members = classAccessors[T]
    val f = members.map(_.name.toString)

    val pk = partitionKeys.collect {
      case a if f.find(_ == a) != None => camelToUnderscores(a)
    }.mkString(",")

    val ck = clusteringKeys.collect {
      case a if f.find(_ == a) != None => camelToUnderscores(a)
    }.mkString(",")

    val ob = orderBy.collect {
      case a if f.find(_ == a._1) != None => a
    }.map { case (a, direction) =>
      val sort = direction match {
        case Some(b) => if (b > 0) "asc" else "desc"
        case None => "asc"
      }

      s"${camelToUnderscores(a)} ${sort}"
    }.mkString(",")

    val sb = s" with clustering order by (${ob})"

    /*
    val sb = orderBy match {
      case Some(a) if a.length > 0 && f.find(_ == a) != None =>
        val sort = direction match {
          case Some(b) => if (b > 0) "asc" else "desc"
          case None => "asc"
        }

        s" with clustering order by (${a} ${sort})"
      case None => ""
    }
    */

    val t0 = t(0)
    val trim = t0.substring(0, t0.length - 2)

    val stmt = trim +
      s", primary key ((${pk})" +
      (if (ck.length > 0) s", ${ck}))" else "))") +
      sb + ";"

    println(stmt)

    new SimpleStatement(stmt)

  }

}
