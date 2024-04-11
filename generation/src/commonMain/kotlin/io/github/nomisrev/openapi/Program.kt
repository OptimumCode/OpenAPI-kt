package io.github.nomisrev.openapi

import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import okio.use

private fun BufferedSink.writeUtf8Line(line: String) {
  writeUtf8("$line\n")
}

private fun BufferedSink.writeUtf8Line() {
  writeUtf8("\n")
}

public fun FileSystem.program(
  pathSpec: String,
  `package`: String = "io.github.nomisrev.openapi",
  modelPackage: String = "$`package`.models",
  generationPath: String =
    "build/generated/openapi/src/commonMain/kotlin/${`package`.replace(".", "/")}"
) {
  deleteRecursively(generationPath.toPath())
  fun file(name: String, imports: Set<Model.Import>, code: String) {
    write("$generationPath/models/$name.kt".toPath()) {
      writeUtf8Line("package $modelPackage")
      writeUtf8Line()
      if (imports.isNotEmpty()) {
        writeUtf8Line(imports.joinToString("\n") { "import ${it.`package`}.${it.typeName}" })
        writeUtf8Line()
      }
      writeUtf8Line(code)
    }
  }

  createDirectories("$generationPath/models".toPath())
//  file(
//    "predef",
//    setOf(
//      Model.Import("kotlin.reflect", "KClass"),
//      Model.Import("kotlinx.serialization", "SerializationException"),
//      Model.Import("kotlinx.serialization.json", "JsonElement"),
//    ),
//    predef
//  )

  val rawSpec = source(pathSpec.toPath()).buffer().use(BufferedSource::readUtf8)
  val openAPI = OpenAPI.fromJson(rawSpec)

  val models = openAPI.models()
  println(models.joinToString(separator = "\n"))
  models.forEach { model ->
    file(model.typeName, model.imports(), model.toKotlinCode(0))
  }
}
