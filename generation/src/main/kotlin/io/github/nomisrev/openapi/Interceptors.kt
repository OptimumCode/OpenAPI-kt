package io.github.nomisrev.openapi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.withIndent
import io.github.nomisrev.openapi.NamingContext.Named
import io.ktor.http.*
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Head
import io.ktor.http.HttpMethod.Companion.Options
import io.ktor.http.HttpMethod.Companion.Patch
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put

interface APIInterceptor {
  fun OpenAPIContext.intercept(api: API): API

  fun OpenAPIContext.modifyInterface(api: API, typeSpec: TypeSpec.Builder): TypeSpec.Builder

  /**
   * It's valid to discard the original typeSpec, and produce a new one.
   */
  fun OpenAPIContext.modifyImplementation(api: API, typeSpec: TypeSpec.Builder): TypeSpec.Builder

  companion object {
    val NoOp: APIInterceptor = object : APIInterceptor {
      override fun OpenAPIContext.intercept(api: API): API = api
      override fun OpenAPIContext.modifyInterface(api: API, typeSpec: TypeSpec.Builder): TypeSpec.Builder = typeSpec
      override fun OpenAPIContext.modifyImplementation(
        api: API, typeSpec: TypeSpec.Builder
      ): TypeSpec.Builder = typeSpec
    }
  }
}

fun HttpMethod.name(): String =
  when (value) {
    Get.value -> "Get"
    Post.value -> "Post"
    Put.value -> "Put"
    Patch.value -> "Patch"
    Delete.value -> "Delete"
    Head.value -> "Head"
    Options.value -> "Options"
    else -> TODO("Custom HttpMethod not yet supported")
  }