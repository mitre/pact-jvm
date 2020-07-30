package au.com.dius.pact.core.matchers

import au.com.dius.pact.core.matchers.util.IndicesCombination
import au.com.dius.pact.core.matchers.util.LargestKeyValue
import au.com.dius.pact.core.matchers.util.memoizeFixed
import au.com.dius.pact.core.matchers.util.padTo
import au.com.dius.pact.core.model.OptionalBody
import au.com.dius.pact.core.model.matchingrules.MatchingRules
import au.com.dius.pact.core.support.json.JsonParser
import au.com.dius.pact.core.support.json.JsonValue
import au.com.dius.pact.core.support.jsonArray
import mu.KLogging
import java.math.BigInteger

object JsonBodyMatcher : BodyMatcher, KLogging() {

  override fun matchBody(
    expected: OptionalBody,
    actual: OptionalBody,
    allowUnexpectedKeys: Boolean,
    matchingRules: MatchingRules
  ): BodyMatchResult {
    return when {
      expected.isMissing() -> BodyMatchResult(null, emptyList())
      expected.isEmpty() && actual.isEmpty() -> BodyMatchResult(null, emptyList())
      !expected.isEmpty() && actual.isEmpty() ->
        BodyMatchResult(null, listOf(BodyItemMatchResult("$",
          listOf(BodyMismatch(null, actual.valueAsString(), "Expected empty body but received '${actual.value}'")))))
      expected.isNull() && actual.isPresent() ->
        BodyMatchResult(null, listOf(BodyItemMatchResult("$",
          listOf(BodyMismatch(null, actual.valueAsString(), "Expected null body but received '${actual.value}'")))))
      expected.isNull() -> BodyMatchResult(null, emptyList())
      actual.isMissing() ->
        BodyMatchResult(null, listOf(BodyItemMatchResult("$",
          listOf(BodyMismatch(expected.valueAsString(), null, "Expected body '${expected.value}' but was missing")))))
      else -> {
        BodyMatchResult(null, compare(listOf("$"), JsonParser.parseString(expected.valueAsString()),
          JsonParser.parseString(actual.valueAsString()), allowUnexpectedKeys, matchingRules))
      }
    }
  }

  private fun valueOf(value: Any?) = when (value) {
    is String -> "'$value'"
    is JsonValue.StringValue -> "'${value.asString()}'"
    is JsonValue -> value.serialise()
    null -> "null"
    else -> value.toString()
  }

  private fun typeOf(value: Any?) = when {
    value is Map<*, *> -> "Map"
    value is JsonValue.Object -> "Map"
    value is List<*> -> "List"
    value is JsonValue.Array -> "List"
    value is JsonValue.Null -> "Null"
    value is JsonValue -> value.name
    value == null -> "Null"
    else -> value.javaClass.simpleName
  }

  private fun compare(
    path: List<String>,
    expected: JsonValue,
    actual: JsonValue,
    allowUnexpectedKeys: Boolean,
    matchers: MatchingRules
  ): List<BodyItemMatchResult> {
    return when {
      expected is JsonValue.Object && actual is JsonValue.Object ->
        compareMaps(expected, actual, expected, actual, path, allowUnexpectedKeys, matchers)
      expected is JsonValue.Array && actual is JsonValue.Array ->
        compareLists(expected, actual, expected, actual, path, allowUnexpectedKeys, matchers)
      expected is JsonValue.Object && actual !is JsonValue.Object ||
        expected is JsonValue.Array && actual !is JsonValue.Array ->
        listOf(BodyItemMatchResult(path.joinToString("."),
          listOf(BodyMismatch(expected, actual, "Type mismatch: Expected ${typeOf(expected)} " +
          "${valueOf(expected)} but received ${typeOf(actual)} ${valueOf(actual)}", path.joinToString("."),
          generateJsonDiff(expected, actual)))))
      else -> compareValues(path, expected, actual, matchers)
    }
  }

  private fun compareListContent(
    expectedValues: List<JsonValue>,
    actualValues: List<JsonValue>,
    path: List<String>,
    allowUnexpectedKeys: Boolean,
    matchers: MatchingRules
  ): List<BodyItemMatchResult> {
    val result = mutableListOf<BodyItemMatchResult>()
    for ((index, value) in expectedValues.withIndex()) {
      if (index < actualValues.size) {
        result.addAll(compare(path + index.toString(), value, actualValues[index], allowUnexpectedKeys, matchers))
      } else if (!Matchers.matcherDefined("body", path, matchers)) {
        result.add(BodyItemMatchResult(path.joinToString("."),
          listOf(BodyMismatch(expectedValues, actualValues, "Expected ${valueOf(value)} but was missing",
          path.joinToString("."), generateJsonDiff(jsonArray(expectedValues), jsonArray(actualValues))))))
      }
    }
    return result
  }

  /**
   * Compares any "extra" actual elements to expected using wildcard
   * matching.
   */
  private fun wildcardCompare(
    basePath: List<String>,
    expectedValues: List<JsonValue>,
    actual: JsonValue,
    allowUnexpectedKeys: Boolean,
    matchers: MatchingRules
  ): List<BodyItemMatchResult> {
    val path = basePath + expectedValues.size.toString()
    val pathStr = path.joinToString(".")
    val starPathStr = (basePath + "*").joinToString(".")

    return compare(path, expectedValues[0], actual, allowUnexpectedKeys, matchers)
      .map { matchResult ->
        // replaces index with '*' for clearer errors
        matchResult.copy(result = matchResult.result.map { mismatch ->
          mismatch.copy(path = mismatch.path.replaceFirst(pathStr, starPathStr))
        })
      }
  }

  /**
   * Compares every permutation of actual against expected.
   *
   * If actual has more elements than expected, and there is a wildcard matcher,
   * then each extra actual element is compared to the first expected element
   * using the wildcard matcher.
   */
  private fun compareListContentUnordered(
    expectedValues: List<JsonValue>,
    actualValues: List<JsonValue>,
    path: List<String>,
    allowUnexpectedKeys: Boolean,
    matchers: MatchingRules
  ): List<BodyItemMatchResult> {
    val result = mutableListOf<BodyItemMatchResult>()
    val doWildCardMatching = actualValues.size > expectedValues.size &&
      Matchers.wildcardIndexMatcherDefined(path + expectedValues.size.toString(),
        "body", matchers)

    val memoizedWildcardCompare = { actualIndex: Int ->
      wildcardCompare(path, expectedValues, actualValues[actualIndex],
        allowUnexpectedKeys, matchers)
    }.memoizeFixed(actualValues.size)

    val memoizedCompare = { expectedIndex: Int, actualIndex: Int ->
      compare(path + expectedIndex.toString(),
        expectedValues[expectedIndex], actualValues[actualIndex],
        allowUnexpectedKeys, matchers)
    }.memoizeFixed(expectedValues.size, actualValues.size)

    val bestMatch = LargestKeyValue<Int, IndicesCombination>()
    val examinedActualIndicesCombos = HashSet<BigInteger>()
    /**
     * Determines if a matching permutation exists.
     *
     * Has the side-effect of populating compareResults and wildcardCompareResults via the
     * memoizedCompare calls.
     *
     * Note: this algorithm has a worst case O(n*2^n) time and O(2^n) space complexity
     * if a lot of the actuals match a lot of the expected (e.g., if expected is [3|2|1, 2|1, 1]).
     * Without the caching, the time complexity jumps to around O(2^2n). There are also up to O(n^2)
     * calls to compare (which may be expensive for complex elements). This poor performance is due
     * to the huge n! search space of all possible permutations. For most normal cases, average
     * performance should be closer to O(n^2) time and O(n) space if there aren't many duplicate
     * matches. Best case is O(n)/O(n) if its already in order.
     *
     * @param expectedIndex index of expected being compared against
     * @param actualIndices combination of remaining actual indices to compare
     */
    fun hasMatchingPermutation(
      expectedIndex: Int = 0,
      actualIndices: IndicesCombination = IndicesCombination.of(actualValues)
    ): Boolean {
      return if (actualIndices.comboId in examinedActualIndicesCombos) {
        false
      } else {
        examinedActualIndicesCombos.add(actualIndices.comboId)
        bestMatch.useIfLarger(expectedIndex, actualIndices)
        if (expectedIndex < expectedValues.size) {
          actualIndices.indices().any { actualIndex ->
            memoizedCompare(expectedIndex, actualIndex).all {
              it.result.isEmpty()
            } && hasMatchingPermutation(expectedIndex + 1, actualIndices - actualIndex)
          }
        } else if (doWildCardMatching) {
          actualIndices.indices().all { actualIndex ->
            memoizedWildcardCompare(actualIndex).all {
              it.result.isEmpty()
            }
          }
        } else true
      }
    }

    return if (hasMatchingPermutation()) {
      emptyList()
    } else {
      val smallestCombo = bestMatch.value ?: IndicesCombination.of(actualValues)
      val longestMatch = bestMatch.key ?: 0

      val remainingErrors = smallestCombo.indices().map { actualIndex ->
        (longestMatch until expectedValues.size).map { expectedIndex ->
          memoizedCompare(expectedIndex, actualIndex)
        }.flatten() + if (doWildCardMatching) {
          memoizedWildcardCompare(actualIndex)
        } else emptyList()
      }.toList().flatten()

      result.add(BodyItemMatchResult(path.joinToString("."),
        listOf(BodyMismatch(expectedValues, actualValues,
          "Expected ${valueOf(expectedValues)} to match ${valueOf(actualValues)} " +
          "ignoring order of elements",
          path.joinToString("."),
          generateJsonDiff(jsonArray(expectedValues), jsonArray(actualValues))))))
      result.addAll(remainingErrors)
      result
    }
  }

  private fun compareLists(
    expectedValues: JsonValue.Array,
    actualValues: JsonValue.Array,
    a: JsonValue,
    b: JsonValue,
    path: List<String>,
    allowUnexpectedKeys: Boolean,
    matchers: MatchingRules
  ): List<BodyItemMatchResult> {
    val expectedList = expectedValues.values
    val actualList = actualValues.values
    return if (Matchers.isEqualsIgnoreOrderMatcherDefined(path, "body", matchers)) {
      // match unordered list
      logger.debug { "compareLists: ignore-order matcher defined for path $path" }
      val result = mutableListOf(BodyItemMatchResult(path.joinToString("."),
        Matchers.domatch(matchers, "body", path, expectedValues, actualValues, BodyMismatchFactory)))
      if (expectedList.isNotEmpty()) {
        // No need to pad 'expected' as we already visit all 'actual' values
        result.addAll(compareListContentUnordered(expectedList, actualList, path, allowUnexpectedKeys, matchers))
      }
      result
    } else if (Matchers.matcherDefined("body", path, matchers)) {
      logger.debug { "compareLists: Matcher defined for path $path" }
      val result = mutableListOf(BodyItemMatchResult(path.joinToString("."),
        Matchers.domatch(matchers, "body", path, expectedValues, actualValues, BodyMismatchFactory)))
      if (expectedList.isNotEmpty()) {
        result.addAll(compareListContent(expectedList.padTo(actualList.size, expectedList.first()),
          actualList, path, allowUnexpectedKeys, matchers))
      }
      result
    } else {
      if (expectedList.isEmpty() && actualList.isNotEmpty()) {
        listOf(BodyItemMatchResult(path.joinToString("."),
          listOf(BodyMismatch(a, b, "Expected an empty List but received ${valueOf(actualValues)}",
          path.joinToString("."), generateJsonDiff(expectedValues, actualValues)))))
      } else {
        val result = compareListContent(expectedList, actualList, path, allowUnexpectedKeys, matchers).toMutableList()
        if (expectedList.size != actualList.size) {
          result.add(BodyItemMatchResult(path.joinToString("."), listOf(BodyMismatch(a, b,
            "Expected a List with ${expectedList.size} elements but received ${actualList.size} elements",
            path.joinToString("."), generateJsonDiff(expectedValues, actualValues)))))
        }
        result
      }
    }
  }

  private fun compareMaps(
    expectedValues: JsonValue.Object,
    actualValues: JsonValue.Object,
    a: JsonValue,
    b: JsonValue,
    path: List<String>,
    allowUnexpectedKeys: Boolean,
    matchers: MatchingRules
  ): List<BodyItemMatchResult> {
    return if (expectedValues.isEmpty() && actualValues.isNotEmpty() && !allowUnexpectedKeys) {
      listOf(BodyItemMatchResult(path.joinToString("."),
        listOf(BodyMismatch(a, b, "Expected an empty Map but received ${valueOf(actualValues)}",
        path.joinToString("."), generateJsonDiff(expectedValues, actualValues)))))
    } else {
      val result = mutableListOf<BodyItemMatchResult>()
      if (allowUnexpectedKeys && expectedValues.size > actualValues.size) {
        result.add(BodyItemMatchResult(path.joinToString("."), listOf(BodyMismatch(a, b,
          "Expected a Map with at least ${expectedValues.size} elements but received " +
            "${actualValues.size} elements", path.joinToString("."),
          generateJsonDiff(expectedValues, actualValues)))))
      } else if (!allowUnexpectedKeys && expectedValues.size != actualValues.size) {
        result.add(BodyItemMatchResult(path.joinToString("."), listOf(BodyMismatch(a, b,
          "Expected a Map with ${expectedValues.size} elements but received ${actualValues.size} elements",
          path.joinToString("."), generateJsonDiff(expectedValues, actualValues)))))
      }
      if (Matchers.wildcardMatchingEnabled() && Matchers.wildcardMatcherDefined(path + "any", "body", matchers)) {
        actualValues.entries.forEach { (key, value) ->
          if (expectedValues.has(key)) {
            result.addAll(compare(path + key, expectedValues[key], value, allowUnexpectedKeys, matchers))
          } else if (!allowUnexpectedKeys) {
            result.addAll(compare(path + key, expectedValues.entries.values.firstOrNull()
              ?: JsonValue.Null, value, allowUnexpectedKeys, matchers))
          }
        }
      } else {
        expectedValues.entries.forEach { (key, value) ->
          if (actualValues.has(key)) {
            result.addAll(compare(path + key, value, actualValues[key]!!, allowUnexpectedKeys,
              matchers))
          } else {
            result.add(BodyItemMatchResult(path.joinToString("."),
              listOf(BodyMismatch(a, b, "Expected $key=${valueOf(value)} but was missing",
              path.joinToString("."), generateJsonDiff(expectedValues, actualValues)))))
          }
        }
      }
      result
    }
  }

  private fun compareValues(
    path: List<String>,
    expected: JsonValue,
    actual: JsonValue,
    matchers: MatchingRules
  ): List<BodyItemMatchResult> {
    return if (Matchers.matcherDefined("body", path, matchers)) {
      logger.debug { "compareValues: Matcher defined for path $path" }
      listOf(BodyItemMatchResult(path.joinToString("."),
        Matchers.domatch(matchers, "body", path, expected, actual, BodyMismatchFactory)))
    } else {
      logger.debug { "compareValues: No matcher defined for path $path, using equality" }
      if (expected == actual) {
        listOf(BodyItemMatchResult(path.joinToString("."), emptyList()))
      } else {
        listOf(BodyItemMatchResult(path.joinToString("."),
          listOf(BodyMismatch(expected, actual, "Expected ${valueOf(expected)} (${typeOf(expected)}) " +
            "but received ${valueOf(actual)} (${typeOf(actual)})", path.joinToString(".")))))
      }
    }
  }
}
