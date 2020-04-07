package au.com.dius.pact.core.matchers.util

fun tails(col: List<String>): List<List<String>> {
  val result = mutableListOf<List<String>>()
  var acc = col
  while (acc.isNotEmpty()) {
    result.add(acc)
    acc = acc.drop(1)
  }
  result.add(acc)
  return result
}

fun <A, B> corresponds(l1: List<A>, l2: List<B>, fn: (a: A, b: B) -> Boolean): Boolean {
  return if (l1.size == l2.size) {
    l1.zip(l2).all { fn(it.first, it.second) }
  } else {
    false
  }
}

fun <E> List<E>.padTo(size: Int, item: E): List<E> {
  return if (size < this.size) {
    this.dropLast(this.size - size)
  } else {
    val list = this.toMutableList()
    for (i in this.size.until(size)) {
      list.add(item)
    }
    return list
  }
}

/**
 * Returns a map of the list provided where the keys are the elements of
 * the list and the values are the number of instances each element occurs
 * [100, 100, 200, 200, 300] => {100=2, 200=2, 300=1}
 */
fun instanceMapOf(list: List<Any?>): HashMap<Any?, Int> {
  var result = HashMap<Any?, Int>()
  println(list)
  list.forEach {
    result[it] = result.getOrDefault(it, 0) + 1
  }
  return result
}

/**
 * Returns true if all keys of expected represented in actual and
 * and all values less than or equally represented, otherwise false
 */
fun subsetOf(expected: HashMap<Any?, Int>, actual: HashMap<Any?, Int>): Boolean {
  return expected.all { (k, _) -> actual.containsKey(k) } &&
          expected.all { (k, v) -> actual.getValue(k) >= v }
}