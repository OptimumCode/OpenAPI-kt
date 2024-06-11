package io.github.nomisrev.openapi

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.withIndent
import io.github.nomisrev.openapi.Model.Collection
import io.github.nomisrev.openapi.NamingContext.Named
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PolymorphicKind

fun Iterable<Model>.toFileSpecs(): List<FileSpec> =
  mapNotNull { it.toFileSpec() }

private val `package` = "io.github.nomisrev.openapi"

fun Model.toFileSpec(): FileSpec? =
  when (this) {
    is Collection -> inner.value.toFileSpec()
    is Model.Enum ->
      FileSpec.builder(`package`, Nam.toClassName(context).simpleName)
        .addType(toTypeSpec())
        .build()

    is Model.Object ->
      FileSpec.builder(`package`, Nam.toClassName(context).simpleName)
        .addType(toTypeSpec())
        .build()

    is Model.Union ->
      FileSpec.builder(`package`, Nam.toClassName(context).simpleName)
        .addType(toTypeSpec())
        .build()

    Model.Binary,
    is Model.Primitive,
    Model.FreeFormJson -> null
  }

tailrec fun Model.toTypeSpec(): TypeSpec? =
  when (this) {
    is Model.Binary,
    is Model.FreeFormJson,
    is Model.Primitive -> null

    is Collection -> inner.value.toTypeSpec()
    is Model.Enum -> toTypeSpec()
    is Model.Object -> toTypeSpec()
    is Model.Union -> toTypeSpec()
  }


val SerialDescriptor =
  ClassName("kotlinx.serialization.descriptors", "SerialDescriptor")

@OptIn(ExperimentalSerializationApi::class)
fun Model.Union.toTypeSpec(): TypeSpec =
  TypeSpec.interfaceBuilder(Nam.toClassName(context))
    .addModifiers(KModifier.SEALED)
    .addAnnotation(annotationSpec<Serializable>())
    .addTypes(
      cases.map { case ->
        val model = case.model
        TypeSpec.classBuilder(Nam.toCaseClassName(this@toTypeSpec, case.model.value).simpleName)
          .addModifiers(KModifier.VALUE)
          .addAnnotation(annotationSpec<JvmInline>())
          .primaryConstructor(
            FunSpec.constructorBuilder()
              .addParameter(ParameterSpec.builder("value", model.toTypeName()).build())
              .build()
          )
          .addProperty(
            PropertySpec.builder("value", model.toTypeName()).initializer("value").build()
          )
          .addSuperinterface(Nam.toClassName(context))
          .build()
      }
    )
    .addTypes(inline.mapNotNull { it.toTypeSpec() })
    .addType(
      TypeSpec.objectBuilder("Serializer")
        .addSuperinterface(KSerializer.parameterizedBy(Nam.toClassName(context)))
        .addProperty(
          PropertySpec.builder("descriptor", SerialDescriptor)
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(
              AnnotationSpec.builder(ClassName("kotlinx.serialization", "InternalSerializationApi"))
                .build()
            )
            .initializer(
              CodeBlock.builder()
                .add(
                  "%M(%S, %T.SEALED) {\n",
                  MemberName("kotlinx.serialization.descriptors", "buildSerialDescriptor"),
                  Nam.toClassName(context)
                    .simpleNames.joinToString("."),
                  PolymorphicKind::class
                )
                .withIndent {
                  cases.forEach { case ->
                    val (placeholder, values) = case.model.serializer()
                    add(
                      "element(%S, $placeholder.descriptor)\n",
                      Nam.toCaseClassName(this@toTypeSpec, case.model.value)
                        .simpleNames.joinToString("."),
                      *values
                    )
                  }
                }
                .add("}\n")
                .build()
            )
            .build()
        )
        .addFunction(
          FunSpec.builder("serialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("encoder", ClassName("kotlinx.serialization.encoding", "Encoder"))
            .addParameter("value", Nam.toClassName(context))
            .addCode(
              CodeBlock.builder()
                .add("when(value) {\n")
                .apply {
                  cases.forEach { case ->
                    val (placeholder, values) = case.model.serializer()
                    addStatement(
                      "is %T -> encoder.encodeSerializableValue($placeholder, value.value)",
                      Nam.toCaseClassName(this@toTypeSpec, case.model.value),
                      *values
                    )
                  }
                }
                .add("}\n")
                .build()
            )
            .build()
        )
        .addFunction(
          FunSpec.builder("deserialize")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("decoder", ClassName("kotlinx.serialization.encoding", "Decoder"))
            .returns(Nam.toClassName(context))
            .addCode(
              CodeBlock.builder()
                .add(
                  "val json = decoder.decodeSerializableValue(%T.serializer())\n",
                  ClassName("kotlinx.serialization.json", "JsonElement")
                )
                .add("return attemptDeserialize(json,\n")
                .apply {
                  cases.forEach { case ->
                    val (placeholder, values) = case.model.serializer()
                    add(
                      "Pair(%T::class) { %T(%T.decodeFromJsonElement($placeholder, json)) },\n",
                      Nam.toCaseClassName(this@toTypeSpec, case.model.value),
                      Nam.toCaseClassName(this@toTypeSpec, case.model.value),
                      ClassName("kotlinx.serialization.json", "Json"),
                      *values
                    )
                  }
                }
                .add(")\n")
                .build()
            )
            .build()
        )
        .build()
    )
    .build()

private inline fun <reified A : Annotation> annotationSpec(): AnnotationSpec =
  AnnotationSpec.builder(A::class).build()

private fun serialName(rawName: String): AnnotationSpec =
  annotationSpec<SerialName>().toBuilder().addMember("\"$rawName\"").build()

fun List<ParameterSpec>.sorted(): List<ParameterSpec> {
  val (required, optional) = partition { it.defaultValue == null }
  return required + optional
}

/*
 * Generating data classes with KotlinPoet!?
 * https://stackoverflow.com/questions/44483831/generate-data-class-with-kotlinpoet
 */
fun Model.Object.toTypeSpec(): TypeSpec =
  TypeSpec.classBuilder(Nam.toClassName(context))
    .addModifiers(KModifier.DATA)
    // We cannot serialize files, these are used for multipart requests, but we cannot check at this
    // point...
    // This occurs when request bodies are defined using top-level schemas.
    .apply {
      if (properties.none { it.model.value is Model.Binary })
        addAnnotation(annotationSpec<Serializable>())
    }
    .primaryConstructor(
      FunSpec.constructorBuilder()
        .addParameters(
          properties
            .map { prop ->
              val default = prop.model.default()
              val isRequired = prop.isRequired && default != null
              ParameterSpec.builder(
                Nam.toPropName(context, prop),
                prop.model.toTypeName().copy(nullable = prop.isNullable)
              )
                .apply {
                  default?.let { defaultValue(it) }
                  if (isRequired) addAnnotation(annotationSpec<Required>())
                }
                .build()
            }
            .sorted()
        )
        .build()
    )
    .addProperties(
      properties.map { prop ->
        PropertySpec.builder(
          Nam.toPropName(context, prop),
          prop.model.toTypeName().copy(nullable = prop.isNullable)
        )
          .initializer(Nam.toPropName(context, prop))
          .build()
      }
    )
    .addTypes(inline.mapNotNull { it.toTypeSpec() })
    .build()

fun Resolved<Model>.toTypeName(): TypeName =
  value.toTypeName()

fun Model.toTypeName(): TypeName =
  when (this) {
    is Model.Primitive.Boolean -> BOOLEAN
    is Model.Primitive.Double -> DOUBLE
    is Model.Primitive.Int -> INT
    is Model.Primitive.String -> STRING
    Model.Primitive.Unit -> UNIT
    is Collection.List -> LIST.parameterizedBy(inner.toTypeName())
    is Collection.Set -> SET.parameterizedBy(inner.toTypeName())
    is Collection.Map -> MAP.parameterizedBy(STRING, inner.toTypeName())
    Model.Binary -> ClassName(`package`, "UploadFile")
    Model.FreeFormJson -> ClassName("kotlinx.serialization.json", "JsonElement")
    is Model.Enum -> Nam.toClassName(context)
    is Model.Object -> Nam.toClassName(context)
    is Model.Union -> Nam.toClassName(context)
  }

fun Model.Enum.toTypeSpec(): TypeSpec =
  when (this) {
    is Model.Enum.Closed -> toTypeSpec()
    is Model.Enum.Open -> toTypeSpec()
  }

fun Model.Enum.Closed.toTypeSpec(): TypeSpec {
  val rawToName = values.map { rawName -> Pair(rawName, Nam.toEnumValueName(rawName)) }
  val isSimple = rawToName.all { (rawName, valueName) -> rawName == valueName }
  val enumName = Nam.toClassName(context)
  return TypeSpec.enumBuilder(enumName)
    .apply {
      if (!isSimple)
        primaryConstructor(FunSpec.constructorBuilder().addParameter("value", STRING).build())
      rawToName.forEach { (rawName, valueName) ->
        if (isSimple) addEnumConstant(rawName)
        else
          addEnumConstant(
            valueName,
            TypeSpec.anonymousClassBuilder()
              .addAnnotation(serialName(rawName))
              .addSuperclassConstructorParameter("\"$rawName\"")
              .build()
          )
      }
    }
    .addAnnotation(annotationSpec<Serializable>())
    .build()
}

val KSerializer =
  ClassName("kotlinx.serialization", "KSerializer")

fun Model.Enum.Open.toTypeSpec(): TypeSpec {
  val rawToName = values.map { rawName -> Pair(rawName, Nam.toEnumValueName(rawName)) }
  val enumName = Nam.toClassName(context)
  return TypeSpec.interfaceBuilder(enumName)
    .addModifiers(KModifier.SEALED)
    .addProperty(PropertySpec.builder("value", STRING).addModifiers(KModifier.ABSTRACT).build())
    .addAnnotation(annotationSpec<Serializable>())
    .addType(
      TypeSpec.classBuilder("Custom")
        .addModifiers(KModifier.VALUE)
        .addAnnotation(annotationSpec<JvmInline>())
        .primaryConstructor(FunSpec.constructorBuilder().addParameter("value", STRING).build())
        .addProperty(
          PropertySpec.builder("value", STRING)
            .initializer("value")
            .addModifiers(KModifier.OVERRIDE)
            .build()
        )
        .addSuperinterface(Nam.toClassName(context))
        .build()
    )
    .addTypes(
      rawToName.map { (rawName, valueName) ->
        TypeSpec.objectBuilder(valueName)
          .addModifiers(KModifier.DATA)
          .addSuperinterface(Nam.toClassName(context))
          .addProperty(
            PropertySpec.builder("value", STRING)
              .initializer("\"$rawName\"")
              .addModifiers(KModifier.OVERRIDE)
              .build()
          )
          .build()
      }
    )
    .addType(
      TypeSpec.companionObjectBuilder()
        .addProperty(
          PropertySpec.builder("defined", LIST.parameterizedBy(Nam.toClassName(context)))
            .initializer(
              CodeBlock.builder()
                .add("listOf(")
                .apply {
                  rawToName.forEachIndexed { index, (_, valueName) ->
                    add(valueName)
                    if (index < rawToName.size - 1) add(", ")
                  }
                  add(")")
                }
                .build()
            )
            .build()
        )
        .addType(
          TypeSpec.objectBuilder("Serializer")
            .addSuperinterface(KSerializer.parameterizedBy(Nam.toClassName(context)))
            .addProperty(
              PropertySpec.builder("descriptor", SerialDescriptor)
                .addModifiers(KModifier.OVERRIDE)
                .addAnnotation(
                  AnnotationSpec.builder(
                    ClassName("kotlinx.serialization", "InternalSerializationApi")
                  )
                    .build()
                )
                .initializer(
                  CodeBlock.builder()
                    .addStatement(
                      "%M(%S, %T.STRING)",
                      MemberName("kotlinx.serialization.descriptors", "PrimitiveSerialDescriptor"),
                      enumName,
                      ClassName("kotlinx.serialization.descriptors", "PrimitiveKind")
                    )
                    .build()
                )
                .build()
            )
            .addFunction(
              FunSpec.builder("serialize")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("encoder", ClassName("kotlinx.serialization.encoding", "Encoder"))
                .addParameter("value", Nam.toClassName(context))
                .addCode(
                  CodeBlock.builder().addStatement("encoder.encodeString(value.value)").build()
                )
                .build()
            )
            .addFunction(
              FunSpec.builder("deserialize")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("decoder", ClassName("kotlinx.serialization.encoding", "Decoder"))
                .returns(Nam.toClassName(context))
                .addCode(
                  CodeBlock.builder()
                    .addStatement("val value = decoder.decodeString()")
                    .addStatement("return attemptDeserialize(value,")
                    .withIndent {
                      rawToName.forEach { (_, name) ->
                        val nested = NamingContext.Nested(Named(name), context)
                        addStatement(
                          "Pair(%T::class) { defined.find { it.value == value } },",
                          Nam.toClassName(nested)
                        )
                      }
                      addStatement("Pair(Custom::class) { Custom(value) }")
                    }
                    .addStatement(")")
                    .build()
                )
                .build()
            )
            .build()
        )
        .build()
    )
    .build()
}

private val ListSerializer = MemberName("kotlinx.serialization.builtins", "ListSerializer")

private val SetSerializer = MemberName("kotlinx.serialization.builtins", "SetSerializer")

private val MapSerializer = MemberName("kotlinx.serialization.builtins", "MapSerializer")

private fun Resolved<Model>.serializer(): Pair<String, Array<Any>> =
  with(value) {
    val values: MutableList<Any> = mutableListOf()
    fun Model.placeholder(): String =
      when (this) {
        is Collection.List -> {
          values.add(ListSerializer)
          "%M(${inner.value.placeholder()})"
        }

        is Collection.Map -> {
          values.add(MapSerializer)
          "%M(${key.placeholder()}, ${inner.value.placeholder()})"
        }

        is Collection.Set -> {
          values.add(SetSerializer)
          "%M(${inner.value.placeholder()})"
        }

        is Model.Primitive -> {
          values.add(toTypeName())
          values.add(MemberName("kotlinx.serialization.builtins", "serializer", isExtension = true))
          "%T.%M()"
        }

        else -> {
          values.add(toTypeName())
          "%T.serializer()"
        }
      }

    return Pair(placeholder(), values.toTypedArray())
  }
