package au.com.dius.pact.core.matchers.util

import spock.lang.Specification
import spock.lang.Unroll

@SuppressWarnings('ClosureAsLastMethodParameter')
class CollectionUtilsSpec extends Specification {

  def 'tails test'() {
    expect:
    CollectionUtilsKt.tails(['a', 'b', 'c', 'd']) == [['a', 'b', 'c', 'd'], ['b', 'c', 'd'], ['c', 'd'], ['d'], []]
    CollectionUtilsKt.tails(['something', '$']) == [['something', '$'], ['$'], []]
  }

  def 'corresponds test'() {
    expect:
    CollectionUtilsKt.<Integer, String>corresponds([1, 2, 3], ['1', '2', '3'], { a, b -> a == Integer.parseInt(b) })
    !CollectionUtilsKt.<Integer, String>corresponds([1, 2, 4], ['1', '2', '3'], { a, b -> a == Integer.parseInt(b) })
    !CollectionUtilsKt.<Integer, String>corresponds([1, 2, 3, 4], ['1', '2', '3'], { a, b -> a == Integer.parseInt(b) })
  }

  @Unroll
  def 'padTo test'() {
    expect:
    CollectionUtilsKt.padTo(list, size, 1) == result

    where:

    list      | size | result
    []        | 0    | []
    [1]       | 1    | [1]
    [1]       | 0    | []
    [1, 2, 3] | 0    | []
    [1, 2, 3] | 2    | [1, 2]
    [1, 2, 3] | 3    | [1, 2, 3]
    []        | 3    | [1, 1, 1]
    [1]       | 3    | [1, 1, 1]
    [1, 2, 3] | 6    | [1, 2, 3, 1, 1, 1]
  }

  @Unroll
  def 'instanceMapOf test'() {
    expect:
    CollectionUtilsKt.instanceMapOf(list) == map

    where:
    list                           | map
    [100, 100, 200, 200, 300]      | [100: 2, 200: 2, 300: 1]
    [100, 300, 300, 100, 200, 400] | [100: 2, 200: 1, 300: 2, 400: 1]

  }

  @Unroll
  def 'subsetOf test'() {
    given:
    def expectedMap = CollectionUtilsKt.instanceMapOf(expected)
    def actualMap = CollectionUtilsKt.instanceMapOf(actual)

    expect:
    CollectionUtilsKt.subsetOf(expectedMap, actualMap) == result

    where:
    expected                  | actual                         | result
    [100, 100]                | [100, 100, 400]                | true
    [100, 100]                | [100, 100, 400, 400]           | true
    [100, 100, 200, 200, 300] | [100, 300, 300, 100, 200, 400] | false
  }
}
