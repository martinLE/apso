package eu.shiftforward.apso.json

import spray.json.DefaultJsonProtocol._
import spray.json._
import eu.shiftforward.apso.Implicits._

import scala.util.Try

/**
 * Object containing implicit classes and methods related to JSON handling.
 */
object Implicits {

  object ToInt {
    def unapply(str: String) = Try(str.toInt).toOption
  }

  /**
   * Implicit class that provides new methods for `JsValues`.
   * @param json the `JsValue` to which the new methods are provided.
   */
  final implicit class ApsoJsonJsValue(val json: JsValue) extends AnyVal {

    /**
     * Unwraps a JSON value. If the given value is a JSON string, number or
     * boolean, a `String`, `BigDecimal` or `Boolean` is returned
     * respectively. If the given value is a JSON array or object, a `List[Any]`
     * or `Map[String, Any]` is returned respectively, where each of the values
     * is recursively unwrapped. If the given value is a JSON null, `null` is
     * returned.
     * @return the unwrapped JSON value.
     */
    def toValue: Any = json match {
      case JsString(str) => str
      case JsNumber(num) => num
      case JsObject(map) => map.mapValues(_.toValue).map(identity)
      case JsArray(elems) => elems.map(_.toValue)
      case JsBoolean(bool) => bool
      case JsNull => null
    }

    /**
     * Merges two JsValues if they are JsArrays or JsObjects. If they are JsObjects, a deep merge is performed.
     *
     * When merging JsObjects, if `failOnConflict` is false and conflicts exists between terminal values, these are
     * resolved by using the values of the second JSON. If `failOnConflict` is true, an `IllegalArgumentException` is
     * thrown.
     *
     * @param other the other JSON value to merge
     * @param failOnConflict whether to fail or resolve conflicts by using the values on the `other` JSON.
     * @return the resulting merged JsObject
     */
    def merge(other: JsValue, failOnConflict: Boolean = true): JsValue = (json, other) match {
      case (JsObject(fields), JsObject(otherFields)) =>
        fields.twoWayMerge(otherFields)((js1, js2) => js1.merge(js2, failOnConflict)).toJson
      case (JsArray(arr), JsArray(otherArr)) => (arr ++ otherArr).toJson
      case (_, anyVal) if !failOnConflict => anyVal
      case _ => throw new IllegalArgumentException("Invalid types for merging")
    }
  }

  /**
   * Implicit class that provides new methods for `JsObjects`.
   * @param json the `JsObjects` to which the new methods are provided.
   */
  final implicit class ApsoJsonJsObject(val json: JsObject) extends AnyVal {
    /**
     * Returns a set of keys of this object where nested keys are separated by a separator character.
     *
     * Eg. {"a":1,"b":{"c":2},"d":null}.flattenedKeySet(".", ignoreNull = true) = Set("a","b.c")
     *
     * @param separator character separator to use
     * @param ignoreNull if set, fields with a null value are ignored
     * @return flattened key set
     */
    def flattenedKeySet(separator: String = ".", ignoreNull: Boolean = true): Set[String] = {
      val fields = json.fields.toSet
      fields.flatMap {
        case (k, v: JsObject) => v.flattenedKeySet(separator, ignoreNull).map(k + separator + _)
        case (k, JsNull) if ignoreNull => Set.empty[String]
        case (k, v) => Set(k)
      }
    }
  }

  /**
   * Creates a JsObject from a sequence of pairs of dot-separated (or other separator) paths with the corresponding
   * leaf values (eg. `List(("root.leaf1", JsString("leafVal1")), ("root.leaf2", JsString("leafVal2")))`
   * @param paths the sequence of dot-separated (or other separator) paths
   * @param separatorRegex regex to use to separate fields
   * @return the resulting JsObject
   */
  def fromFullPaths(paths: Seq[(String, JsValue)], separatorRegex: String = "\\."): JsValue = {
    def createJsValue(keys: Seq[String], value: JsValue): JsValue = {
      keys match {
        case Nil => value
        case h :: t => JsObject(h -> createJsValue(t, value))
      }
    }

    paths match {
      case Nil => JsObject()
      case (path, value) :: rem =>
        createJsValue(path.split(separatorRegex).toList, value).merge(fromFullPaths(rem, separatorRegex))
    }
  }
}