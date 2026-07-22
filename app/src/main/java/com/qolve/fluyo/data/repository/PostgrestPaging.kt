package com.qolve.fluyo.data.repository

/**
 * A page size deliberately below PostgREST's usual response ceiling.
 *
 * Every query using this value must also define a total order (including a unique
 * tie-breaker such as `id`) so offset pages cannot overlap merely because two rows
 * share the same business sort key.
 */
internal const val POSTGREST_PAGE_SIZE = 500L

/**
 * Reads every row through explicitly bounded inclusive ranges.
 *
 * The extra request after a full final page is intentional: PostgREST does not expose
 * an end-of-results flag through `decodeList`, so a short page is the reliable stop
 * condition. Callers remain responsible for using a stable total order in each query.
 */
internal suspend fun <T> collectPostgrestPages(
    pageSize: Long = POSTGREST_PAGE_SIZE,
    fetchPage: suspend (LongRange) -> List<T>,
): List<T> {
    require(pageSize in 1..Int.MAX_VALUE.toLong()) { "Invalid PostgREST page size: $pageSize" }

    val result = mutableListOf<T>()
    var offset = 0L
    do {
        val range = offset..Math.addExact(offset, pageSize - 1L)
        val page = fetchPage(range)
        check(page.size.toLong() <= pageSize) {
            "PostgREST page exceeded the requested range: ${page.size} > $pageSize"
        }
        result += page
        offset = Math.addExact(offset, page.size.toLong())
    } while (page.size.toLong() == pageSize)

    return result
}
