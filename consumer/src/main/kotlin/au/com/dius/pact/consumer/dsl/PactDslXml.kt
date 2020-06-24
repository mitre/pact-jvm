package au.com.dius.pact.consumer.dsl

import au.com.dius.pact.core.model.generators.Category.BODY
import au.com.dius.pact.core.model.generators.Generators
import au.com.dius.pact.core.model.matchingrules.Category
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.util.function.Consumer
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class PactDslXml private constructor(
  private val element: Element,
  private val parent: PactDslXml? = null,
  private val path: List<String> = listOf("$", element.tagName)
) : DslContent {
  private val matchers: Category
  private val generators: Generators
  private val tagCounts = mutableMapOf<String, Int>()

  init {
    if (parent != null) {
      matchers = parent.matchers
      generators = parent.generators
    } else {
      matchers = Category("body")
      generators = Generators()
    }
  }
  companion object {
    @JvmOverloads
    @JvmStatic
    fun document(tagName: String, element: Consumer<PactDslXml>? = null): PactDslXml {
      val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
      val body = doc.createElement(tagName)
      doc.appendChild(body)
      val root = PactDslXml(body)
      element?.accept(root)
      return root
    }
  }

  fun defaultNamespace(uri: String): PactDslXml {
    element.setAttribute("xmlns", uri)
    return this
  }

  fun namespace(prefix: String, uri: String): PactDslXml {
    element.setAttribute("xmlns:$prefix", uri)
    return this
  }

  private fun matcherKey(path: List<String>, vararg key: String) = (path + key).joinToString(".")

  fun attribute(name: String, value: Any): PactDslXml {
    if (value is Matcher) {
      val key = matcherKey(path, "@$name")
      if (value.matcher != null) {
        matchers.addRule(key, value.matcher!!)
      }
      if (value.generator != null) {
        generators.addGenerator(BODY, key, value.generator!!)
      }
      element.setAttribute(name, value.value.toString())
    } else {
      element.setAttribute(name, value.toString())
    }
    return this
  }

  fun attributes(attributes: Map<String, Any>): PactDslXml {
    attributes.forEach { (name, value) ->
      attribute(name, value)
    }
    return this
  }

  fun text(text: Any): PactDslXml {
    if (text is Matcher) {
      val key = matcherKey(path, "#text")
      if (text.matcher != null) {
        matchers.addRule(key, text.matcher!!)
      }
      if (text.generator != null) {
        generators.addGenerator(BODY, key, text.generator!!)
      }
      element.textContent = text.value.toString()
    } else {
      element.textContent = text.toString()
    }
    return this
  }

  private fun count(tagName: String, number: Int = 1) =
    tagCounts.compute(tagName) { _, count -> (count ?: -1) + number }

  @JvmOverloads
  fun element(tagName: String, element: Consumer<PactDslXml>? = null): PactDslXml {
    val index = count(tagName)
    val childElement = this.element.ownerDocument.createElement(tagName)
    val child = PactDslXml(childElement, this, (path + tagName + index.toString()))
    this.element.appendChild(child.element)
    element?.accept(child)
    return this
  }

  fun element(tagName: String, text: Any): PactDslXml {
    return element(tagName) {
      text(text)
    }
  }

  @JvmOverloads
  fun eachLike(tagName: String, examples: Int, element: Consumer<PactDslXml>? = null): PactDslXml {
    count(tagName, examples)
    val childElement = this.element.ownerDocument.createElement(tagName)
    val child = PactDslXml(childElement, this, (path + tagName + "*"))
    this.element.appendChild(child.element)
    element?.accept(child)

    (2..examples).forEach { _ -> this.element.appendChild(child.element.cloneNode(true)) }
    return this
  }

  override fun getMatchers() = matchers

  override fun getGenerators() = generators

  override fun toBytes(charset: Charset): ByteArray {
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    val source = DOMSource(element)
    val outputStream = ByteArrayOutputStream()
    val result = StreamResult(OutputStreamWriter(outputStream, charset))
    transformer.transform(source, result)
    return outputStream.toByteArray()
  }

  override fun toString() = String(toBytes())

  override fun close(): PactDslXml {
    var node = this
    while (node.parent != null) {
      node = node.parent!!
    }
    return node
  }
}
