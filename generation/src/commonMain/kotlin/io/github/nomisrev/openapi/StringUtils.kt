package io.github.nomisrev.openapi

internal fun String.toPascalCase(): String {
  val words = split("_", "-", ".")
  return when (words.size) {
    1 -> words[0].capitalize()
    else -> buildString {
      append(words[0].capitalize())
      for (i in 1 until words.size) {
        append(words[i].capitalize())
      }
    }
  }
}

internal fun String.toCamelCase(): String {
  val words = replace("[]", "").split("_")
  return when (words.size) {
    1 -> words[0]
    else -> buildString {
      append(words[0].decapitalize())
      for (i in 1 until words.size) {
        append(words[i].capitalize())
      }
    }
  }
}

internal fun String.decapitalize(): String =
  replaceFirstChar { it.lowercase() }

internal fun String.capitalize(): String =
  replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private val classNameRegex = Regex("^[a-zA-Z_$][a-zA-Z\\d_$]*$")

internal fun String.isValidClassname(): Boolean =
  classNameRegex.matches(this)

internal fun String.sanitize(delimiter: String = ".", prefix: String = ""): String =
  splitToSequence(delimiter)
    .joinToString(delimiter, prefix) { if (it in KOTLIN_KEYWORDS) "`$it`" else it }

// This list only contains words that need to be escaped.
private val KOTLIN_KEYWORDS = setOf(
  "as",
  "break",
  "class",
  "continue",
  "do",
  "else",
  "false",
  "for",
  "fun",
  "if",
  "in",
  "interface",
  "is",
  "null",
  "object",
  "package",
  "return",
  "super",
  "this",
  "throw",
  "true",
  "try",
  "typealias",
  "typeof",
  "val",
  "var",
  "when",
  "while",
)