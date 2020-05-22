package au.com.dius.pact.consumer

import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.dsl.PactDslJsonRootValue
import au.com.dius.pact.core.model.matchingrules.EqualsIgnoreOrderMatcher
import au.com.dius.pact.core.model.matchingrules.EqualsMatcher
import au.com.dius.pact.core.model.matchingrules.MatchingRuleGroup
import au.com.dius.pact.core.model.matchingrules.MaxEqualsIgnoreOrderMatcher
import au.com.dius.pact.core.model.matchingrules.MaxTypeMatcher
import au.com.dius.pact.core.model.matchingrules.MinEqualsIgnoreOrderMatcher
import au.com.dius.pact.core.model.matchingrules.MinTypeMatcher
import au.com.dius.pact.core.model.matchingrules.NumberTypeMatcher
import au.com.dius.pact.core.model.matchingrules.TypeMatcher
import groovy.json.JsonSlurper
import spock.lang.Specification

class PactDslJsonBodyMatcherSpec extends Specification {

  private PactDslJsonBody subject

  def setup() {
      subject = new PactDslJsonBody()
  }

  def 'String Matcher Throws Exception If The Example Does Not Match The Pattern'() {
    when:
    subject.stringMatcher('name', '[a-z]+', 'dfhdsjf87fdjh')

    then:
    thrown(InvalidMatcherException)
  }

  def 'Hex Matcher Throws Exception If The Example Is Not A Hexadecimal Value'() {
    when:
    subject.hexValue('name', 'dfhdsjf87fdjh')

    then:
    thrown(InvalidMatcherException)
  }

  def 'Uuid Matcher Throws Exception If The Example Is Not An Uuid'() {
    when:
    subject.uuid('name', 'dfhdsjf87fdjh')

    then:
    thrown(InvalidMatcherException)
  }

  def 'each like allows the number of examples to be set'() {
    given:
    subject
      .eachLike('data', 2)
        .date('defDate')
        .decimalType('cost')
        .closeObject()
      .closeArray()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.data.size == 2
    result.data.every { it.keySet() == ['defDate', 'cost'] as Set }
  }

  def 'min like allows the number of examples to be set'() {
    given:
    subject = new PactDslJsonBody()
      .minArrayLike('data', 1, 2)
        .date('defDate')
        .decimalType('cost')
        .closeObject()
      .closeArray()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.data.size == 2
    result.data.every { it.keySet() == ['defDate', 'cost'] as Set }
  }

  def 'max like allows the number of examples to be set'() {
    given:
    subject = new PactDslJsonBody()
      .maxArrayLike('data', 10, 2)
        .date('defDate')
        .decimalType('cost')
        .closeObject()
      .closeArray()

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())

    then:
    result.data.size == 2
    result.data.every { it.keySet() == ['defDate', 'cost'] as Set }
  }

  def 'each like allows examples that are not objects'() {
    given:
    subject = new PactDslJsonBody()
      .stringType('preference')
      .stringType('subscriptionId')
      .eachLike('types', PactDslJsonRootValue.stringType('abc'), 2)

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())
    def keys = ['preference', 'subscriptionId', 'types'] as Set

    then:
    result.size() == 3
    result.keySet() == keys
    result.types == ['abc', 'abc']
    subject.matchers.matchingRules == [
      '.types': new MatchingRuleGroup([new MinTypeMatcher(0)]),
      '.subscriptionId': new MatchingRuleGroup([TypeMatcher.INSTANCE]),
      '.types[*]': new MatchingRuleGroup([TypeMatcher.INSTANCE]),
      '.preference': new MatchingRuleGroup([TypeMatcher.INSTANCE])
    ]
  }

  def 'min like allows examples that are not objects'() {
    given:
    subject = new PactDslJsonBody()
      .stringType('preference')
      .stringType('subscriptionId')
      .minArrayLike('types', 2, PactDslJsonRootValue.stringType('abc'), 2)

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())
    def keys = ['preference', 'subscriptionId', 'types'] as Set

    then:
    result.size() == 3
    result.keySet() == keys
    result.types == ['abc', 'abc']
    subject.matchers.matchingRules == [
      '.types': new MatchingRuleGroup([new MinTypeMatcher(2)]),
      '.subscriptionId': new MatchingRuleGroup([TypeMatcher.INSTANCE]),
      '.types[*]': new MatchingRuleGroup([TypeMatcher.INSTANCE]),
      '.preference': new MatchingRuleGroup([TypeMatcher.INSTANCE])
    ]
  }

  def 'max like allows examples that are not objects'() {
    given:
    subject = new PactDslJsonBody()
      .stringType('preference')
      .stringType('subscriptionId')
      .maxArrayLike('types', 10, PactDslJsonRootValue.stringType('abc'), 2)

    when:
    def result = new JsonSlurper().parseText(subject.body.toString())
    def keys = ['preference', 'subscriptionId', 'types'] as Set

    then:
    result.size() == 3
    result.keySet() == keys
    result.types == ['abc', 'abc']
    subject.matchers.matchingRules == [
      '.types': new MatchingRuleGroup([new MaxTypeMatcher(10)]),
      '.subscriptionId': new MatchingRuleGroup([TypeMatcher.INSTANCE]),
      '.types[*]': new MatchingRuleGroup([TypeMatcher.INSTANCE]),
      '.preference': new MatchingRuleGroup([TypeMatcher.INSTANCE])
    ]
  }

  def 'eachLike with GeoJSON'() {
    given:
    subject = new PactDslJsonBody()
      .stringType('type', 'FeatureCollection')
      .eachLike('features')
        .stringType('type', 'Feature')
        .object('geometry')
          .stringType('type', 'Point')
          .eachArrayLike('coordinates')
            .decimalType(-7.55717)
            .decimalType(49.766896)
            .closeArray()
          .closeArray()
        .closeObject()
        .object('properties')
          .stringType('prop0', 'value0')
        .closeObject()
        .closeObject()
      .closeArray()

    when:
    def bodyJson = subject.body.toString()
    def result = new JsonSlurper().parseText(bodyJson)
    def keys = ['type', 'features'] as Set

    then:
    bodyJson == '{"features":[{"geometry":{"coordinates":[[-7.55717,49.766896]],"type":"Point"},"type":"Feature",' +
      '"properties":{"prop0":"value0"}}],"type":"FeatureCollection"}'
    result.size() == 2
    result.keySet() == keys
    result.features[0].geometry.coordinates[0] == [-7.55717, 49.766896]
    subject.matchers.matchingRules == [
      '.type': new MatchingRuleGroup([TypeMatcher.INSTANCE]),
      '.features': new MatchingRuleGroup([new MinTypeMatcher(0)]),
      '.features[*].type': new MatchingRuleGroup([TypeMatcher.INSTANCE]),
      '.features[*].properties.prop0': new MatchingRuleGroup([TypeMatcher.INSTANCE]),
      '.features[*].geometry.type': new MatchingRuleGroup([TypeMatcher.INSTANCE]),
      '.features[*].geometry.coordinates': new MatchingRuleGroup([new MinTypeMatcher(0)]),
      '.features[*].geometry.coordinates[*][0]': new MatchingRuleGroup([
        new NumberTypeMatcher(NumberTypeMatcher.NumberType.DECIMAL)]),
      '.features[*].geometry.coordinates[*][1]': new MatchingRuleGroup([
        new NumberTypeMatcher(NumberTypeMatcher.NumberType.DECIMAL)])
    ]

  }

  def 'each like generates the correct JSON for arrays of strings'() {
    given:
    subject
      .object('dataStorePathInfo')
        .stringMatcher('basePath', String.format('%s/%s/training-data/[a-z0-9]{20,24}', 'CUSTOMER', 'TRAINING'),
          'CUSTOMER/TRAINING/training-data/12345678901234567890')
        .eachLike('fileNames', PactDslJsonRootValue.stringType('abc.txt'), 1)
      .closeObject()

    when:
    def bodyJson = subject.body.toString()

    then:
    bodyJson == '{"dataStorePathInfo":{"basePath":"CUSTOMER/TRAINING/training-data/12345678901234567890",' +
      '"fileNames":["abc.txt"]}}'
  }

  def 'unorderedMaxArrayLike generates content with correct values and matching rules'() {
    given:
    subject.unorderedMaxArrayLike('array', 3, PactDslJsonRootValue.numberType(5))

    when:
    def result = subject.body.toString()

    then:
    result.toString() == '{"array":[5]}'
    subject.matchers.matchingRules == [
      '.array': new MatchingRuleGroup([new MaxEqualsIgnoreOrderMatcher(3)]),
      '.array[*]': new MatchingRuleGroup([TypeMatcher.INSTANCE])
    ]
  }

  def 'unorderedEachLike generates content with correct values and matching rules'() {
    given:
    def root = new PactDslJsonRootValue()
    root.setValue(100)
    root.setMatcher(EqualsMatcher.INSTANCE)
    subject.unorderedEachLike('array', root, 3)

    when:
    def result = subject.body.toString()

    then:
    result.toString() == '{"array":[100,100,100]}'
    subject.matchers.matchingRules == [
      '.array': new MatchingRuleGroup([EqualsIgnoreOrderMatcher.INSTANCE]),
      '.array[*]': new MatchingRuleGroup([EqualsMatcher.INSTANCE])
    ]
  }

  def 'unorderedEachArrayWithMinLike generates content with correct values and matching rules'() {
    given:
    subject.unorderedEachArrayWithMinLike('array', 3, 3)
      .number(1).number(2).number(3)
    .close()

    when:
    def result = subject.body.toString()

    then:
    result.toString() == '{"array":[[1,2,3],[1,2,3],[1,2,3]]}'
    subject.matchers.matchingRules == [
      '$.array': new MatchingRuleGroup([new MinEqualsIgnoreOrderMatcher(3)])
    ]
  }
}
