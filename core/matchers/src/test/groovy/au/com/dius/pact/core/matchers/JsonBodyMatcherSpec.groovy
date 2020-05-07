package au.com.dius.pact.core.matchers

import au.com.dius.pact.core.model.OptionalBody
import au.com.dius.pact.core.model.matchingrules.EqualsMatcher
import au.com.dius.pact.core.model.matchingrules.EqualsIgnoreOrderMatcher
import au.com.dius.pact.core.model.matchingrules.MatchingRulesImpl
import au.com.dius.pact.core.model.matchingrules.MaxEqualsIgnoreOrderMatcher
import au.com.dius.pact.core.model.matchingrules.MinEqualsIgnoreOrderMatcher
import au.com.dius.pact.core.model.matchingrules.MinTypeMatcher
import au.com.dius.pact.core.model.matchingrules.RegexMatcher
import au.com.dius.pact.core.model.matchingrules.TypeMatcher
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

@SuppressWarnings(['BracesForMethod', 'PrivateFieldCouldBeFinal'])
class JsonBodyMatcherSpec extends Specification {

  private matchers
  private JsonBodyMatcher matcher = new JsonBodyMatcher()

  def setup() {
    matchers = new MatchingRulesImpl()
  }

  def 'matching json bodies - return no mismatches - when comparing empty bodies'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.empty()
    expectedBody = OptionalBody.empty()
  }

  def 'matching json bodies - return no mismatches - when comparing a missing body to anything'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('"Blah"'.bytes)
    expectedBody = OptionalBody.missing()
  }

  def 'matching json bodies - return no mismatches - with equal bodies'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('"Blah"'.bytes)
    expectedBody = OptionalBody.body('"Blah"'.bytes)
  }

  def 'matching json bodies - return no mismatches - with equal Maps'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('{"something": 100}'.bytes)
    expectedBody = OptionalBody.body('{"something":100}'.bytes)
  }

  def 'matching json bodies - return no mismatches - with equal Lists'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('[100,200,300]'.bytes)
    expectedBody = OptionalBody.body('[100, 200, 300]'.bytes)
  }

  def 'matching json bodies - return no mismatches - with each like matcher on unequal lists'() {
    given:
    matchers.addCategory('body').addRule('$.list', new MinTypeMatcher(1))

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('{"list": [100, 200, 300, 400]}'.bytes)
    expectedBody = OptionalBody.body('{"list": [100]}'.bytes)
  }

  def 'matching json bodies - return no mismatches - with each like matcher on empty list'() {
    given:
    matchers.addCategory('body').addRule('$.list', new MinTypeMatcher(0))

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('{"list": []}'.bytes)
    expectedBody = OptionalBody.body('{"list": [100]}'.bytes)
  }

  def 'matching json bodies - returns a mismatch - when comparing anything to an empty body'() {
    expect:
    !matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body(''.bytes)
    expectedBody = OptionalBody.body('"Blah"'.bytes)
  }

  def 'matching json bodies - returns a mismatch - when comparing anything to a null body'() {
    expect:
    !matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('""'.bytes)
    expectedBody = OptionalBody.nullBody()
  }

  def 'matching json bodies - returns no mismatch - when comparing an empty map to a non-empty one'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('{"something": 100}'.bytes)
    expectedBody = OptionalBody.body('{}'.bytes)
  }

  def '''matching json bodies - returns a mismatch - when comparing an empty map to a non-empty one and we do not
         allow unexpected keys'''() {
    expect:
    matcher.matchBody(expectedBody, actualBody, false, matchers).find {
      it instanceof BodyMismatch &&
        it.mismatch.contains('Expected an empty Map but received {"something":100}')
    }

    where:
    actualBody = OptionalBody.body('{"something": 100}'.bytes)
    expectedBody = OptionalBody.body('{}'.bytes)
  }

  def 'matching json bodies - returns a mismatch - when comparing an empty list to a non-empty one'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).find {
      it instanceof BodyMismatch &&
        it.mismatch.contains('Expected an empty List but received [100]')
    }

    where:
    actualBody = OptionalBody.body('[100]'.bytes)
    expectedBody = OptionalBody.body('[]'.bytes)
  }

  def 'matching json bodies - returns a mismatch - when comparing a map to one with less entries'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).find {
      it instanceof BodyMismatch &&
        it.mismatch.contains('Expected a Map with at least 2 elements but received 1 elements')
    }

    where:

    actualBody = OptionalBody.body('{"something": 100}'.bytes)
    expectedBody = OptionalBody.body('{"something": 100, "somethingElse": 100}'.bytes)
  }

  def 'matching json bodies - returns a mismatch - when comparing a list to one with with different size'() {
    given:
    def actualBody = OptionalBody.body('[1,2,3]'.bytes)
    def expectedBody = OptionalBody.body('[1,2,3,4]'.bytes)

    when:
    def mismatches = matcher.matchBody(expectedBody, actualBody, true, matchers).findAll {
      it instanceof BodyMismatch
    }*.mismatch

    then:
    mismatches.size() == 2
    mismatches.contains('Expected a List with 4 elements but received 3 elements')
    mismatches.contains('Expected 4 but was missing')
  }

  def 'matching json bodies - returns a mismatch - when the actual body is missing a key'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).find {
      it instanceof BodyMismatch &&
        it.mismatch.contains('Expected somethingElse=100 but was missing')
    }

    where:

    actualBody = OptionalBody.body('{"something": 100}'.bytes)
    expectedBody = OptionalBody.body('{"something": 100, "somethingElse": 100}'.bytes)
  }

  def 'matching json bodies - returns a mismatch - when the actual body has invalid value'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).find {
      it instanceof BodyMismatch &&
        it.mismatch.contains('Expected 100 but received 101')
    }

    where:

    actualBody = OptionalBody.body('{"something": 101}'.bytes)
    expectedBody = OptionalBody.body('{"something": 100}'.bytes)
  }

  def 'matching json bodies - returns a mismatch - when comparing a map to a list'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).find {
      it instanceof BodyMismatch &&
        it.mismatch.contains('Type mismatch: Expected Map {"something":100,"somethingElse":100} ' +
          'but received List [100,100]')
    }

    where:

    actualBody = OptionalBody.body('[100, 100]'.bytes)
    expectedBody = OptionalBody.body('{"something": 100, "somethingElse": 100}'.bytes)
  }

  def 'matching json bodies - returns a mismatch - when comparing list to anything'() {
    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).find {
      it instanceof BodyMismatch &&
        it.mismatch.contains('Type mismatch: Expected List [100,100] but received Primitive 100')
    }

    where:

    actualBody = OptionalBody.body('100'.bytes)
    expectedBody = OptionalBody.body('[100, 100]'.bytes)
  }

  def 'matching json bodies - with a matcher defined - delegate to the matcher'() {
    given:
    matchers.addCategory('body').addRule('$.something', new RegexMatcher('\\d+'))

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('{"something": 100}'.bytes)
    expectedBody = OptionalBody.body('{"something": 101}'.bytes)
  }

  @RestoreSystemProperties
  def 'matching json bodies - with a matcher defined - and when the actual body is missing a key, not be a mismatch'() {
    given:
    matchers.addCategory('body').addRule('$.*', TypeMatcher.INSTANCE)
    System.setProperty(Matchers.PACT_MATCHING_WILDCARD, 'true')

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:
    actualBody = OptionalBody.body('{"something": 100, "other": 100}'.bytes)
    expectedBody = OptionalBody.body('{"somethingElse": 100}'.bytes)
  }

  @RestoreSystemProperties
  def 'matching json bodies - with a matcher defined - defect 562: matching a list at the root with extra fields'() {
    given:
    matchers.addCategory('body').addRule('$', new MinTypeMatcher(1))
    matchers.addCategory('body').addRule('$[*].*', TypeMatcher.INSTANCE)
    System.setProperty(Matchers.PACT_MATCHING_WILDCARD, 'true')

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('''[
        {
            "documentId": 0,
            "documentCategoryId": 5,
            "documentCategoryCode": null,
            "contentLength": 0,
            "tags": null
        },
        {
            "documentId": 1,
            "documentCategoryId": 5,
            "documentCategoryCode": null,
            "contentLength": 0,
            "tags": null
        }
    ]'''.bytes)
    expectedBody = OptionalBody.body('''[{
      "name": "Test",
      "documentId": 0,
      "documentCategoryId": 5,
      "contentLength": 0
    }]'''.bytes)
  }

  @RestoreSystemProperties
  def 'returns a mismatch - when comparing maps with different keys and wildcard matching is disabled'() {
    given:
    matchers.addCategory('body').addRule('$.*', new MinTypeMatcher(0))
    System.setProperty(Matchers.PACT_MATCHING_WILDCARD, 'false')

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).find {
      it instanceof BodyMismatch && it.mismatch.contains('Expected height=100 but was missing')
    }

    where:

    actualBody = OptionalBody.body('{"id": 100, "width": 100}'.bytes)
    expectedBody = OptionalBody.body('{"id": 100, "height": 100}'.bytes)
  }

  @RestoreSystemProperties
  def 'returns no mismatch - when comparing maps with different keys and wildcard matching is enabled'() {
    given:
    matchers.addCategory('body').addRule('$.*', new MinTypeMatcher(0))
    System.setProperty(Matchers.PACT_MATCHING_WILDCARD, 'true')

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:

    actualBody = OptionalBody.body('{"id": 100, "width": 100}'.bytes)
    expectedBody = OptionalBody.body('{"id": 100, "height": 100}'.bytes)
  }

  @Unroll
  def 'matching json bodies - with ignore-order - return no mismatches when comparing lists'() {
    given:
    def expectedBody = OptionalBody.body(expected.bytes)
    def actualBody = OptionalBody.body(actual.bytes)
    matchers.addCategory('body')
      .addRule('$', EqualsIgnoreOrderMatcher.INSTANCE)

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:
    expected                             | actual
    '[1, 2, 3, 4]'                       | '[2, 3, 1, 4]'
    '["a", "b", "c", "d"]'               | '["c", "a", "b", "d"]'
    '[1, "b", 3, "d"]'                   | '["d", 1, 3, "b"]'
    '[{"i": "a"}, {"i": 2}, {"i": "c"}]' | '[{"i": 2}, {"i": "c"}, {"i": "a"}]'
  }

  @Unroll
  def 'matching json bodies - with ignore-order - return a mismatch when actual is missing an element'() {
    given:
    def actualBody = OptionalBody.body(actual.bytes)
    def expectedBody = OptionalBody.body(expected.bytes)
    matchers.addCategory('body')
      .addRule('$', EqualsIgnoreOrderMatcher.INSTANCE)

    when:
    def mismatches = matcher.matchBody(expectedBody, actualBody, true, matchers)

    then:
    mismatches.size() == 1
    // @asteffey - update error message based on change to MatcherExecutor
    mismatches*.mismatch == ["Expected $expected to equal $actual ignoring order of elements"]
    mismatches*.path == ['$']

    where:
    expected                            | actual                              | missing
    '[1, 2, 3, 4]'                      | '[2, 3, 1, 5]'                      | '4'
    '[{"i":"a"}, {"i":"b"}, {"i":"c"}]' | '[{"i":"b"}, {"i":"a"}, {"i":"d"}]' | '{"i":"c"}'
  }

  // @asteffey - exercise EqualsIgnoreOrderMatcher expecting actual to have same size
  @Unroll
  def 'matching json bodies - return a mismatch - with ignore-order - when actual has extra elements'() {
    given:
    def actualBody = OptionalBody.body(actual.bytes)
    def expectedBody = OptionalBody.body(expected.bytes)
    matchers.addCategory('body')
      .addRule('$', EqualsIgnoreOrderMatcher.INSTANCE)

    when:
    def mismatches = matcher.matchBody(expectedBody, actualBody, true, matchers)

    then:
    !mismatches.empty
    mismatches*.mismatch == ["Expected $actual to have 3 elements"]
    mismatches*.path == ['$']

    where:
    expected                          | actual
    '[1,2,3]'                         | '[1,2,3,4]'
    '[{"i":"a"},{"i":"b"},{"i":"c"}]' | '[{"i":"a"},{"i":"b"},{"i":"c"},{"i":"d"}]'
  }

  // @asteffey - create a MaxEqualsIgnoreOrderMatcher as test above was changed to EqualsIgnoreOrderMatcher
  @Unroll
  def 'matching json bodies - with max-equals-ignore-order - return a mismatch when actual has extra elements'() {
    given:
    def maxSize = 3
    def actualBody = OptionalBody.body(actual.bytes)
    def expectedBody = OptionalBody.body(expected.bytes)
    matchers.addCategory('body')
      .addRule('$', new MaxEqualsIgnoreOrderMatcher(maxSize))

    when:
    def mismatches = matcher.matchBody(expectedBody, actualBody, true, matchers)

    then:
    !mismatches.empty
    mismatches*.mismatch == ["Expected $actual to have maximum $maxSize"]
    mismatches*.path == ['$']

    where:
    expected                | actual                                      | actualSize
    '[1,2]'                 | '[1,2,3,4,5,6]'                             | 6
    '[{"i":"a"},{"i":"b"}]' | '[{"i":"a"},{"i":"b"},{"i":"c"},{"i":"d"}]' | 4
  }

  @Unroll
  def 'matching json bodies - with min-equals-ignore-order type matching - when actual has extra elements'() {
    given:
    def actualBody = OptionalBody.body(actual.bytes)
    def expectedBody = OptionalBody.body(expected.bytes)
    matchers.addCategory('body')
            .addRule('$', new MinEqualsIgnoreOrderMatcher(3))

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty == match

    where:
    expected                | actual                            | match
    '[1,2]'                 | '[1,2,3]'                         | true
    '[1,2]'                 | '[2,1,3]'                         | true
    '[1,2]'                 | '[1,3,4]'                         | false
    '[{"i":"a"},{"i":"b"}]' | '[{"i":"a"},{"i":"b"},{"i":"c"}]' | true
  }

  @Unroll
  def 'matching json bodies - with ignore-order and regex - return no mismatches'() {
    given:
    def actualBody = OptionalBody.body(actual.bytes)
    def expectedBody = OptionalBody.body(expected.bytes)
    matchers.addCategory('body')
      .addRule('$', EqualsIgnoreOrderMatcher.INSTANCE)
      .addRule('$[0]', new RegexMatcher('[a-z]'))
      .addRule('$[1]', new RegexMatcher('[A-Z]'))

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty

    where:
    expected                | actual                            | match
    '["a","A"]'             | '["b","B"]'                       | true
    '["a","A"]'             | '["B","b"]'                       | true
  }

  def 'matching json bodies - with min-equals-ignore-order - return type mismatch on bad type'() {
    given:
    matchers.addCategory('body')
      .addRule('$', new MinEqualsIgnoreOrderMatcher(1))
      .addRule('$[*]', TypeMatcher.INSTANCE)

    when:
    def mismatches = matcher.matchBody(expectedBody, actualBody, true, matchers)

    then:
    !mismatches.empty
    mismatches*.mismatch == ['Expected "bad" (JsonPrimitive) to be the same type as 100 (JsonPrimitive)']
    mismatches*.path == ['$.1']

    where:
    actualBody = OptionalBody.body('[200, 100, 300, "bad"]'.bytes)
    expectedBody = OptionalBody.body('[100]'.bytes)
  }

  def 'matching json bodies - with min-equals-ignore-order - return equality mismatch'() {
    given:
    matchers.addCategory('body')
      .addRule('$', new MinEqualsIgnoreOrderMatcher(1))

    when:
    def mismatches = matcher.matchBody(expectedBody, actualBody, true, matchers)

    then:
    !mismatches.empty
    mismatches*.mismatch == ['Expected [50] to equal [200, 100, 300, 400] ignoring order of elements']
    mismatches*.path == ['$']

    where:
    actualBody = OptionalBody.body('[200, 100, 300, 400]'.bytes)
    expectedBody = OptionalBody.body('[50]'.bytes)
  }

  def 'matching json bodies - with ignore-order - return a mismatch when inorder defaults on other list'() {
    given:
    matchers.addCategory('body')
      .addRule('$[0].array1', EqualsIgnoreOrderMatcher.INSTANCE)

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).find {
      it instanceof BodyMismatch && it.mismatch.contains('Expected 1 but received 2')
    }

    where:
    actualBody = OptionalBody.body('''{
     "array1": [{"foo": "a"},{"foo": "b"}],
     "array2": [2, 3, 1, 4]
     }'''.bytes)
    expectedBody = OptionalBody.body('''{
     "array1": [{"foo": "b"},{"foo": "a"}],
     "array2": [1, 2, 3, 4]
     }'''.bytes)
  }

  @Unroll
  def 'matching json bodies - with min-equals-ignore-order - and multiple of the same element'() {
    given:
    def actualBody = OptionalBody.body(actual.bytes)
    def expectedBody = OptionalBody.body(expected.bytes)
    matchers.addCategory('body')
      .addRule('$', new MinEqualsIgnoreOrderMatcher(min))

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty == matches

    where:
    expected                    | actual                           | matches | min
    '[100, 100]'                | '[100, 100, 400]'                | true    | 2
    '[100, 100]'                | '[100, 100, 100, 400]'           | true    | 2
    '[100, 100]'                | '[100, 200, 400]'                | false   | 2 // only one 100 in actual
    '[100, 100, 200]'           | '[100, 200, 100, 400]'           | true    | 3
    '[100, 100, 200, 200, 300]' | '[100, 300, 200, 100, 200, 400]' | true    | 5
    '[100, 100, 200, 200, 300]' | '[100, 300, 300, 100, 200, 400]' | false   | 5 // only one 200 in actual
    '[100, 100, 200, 200, 300]' | '[100, 300, 200, 100, 200, 400]' | false   | 7 // not enough in actual
  }

  @Unroll
  def 'matching json bodies - with ignore-order - and multiple of the same element'() {
    given:
    def actualBody = OptionalBody.body(actual.bytes)
    def expectedBody = OptionalBody.body(expected.bytes)
    matchers.addCategory('body')
            .addRule('$', new MinEqualsIgnoreOrderMatcher(2))

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty == matches

    where:
    expected        | actual            | matches
    '[100, 100]'    | '[100, 200]'      | false
    '[100, 100]'    | '[100, 100, 400]' | true
    '[100, 100]'    | '[100, 400, 100]' | true
  }

  def 'matching json bodies - with ignore-order and regex - returns a mismatch when multiple of the same element'() {
    given:
    def expected = '["red", "blue"]'
    def actual = '["blue", "seven"]'
    def expectedBody = OptionalBody.body(expected.bytes)
    def actualBody = OptionalBody.body(actual.bytes)
    matchers.addCategory('body')
      .addRule('$', EqualsIgnoreOrderMatcher.INSTANCE)
      .addRule('$[*]', new RegexMatcher('red|blue'))

    when:
    def mismatches = matcher.matchBody(expectedBody, actualBody, true, matchers)

    then:
    !mismatches.empty
    mismatches*.mismatch == ["Expected $expected to equal $actual ignoring order of elements",
                             'Expected "seven" to match \'red|blue\'',
                             'Expected "seven" to match \'red|blue\'']
    mismatches*.path == ['$', '$.2', '$.2']
  }

  @Unroll
  def 'matching json bodies - with ignore-order, addtnl matchers - and elements with unique ids'() {
    given:
    matchers.addCategory('body')
      .addRule('$', EqualsIgnoreOrderMatcher.INSTANCE)
      .addRule('$[*].id', new EqualsMatcher())
      .addRule('$[0].status', new EqualsMatcher())
      .addRule('$[1].status', new RegexMatcher('up|down'))
    def expectedBody = OptionalBody.body('[{"id":"a", "status":"up"},{"id":"b", "status":"down"}]'.bytes)
    def actualBody = OptionalBody.body(actual.bytes)

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty == matches

    where:
    actual                                                    | matches
    '[{"id":"b", "status":"up"},{"id":"a", "status":"down"}]' | false
    '[{"id":"a", "status":"up"},{"id":"b", "status":"down"}]' | true
    '[{"id":"a", "status":"up"},{"id":"b", "status":"up"}]'   | true
    '[{"id":"b", "status":"down"},{"id":"a", "status":"up"}]' | true
  }

  @Unroll
  def 'matching json bodies - with ignore-order, addtnl matchers - and elements with unique ids plus numbers'() {
    given:
    matchers.addCategory('body')
      .addRule('$', EqualsIgnoreOrderMatcher.INSTANCE)
      .addRule('$[*].id', new EqualsMatcher())
      .addRule('$[0].status', new EqualsMatcher())
      .addRule('$[1]', TypeMatcher.INSTANCE)
      .addRule('$[2].status', new RegexMatcher('up|down'))
    def actualBody = OptionalBody.body(actual.bytes)
    def expectedBody = OptionalBody.body('[{"id":"a", "status":"up"}, 4, {"id":"b", "status":"down"}]'.bytes)

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty == matches

    where:
    actual                                                          | matches
    '[{"id":"a", "status":"up"}, 4, {"id":"b", "status":"down"}]'   | true
    '[{"id":"a", "status":"up"}, "4", {"id":"b", "status":"down"}]' | false
    '[{"id":"b", "status":"up"}, {"id":"a", "status":"up"}, 5]'     | true
    '[{"id":"b", "status":"down"}, {"id":"a", "status":"up"}, 5]'   | true
    '[{"id":"b", "status":"up"}, {"id":"a", "status":"down"}, 5]'   | false
    '[{"id":"c", "status":"up"}, {"id":"a", "status":"up"}, 5]'     | false
  }

  @Unroll
  def 'matching json bodies - with ignore-order, addtnl matchers - and elements without unique ids'() {
    given:
    matchers.addCategory('body')
      .addRule('$', EqualsIgnoreOrderMatcher.INSTANCE)
      .addRule('$[0].id', new RegexMatcher('a|b'))
      .addRule('$[0].status', new EqualsMatcher())
      .addRule('$[1].id', new RegexMatcher('b|c'))
      .addRule('$[1].status', new RegexMatcher('up|down'))
    def expectedBody = OptionalBody.body('[{"id":"a", "status":"up"},{"id":"b", "status":"down"}]'.bytes)
    def actualBody = OptionalBody.body(actual.bytes)

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty == matches

    where:
    actual                                                    | matches
    '[{"id":"b", "status":"up"},{"id":"b", "status":"down"}]' | true  // expct[0]==actl[0] & expct[1]==actl[0|1]
    '[{"id":"a", "status":"up"},{"id":"b", "status":"down"}]' | true  // expct[0]==actl[0] & expct[1]==actl[1]
    '[{"id":"a", "status":"up"},{"id":"b", "status":"up"}]'   | true  // expct[0]==actl[0] & expct[1]==actl[1]
    '[{"id":"b", "status":"down"},{"id":"c", "status":"up"}]' | false // expct[0] no match & expct[1]==actl[1]
    '[{"id":"b", "status":"up"},{"id":"a", "status":"down"}]' | false // expct[0]==expct[1]==actl[0], no unique for each
    '[{"id":"b", "status":"up"},{"id":"c", "status":"up"}]'   | true  // expct[0]==actl[0] & expct[1]==actl[1]
    '[{"id":"c", "status":"up"},{"id":"b", "status":"up"}]'   | true  // expct[0]==actl[1] & expct[1]==actl[0]
  }

  def 'matching json bodies - with ignore-order - return no mismatches on nested lists'() {
    given:
    def expected = '[[1,2,3],[4,5,6]]'
    def actual = '[[6,4,5],[2,3,1]]'
    def expectedBody = OptionalBody.body(expected.bytes)
    def actualBody = OptionalBody.body(actual.bytes)
    matchers.addCategory('body')
      .addRule('$', EqualsIgnoreOrderMatcher.INSTANCE)

    expect:
    matcher.matchBody(expectedBody, actualBody, true, matchers).empty
  }

  def 'matching json bodies - with ignore-order - return mismatches on nested lists when overriding equality'() {
    given:
    def expected = '[[1,2,3],[4,5,6]]'
    def actual = '[[6,4,5],[2,3,1]]'
    def expectedBody = OptionalBody.body(expected.bytes)
    def actualBody = OptionalBody.body(actual.bytes)
    matchers.addCategory('body')
      .addRule('$', EqualsIgnoreOrderMatcher.INSTANCE)
      .addRule('$[0]', EqualsMatcher.INSTANCE)

    when:
    def mismatches = matcher.matchBody(expectedBody, actualBody, true, matchers)

    then:
    // Returns 13 mismatches because the EqualsMatcher on $[0] gets applied recursively
    !mismatches.empty
  }

  // TODO Add some more list within list test cases...

}
