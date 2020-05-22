package au.com.dius.pact.consumer

import au.com.dius.pact.consumer.dsl.PactDslJsonArray
import au.com.dius.pact.consumer.dsl.PactDslJsonRootValue
import au.com.dius.pact.core.model.PactSpecVersion
import au.com.dius.pact.core.model.matchingrules.DateMatcher
import au.com.dius.pact.core.model.matchingrules.EqualsIgnoreOrderMatcher
import au.com.dius.pact.core.model.matchingrules.MatchingRuleGroup
import au.com.dius.pact.core.model.matchingrules.MaxTypeMatcher
import au.com.dius.pact.core.model.matchingrules.MinEqualsIgnoreOrderMatcher
import au.com.dius.pact.core.model.matchingrules.MinMaxEqualsIgnoreOrderMatcher
import au.com.dius.pact.core.model.matchingrules.MinTypeMatcher
import au.com.dius.pact.core.model.matchingrules.NumberTypeMatcher
import au.com.dius.pact.core.model.matchingrules.TypeMatcher
import groovy.json.JsonSlurper
import spock.lang.Specification

class PactDslJsonArrayMatcherSpec extends Specification {

    private PactDslJsonArray subject

    def setup() {
      subject = new PactDslJsonArray()
    }

    def 'String Matcher Throws Exception If The Example Does Not Match The Pattern'() {
      when:
      subject.stringMatcher('[a-z]+', 'dfhdsjf87fdjh')

      then:
      thrown(InvalidMatcherException)
    }

    def 'Hex Matcher Throws Exception If The Example Is Not A Hexadecimal Value'() {
      when:
      subject.hexValue('dfhdsjf87fdjh')

      then:
      thrown(InvalidMatcherException)
    }

    def 'UUID Matcher Throws Exception If The Example Is Not A UUID'() {
      when:
      subject.uuid('dfhdsjf87fdjh')

      then:
      thrown(InvalidMatcherException)
    }

    def 'Allows Like Matchers When The Array Is The Root'() {
      given:
      Date date = new Date()
      subject = (PactDslJsonArray) PactDslJsonArray.arrayEachLike()
          .date('clearedDate', 'mm/dd/yyyy', date)
          .stringType('status', 'STATUS')
          .decimalType('amount', 100.0)
          .closeObject()

      expect:
        new JsonSlurper().parseText(subject.body.toString()) == [
          [amount: 100, clearedDate: date.format('mm/dd/yyyy'), status: 'STATUS']
        ]
        subject.matchers.matchingRules == [
          '': new MatchingRuleGroup([new MinTypeMatcher(0)]),
          '[*].amount': new MatchingRuleGroup([new NumberTypeMatcher(NumberTypeMatcher.NumberType.DECIMAL)]),
          '[*].clearedDate': new MatchingRuleGroup([new DateMatcher('mm/dd/yyyy')]),
          '[*].status': new MatchingRuleGroup([TypeMatcher.INSTANCE])
        ]
    }

    def 'Allows Like Min Matchers When The Array Is The Root'() {
      given:
      Date date = new Date()
      subject = (PactDslJsonArray) PactDslJsonArray.arrayMinLike(1)
          .date('clearedDate', 'mm/dd/yyyy', date)
          .stringType('status', 'STATUS')
          .decimalType('amount', 100.0)
          .closeObject()

      expect:
        new JsonSlurper().parseText(subject.body.toString()) == [
          [amount: 100, clearedDate: date.format('mm/dd/yyyy'), status: 'STATUS']
        ]
        subject.matchers.matchingRules == [
          '': new MatchingRuleGroup([new MinTypeMatcher(1)]),
          '[*].amount': new MatchingRuleGroup([new NumberTypeMatcher(NumberTypeMatcher.NumberType.DECIMAL)]),
          '[*].clearedDate': new MatchingRuleGroup([new DateMatcher('mm/dd/yyyy')]),
          '[*].status': new MatchingRuleGroup([TypeMatcher.INSTANCE])
        ]
    }

    def 'Allows Like Max Matchers When The Array Is The Root'() {
      given:
      Date date = new Date()
      subject = (PactDslJsonArray) PactDslJsonArray.arrayMaxLike(10)
          .date('clearedDate', 'mm/dd/yyyy', date)
          .stringType('status', 'STATUS')
          .decimalType('amount', 100.0)
          .closeObject()

      expect:
        new JsonSlurper().parseText(subject.body.toString()) == [
          [amount: 100, clearedDate: date.format('mm/dd/yyyy'), status: 'STATUS']
        ]
        subject.matchers.matchingRules == [
          '': new MatchingRuleGroup([new MaxTypeMatcher(10)]),
          '[*].amount': new MatchingRuleGroup([new NumberTypeMatcher(NumberTypeMatcher.NumberType.DECIMAL)]),
          '[*].clearedDate': new MatchingRuleGroup([new DateMatcher('mm/dd/yyyy')]),
          '[*].status': new MatchingRuleGroup([TypeMatcher.INSTANCE])
        ]
    }

  def 'root array each like allows the number of examples to be set'() {
    given:
    subject = PactDslJsonArray.arrayEachLike(3)
      .date('defDate')
      .decimalType('cost')
      .closeObject()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.size == 3
    result.every { it.keySet() == ['defDate', 'cost'] as Set }
  }

  def 'root array min like allows the number of examples to be set'() {
    given:
    subject = PactDslJsonArray.arrayMinLike(2, 3)
      .date('defDate')
      .decimalType('cost')
      .closeObject()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.size == 3
    result.every { it.keySet() == ['defDate', 'cost'] as Set }
  }

  def 'root array max like allows the number of examples to be set'() {
    given:
    subject = PactDslJsonArray.arrayMaxLike(10, 3)
      .date('defDate')
      .decimalType('cost')
      .closeObject()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.size == 3
    result.every { it.keySet() == ['defDate', 'cost'] as Set }
  }

  def 'each like allows the number of examples to be set'() {
    given:
    subject = new PactDslJsonArray()
      .eachLike(2)
        .date('defDate')
        .decimalType('cost')
        .closeObject()
      .closeArray()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.first().size == 2
    result.first().every { it.keySet() == ['defDate', 'cost'] as Set }
  }

  def 'min like allows the number of examples to be set'() {
    given:
    subject = new PactDslJsonArray()
      .minArrayLike(1, 2)
        .date('defDate')
        .decimalType('cost')
        .closeObject()
      .closeArray()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.first().size == 2
    result.first().every { it.keySet() == ['defDate', 'cost'] as Set }
  }

  def 'max like allows the number of examples to be set'() {
    given:
    subject = new PactDslJsonArray()
      .maxArrayLike(10, 2)
        .date('defDate')
        .decimalType('cost')
        .closeObject()
      .closeArray()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.first().size == 2
    result.first().every { it.keySet() == ['defDate', 'cost'] as Set }
  }

  def 'eachlike supports matching arrays of basic values'() {
    given:
    subject = new PactDslJsonArray()
      .eachLike(PactDslJsonRootValue.stringType('eachLike'))
      .maxArrayLike(2, PactDslJsonRootValue.stringType('maxArrayLike'))
      .minArrayLike(2, PactDslJsonRootValue.stringType('minArrayLike'))

    when:
    def result = subject.body.toString()

    then:
    result == '[["eachLike"],["maxArrayLike"],["minArrayLike","minArrayLike"]]'
    subject.matchers.toMap(PactSpecVersion.V2) == [
      '$.body[1]': [max: 2, match: 'type'],
      '$.body[2]': [min: 2, match: 'type'],
      '$.body[0]': [min: 0, match: 'type'],
      '$.body[1][*]': [match: 'type'],
      '$.body[2][*]': [match: 'type'],
      '$.body[0][*]': [match: 'type']
    ]
  }

  def 'matching root level arrays of basic values'() {
    given:
    subject = PactDslJsonArray.arrayEachLike(PactDslJsonRootValue.stringType('eachLike'))

    when:
    def result = subject.body.toString()

    then:
    result == '["eachLike"]'
    subject.matchers.toMap(PactSpecVersion.V2) == [
      '$.body': [match: 'type', min: 0],
      '$.body[*]': [match: 'type']
    ]
  }

  def 'matching root level arrays of basic values with max'() {
    given:
    subject = PactDslJsonArray.arrayMaxLike(2, PactDslJsonRootValue.stringType('maxLike'))

    when:
    def result = subject.body.toString()

    then:
    result == '["maxLike"]'
    subject.matchers.toMap(PactSpecVersion.V2) == [
      '$.body': [match: 'type', max: 2],
      '$.body[*]': [match: 'type']
    ]
  }

  def 'matching root level arrays of basic values with min'() {
    given:
    subject = PactDslJsonArray.arrayMinLike(2, PactDslJsonRootValue.stringType('minLike'))

    when:
    def result = subject.body.toString()

    then:
    result == '["minLike","minLike"]'
    subject.matchers.toMap(PactSpecVersion.V2) == [
      '$.body': [match: 'type', min: 2],
      '$.body[*]': [match: 'type']
    ]
  }

  def 'unorderedArrayMinLike generates content with correct values and matching rules'() {
    given:
    subject = PactDslJsonArray.unorderedArrayMinLike(3, PactDslJsonRootValue.integerType(1))

    expect:
    subject.body.toString() == '[1,1,1]'
    def matchingRules = subject.matchers.matchingRules.values()
    matchingRules[0].rules == [new MinEqualsIgnoreOrderMatcher(3)]
    matchingRules[1].rules == [new NumberTypeMatcher(NumberTypeMatcher.NumberType.INTEGER)]
  }

  def 'unorderedEachArrayLike generates content with correct values and matching rules()'() {
    given:
    subject = new PactDslJsonArray()
      .unorderedEachArrayLike(3)
        .string('element')
        .closeArray()
      .closeArray()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.toString() == '[[[element], [element], [element]]]'
    subject.matchers.matchingRules == [
      '[0]': new MatchingRuleGroup([new MinEqualsIgnoreOrderMatcher(0)])
    ]
  }

  def 'unorderedArrayMatcher generates content with correct values and matching rules'() {
    given:
    subject = new PactDslJsonArray().unorderedArrayMatcher()
        .array().number(1).number(2).number(3).closeArray()
        .array().number(4).number(5).number(6).closeArray()
      .close()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.toString() == '[[1, 2, 3], [4, 5, 6]]'
    subject.matchers.matchingRules == [
      '$': new MatchingRuleGroup([EqualsIgnoreOrderMatcher.INSTANCE])
    ]
  }

  def 'unorderedEachLike generates content with correct values and matching rules'() {
    given:
    subject = new PactDslJsonArray()
      .unorderedEachLike(2)
        .stringValue('foo', 'Foo')
        .numberValue('bar', 5)
      .closeObject()
    .close()

    when:
    def result = subject.body.toString()

    then:
    result == '[[{"bar":5,"foo":"Foo"},{"bar":5,"foo":"Foo"}]]'
    subject.matchers.matchingRules == [
      '$[0]': new MatchingRuleGroup([EqualsIgnoreOrderMatcher.INSTANCE])
    ]
  }

  def 'unorderedMinMaxArrayLike generates content with correct values and matching rules'() {
    given:
    subject = new PactDslJsonArray()
      .unorderedMinMaxArrayLike(1, 4, 3)
        .numberType('foo', 100)
      .closeObject()
    .close()

    when:
    def result = subject.body.toString()

    then:
    result == '[[{"foo":100},{"foo":100},{"foo":100}]]'
    subject.matchers.matchingRules == [
      '$[0]': new MatchingRuleGroup([new MinMaxEqualsIgnoreOrderMatcher(1, 4)]),
      '$[0][*].foo': new MatchingRuleGroup([new NumberTypeMatcher(NumberTypeMatcher.NumberType.NUMBER)])
    ]
  }
}
