package au.com.dius.pact.core.matchers

import au.com.dius.pact.core.matchers.util.corresponds
import au.com.dius.pact.core.matchers.util.tails
import au.com.dius.pact.core.model.PathToken
import au.com.dius.pact.core.model.matchingrules.*
import au.com.dius.pact.core.model.parsePath
import mu.KLogging
import java.util.Comparator
import java.util.function.Predicate

object Matchers : KLogging() {

  const val PACT_MATCHING_IGNORE_ORDER = "pact.matching.ignore-order"
  const val PACT_MATCHING_WILDCARD = "pact.matching.wildcard"
  private val intRegex = Regex("\\d+")

  private fun matchesToken(pathElement: String, token: PathToken): Int {
    return when (token) {
      is PathToken.Root -> if (pathElement == "$") 2 else 0
      is PathToken.Field -> if (pathElement == token.name) 2 else 0
      is PathToken.Index -> if (pathElement.matches(intRegex) && token.index == pathElement.toInt()) 2 else 0
      is PathToken.StarIndex -> if (pathElement.matches(intRegex)) 1 else 0
      is PathToken.Star -> 1
      else -> 0
    }
  }

  fun matchesPath(pathExp: String, path: List<String>): Int {
    val parseResult = parsePath(pathExp)
    val filter = tails(path.reversed()).filter { l ->
      corresponds(l.reversed(), parseResult) { pathElement, pathToken ->
        matchesToken(pathElement, pathToken) != 0
      }
    }
    return if (filter.isNotEmpty()) {
      filter.maxBy { seq -> seq.size }?.size ?: 0
    } else {
      0
    }
  }

  fun calculatePathWeight(pathExp: String, path: List<String>): Int {
    val parseResult = parsePath(pathExp)
    return path.zip(parseResult).asSequence().map {
        matchesToken(it.first, it.second)
    }.reduce { acc, i -> acc * i }
  }

  fun resolveMatchers(
    matchers: MatchingRules,
    category: String,
    items: List<String>,
    pathComparator: Comparator<String>
  ) = if (category == "body")
      matchers.rulesForCategory(category).filter(Predicate {
        matchesPath(it, items) > 0
      })
    else if (category == "header" || category == "query" || category == "metadata")
      matchers.rulesForCategory(category).filter(Predicate { key ->
        items.all { pathComparator.compare(key, it) == 0 }
      })
    else matchers.rulesForCategory(category)

  @JvmStatic
  @JvmOverloads
  fun matcherDefined(
    category: String,
    path: List<String>,
    matchers: MatchingRules?,
    pathComparator: Comparator<String> = Comparator.naturalOrder()
  ): Boolean =
    if (matchers != null)
      resolveMatchers(matchers, category, path, pathComparator).isNotEmpty()
    else false

  /**
   * Determines if the one and only IgnoreOrderMatcher is defined on an array
   */
  @JvmStatic
  fun ignoreOrderMatcherDefined(category: String, path: List<String>, matchers: MatchingRules?) =
  if (matchers != null) {
    val resolvedMatchers = matchers.rulesForCategory(category).filter(Predicate {
      // filter on paths that equal length of current path
      matchesPath(it, path) == path.size
    })
      resolvedMatchers.matchingRules.values.any { value -> value.rules.contains(IgnoreOrderMatcher) }
    } else false

  /**
   * Determines if any ignore-order matcher is defined on an array
   */
  @JvmStatic
  fun oneOfIgnoreOrderMatchersDefined(category: String, path: List<String>, matchers: MatchingRules?) =
    if (matchers != null) {
      val resolvedMatchers = matchers.rulesForCategory(category)
      resolvedMatchers.matchingRules.values.any { value -> value.rules.any {
        it is IgnoreOrderMatcher ||
        it is MinEqualsIgnoreOrderMatcher ||
        it is MaxEqualsIgnoreOrderMatcher ||
        it is MinMaxEqualsIgnoreOrderMatcher }
      }
    } else false

  /**
   * If ignore-order matching logic is enabled (where array contents can be in any order)
   * TODO possibly remove this function
   */
  @JvmStatic
  fun ignoreOrderMatchingEnabled(): Boolean = System.getProperty(PACT_MATCHING_IGNORE_ORDER)?.trim() == "true"

  /**
   * Determines if a matcher of the form '.*' exists for the path
   */
  @JvmStatic
  fun wildcardMatcherDefined(path: List<String>, category: String, matchers: MatchingRules?) =
    if (matchers != null) {
      val resolvedMatchers = matchers.rulesForCategory(category).filter(Predicate {
        matchesPath(it, path) == path.size
      })
      resolvedMatchers.matchingRules.keys.any { entry -> entry.endsWith(".*") }
    } else false

  /**
   * If wildcard matching logic is enabled (where keys are ignored and only values are compared)
   */
  @JvmStatic
  fun wildcardMatchingEnabled() = System.getProperty(PACT_MATCHING_WILDCARD)?.trim() == "true"

  @JvmStatic
  @JvmOverloads
  fun <M : Mismatch> domatch(
    matchers: MatchingRules,
    category: String,
    path: List<String>,
    expected: Any?,
    actual: Any?,
    mismatchFn: MismatchFactory<M>,
    pathComparator: Comparator<String> = Comparator.naturalOrder()
  ): List<M> {
    val matcherDef = selectBestMatcher(matchers, category, path, pathComparator)
    return domatch(matcherDef, path, expected, actual, mismatchFn)
  }

  @JvmStatic
  @JvmOverloads
  fun selectBestMatcher(
    matchers: MatchingRules,
    category: String,
    path: List<String>,
    pathComparator: Comparator<String> = Comparator.naturalOrder()
  ): MatchingRuleGroup {
    val matcherCategory = resolveMatchers(matchers, category, path, pathComparator)
    return if (category == "body")
      matcherCategory.maxBy(Comparator { a, b ->
        val weightA = calculatePathWeight(a, path)
        val weightB = calculatePathWeight(b, path)
        when {
          weightA == weightB -> when {
            a.length > b.length -> 1
            a.length < b.length -> -1
            else -> 0
          }
          weightA > weightB -> 1
          else -> -1
        }
      })
    else {
      matcherCategory.matchingRules.values.first()
    }
  }
}
