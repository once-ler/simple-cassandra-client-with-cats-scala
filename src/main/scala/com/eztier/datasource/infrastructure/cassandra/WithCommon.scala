package com.eztier.datasource
package infrastructure.cassandra

trait WithCommon {
  def camelToUnderscores(name: String) = "[A-Z\\d]".r.replaceAllIn(name.charAt(0).toLower.toString + name.substring(1), {m =>
    "_" + m.group(0).toLowerCase()
  }).replaceAll("__", "_")

  def underscoreToCamel(name: String) = "_([a-z\\d])".r.replaceAllIn(name, {m =>
    m.group(1).toUpperCase()
  })

  def camelToSpaces(name: String) = "[A-Z\\d]".r.replaceAllIn(name, (m => " " + m.group(0)))
}
