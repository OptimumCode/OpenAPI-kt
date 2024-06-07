package io.github.nomisrev.generic

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.short
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.serialization.Serializable

@Serializable data class Id<A>(val value: A)

@Serializable data class Person(val name: String, val age: Int, val p: Person2? = null)

@Serializable data class Person2(val name: String, val age: Int, val p: Person2? = null)

class ProductSpec :
  StringSpec({
    "Pair" {
      checkAll(Arb.int(), Arb.string()) { a, b ->
        Generic.encode(Pair(a, b)) shouldBe pair(Generic.Number.Int(a), Generic.String(b))
      }
    }

    "Nested Pair" {
      checkAll(Arb.int(), Arb.string(), Arb.float()) { a, b, c ->
        Generic.encode(Pair(a, Pair(b, c))) shouldBe
          pair(Generic.Number.Int(a), pair(Generic.String(b), Generic.Number.Float(c)))
      }
    }

    "Person" {
      val res =
        Generic.encode(Person(name = "X", age = 98, p = Person2(name = "Y", age = 99, p = null)))
      val expected = person("X", 98, Person2("Y", 99))
      res shouldBe expected
    }

    "Serializable Person, without Module Config" {
      val res =
        Generic.encode(
          Id(Person(name = "X", age = 98, p = Person2(name = "Y", age = 99, p = null)))
        )
      val expected = person("X", 98, Person2("Y", 99)).id()
      res shouldBe expected
    }

    testIdProduct(Arb.boolean()) { Generic.Boolean(it) }
    testIdProduct(Arb.string()) { Generic.String(it) }
    testIdProduct(Arb.char()) { Generic.Char(it) }
    testIdProduct(Arb.byte()) { Generic.Number.Byte(it) }
    testIdProduct(Arb.short()) { Generic.Number.Short(it) }
    testIdProduct(Arb.int()) { Generic.Number.Int(it) }
    testIdProduct(Arb.long()) { Generic.Number.Long(it) }
    testIdProduct(Arb.float()) { Generic.Number.Float(it) }
    testIdProduct(Arb.double()) { Generic.Number.Double(it) }
  })

fun Generic.id(): Generic.Product =
  Generic.Product(Generic.Info(Id::class.qualifiedName!!), listOf("value" to this@id))

inline fun <reified A> StringSpec.testIdProduct(
  arb: Arb<A>,
  noinline expected: (A) -> Generic
): Unit = "Id - ${A::class.qualifiedName!!}" {
  checkAll(arb.map(::Id)) { id ->
    Generic.encode(id, serializersModule = serializersModule) shouldBe expected(id.value).id()
  }
}

fun pair(first: Generic, second: Generic): Generic =
  Generic.Product(Generic.Info("kotlin.Pair"), listOf("first" to first, "second" to second))

fun person(name: String, age: Int, p: Person2? = null): Generic =
  Generic.Product(
    Generic.Info(Person::class.qualifiedName!!),
    listOfNotNull(
      "name" to Generic.String(name),
      "age" to Generic.Number.Int(age),
      "p" to if (p == null) Generic.Null else person2(p.name, p.age, p.p)
    )
  )

fun person2(name: String, age: Int, p: Person2? = null): Generic =
  Generic.Product(
    Generic.Info(Person2::class.qualifiedName!!),
    listOfNotNull(
      "name" to Generic.String(name),
      "age" to Generic.Number.Int(age),
      "p" to if (p == null) Generic.Null else person2(p.name, p.age, p.p)
    )
  )
