package io.github.nomisrev.openapi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.github.nomisrev.openapi.NamingContext.Named
import io.github.nomisrev.openapi.generation.DefaultNamingStrategy
import io.github.nomisrev.openapi.generation.NamingStrategy

fun apis(root: Root, naming: NamingStrategy): List<FileSpec> =
  endpoints(root, naming) + root(root, naming)

private fun endpoints(
  root: Root,
  naming: NamingStrategy
): List<FileSpec> = root.endpoints.map { api ->
  FileSpec.builder("io.github.nomisrev.openapi", api.name)
    .addType(api.toCode(naming))
    .build()
}

private fun root(
  root: Root,
  naming: NamingStrategy
) = FileSpec.builder("io.github.nomisrev.openapi", "OpenAPI")
  .addType(
    TypeSpec.interfaceBuilder("OpenAPI")
      .apply {
        root.endpoints.forEach { api ->
          val className = naming.toObjectClassName(Named(api.name))
          val name = naming.toFunctionName(Named(api.name))
          addProperty(
            PropertySpec.builder(
              name,
              ClassName.bestGuess("io.github.nomisrev.openapi.$className")
            )
              .build()
          )
        }
      }
      .build()
  )
  .build()

private fun TypeSpec.Builder.addProperty(api: API, naming: NamingStrategy) {
  val className = naming.toObjectClassName(Named(api.name))
  addProperty(api.name, ClassName.bestGuess(className))
}

private fun API.toCode(naming: NamingStrategy): TypeSpec =
  TypeSpec.interfaceBuilder(naming.toObjectClassName(Named(name)))
    .addFunctions(routes.map { it.toFun(naming).abstract() })
    .apply {
      nested.forEach { api ->
        addType(api.toCode(naming))
        addProperty(api, naming)
      }
    }
    .build()

private fun FunSpec.abstract(): FunSpec = toBuilder().addModifiers(KModifier.ABSTRACT).build()

fun FunSpec.Builder.addParameter(
  naming: NamingStrategy,
  name: String,
  type: Model,
  nullable: Boolean,
): FunSpec.Builder =
  addParameter(
    ParameterSpec.builder(name, type.toTypeName(naming).copy(nullable = nullable))
      .apply {
        if (nullable) defaultValue("null")
      }
      .build()
  )

private fun Route.toFun(naming: NamingStrategy): FunSpec =
  FunSpec.builder(naming.toFunctionName(Named(operation.operationId!!)))
    .apply {
      // TODO support binary, and Xml
      body.jsonOrNull()?.let { json ->
        addParameter(naming, "body", json.type, !body.required)
      } ?: body.multipartOrNull()?.let { multipart ->
        multipart.parameters.forEach { parameter ->
          addParameter(
            naming,
            naming.toFunctionName(Named(parameter.name)),
            parameter.type,
            !body.required
          )
        }
      }

      // TODO isRequired
      input.forEach { input ->
        addParameter(
          naming.toFunctionName(Named(input.name)),
          input.type.toTypeName(naming)
            .copy(nullable = input.isNullable)
        )
      }
    }
    .returns(returnType(naming))
    .build()

// TODO generate an ADT to properly support all return types
private fun Route.returnType(naming: NamingStrategy): TypeName {
  val success =
    returnType.types.toSortedMap { s1, s2 -> s1.code.compareTo(s2.code) }.entries.first()
  return when (success.value.type) {
    is Model.Binary -> ClassName("io.ktor.client.statement", "HttpResponse")
    else -> success.value.type.toTypeName(naming)
  }
}
