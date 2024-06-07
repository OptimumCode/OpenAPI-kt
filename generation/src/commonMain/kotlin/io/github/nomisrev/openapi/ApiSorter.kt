package io.github.nomisrev.openapi

fun interface ApiSorter {
  suspend fun sort(routes: Iterable<Route>): Root

  companion object {
    val ByPath: ApiSorter = ByPathApiSorter
  }
}

/**
 * ADT that models how to generate the API. Our OpenAPI document dictates the structure of the API,
 * so all operations are available as their path, with operationId. i.e. for `OpenAI`
 * `/chat/completions` with operationId `createChatCompletion`.
 *
 * interface OpenAI { val chat: Chat } interface Chat { val completions: Completions } interface
 * Completions { fun createChatCompletion(...): CreateChatCompletionResponse }
 *
 * // openAI.chat.completions.createChatCompletion(...)
 */
data class Root(
  /* `info.title`, or custom name */
  val name: String,
  val endpoints: List<API>
)

data class API(val name: String, val routes: List<Route>, val nested: List<API>)

private data class RootBuilder(
  val name: String,
  val operations: MutableList<Route>,
  val nested: MutableList<APIBuilder>
) {
  fun build(): Root = Root(name, nested.map { it.build() })
}

private data class APIBuilder(
  val name: String,
  val routes: MutableList<Route>,
  val nested: MutableList<APIBuilder>
) {
  fun build(): API = API(name, routes, nested.map { it.build() })
}

private object ByPathApiSorter : ApiSorter {
  override suspend fun sort(routes: Iterable<Route>): Root {
    val root = RootBuilder("OpenAPI", mutableListOf(), mutableListOf())
    routes.forEach { route ->
      // For now we reduce paths like `/file/{file_id}/content` to `file`
      val normalised =
        route.path.takeWhile { it != '{' }.dropLastWhile { it == '/' }.dropWhile { it == '/' }

      // We split /chat/completions into ["chat", "completions"]
      val parts = normalised.split("/").filter { it.isNotEmpty() }
      val first =
        parts.getOrNull(0)
          ?: run {
            // Root operation
            root.operations.add(route)
            return@forEach
          }
      // We need to find `chat` in root.operations, and find completions in chat.nested
      val api =
        root.nested.firstOrNull { it.name == first }
          ?: run {
            val new = APIBuilder(first, mutableListOf(), mutableListOf())
            root.nested.add(new)
            new
          }

      val remaining = parts.drop(1)
      if (remaining.isEmpty()) Unit else addRoute(api, parts.drop(1), route)
    }
    return root.build()
  }

  private fun addRoute(builder: APIBuilder, parts: List<String>, route: Route) {
    if (parts.isEmpty()) builder.routes.add(route)
    else {
      val part = parts[0]
      val api = builder.nested.firstOrNull { it.name == part }
      if (api == null) {
        val new = APIBuilder(part, mutableListOf(route), mutableListOf())
        builder.nested.add(new)
      } else addRoute(api, parts.drop(1), route)
    }
  }
}
