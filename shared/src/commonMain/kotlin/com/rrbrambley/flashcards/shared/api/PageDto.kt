package com.rrbrambley.flashcards.shared.api

import kotlinx.serialization.Serializable

/**
 * One page of a cursor-paginated list endpoint.
 *
 * @param items the items in this page, in the endpoint's stable order.
 * @param nextCursor an opaque token to pass back as the `cursor` query parameter to fetch the
 *   next page, or null when this is the last page.
 */
@Serializable
data class Page<T>(val items: List<T>, val nextCursor: String? = null)
