package au.com.dius.pact.consumer.dsl

import groovy.xml.XmlSlurper
import spock.lang.Specification

import static au.com.dius.pact.consumer.dsl.Matchers.integer
import static au.com.dius.pact.consumer.dsl.Matchers.string

class PactDslXmlSpec extends Specification {
  def 'without a namespace'() {
    given:
    def body = PactDslXml.document('projects') {
      it.attribute('id', '1234')
        .eachLike('project', 2) {
          it.attributes([
            id: integer(12),
            type: 'activity',
            name: string(' Project 1 ')
          ])
        }
    }

    when:
    def result = new XmlSlurper().parseText(body.toString())

    then:
    result.@id == '1234'
    result.project.size() == 2
    result.project.each {
      assert it.@id == '12'
      assert it.@name == ' Project 1 '
      assert it.@type == 'activity'
    }
  }

  def 'elements with mutiple different types'() {
    given:
    def body = PactDslXml.document('animals') {
      it.eachLike('dog', 2) {
        it.attributes([
          id: integer(1),
          name: string('Canine')
        ])
      }.eachLike('cat', 3) {
        it.attributes([
          id: integer(2),
          name: string('Feline')
        ])
      }.eachLike('wolf', 1) {
        it.attributes([
          id: integer(3),
          name: string('Canine')
        ])
      }
    }

    when:
    def result = new XmlSlurper().parseText(body.toString())

    then:
    result.dog.size() == 2
    result.cat.size() == 3
    result.wolf.size() == 1
  }
}
