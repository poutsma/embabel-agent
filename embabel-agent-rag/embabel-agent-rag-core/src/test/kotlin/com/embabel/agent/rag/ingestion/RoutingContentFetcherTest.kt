/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.agent.rag.ingestion

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.net.URI

class RoutingContentFetcherTest {

    private val defaultFetcher = mockk<ContentFetcher>()
    private val mediumFetcher = mockk<ContentFetcher>()
    private val substackFetcher = mockk<ContentFetcher>()

    private fun setupFetcher(fetcher: ContentFetcher, content: String) {
        every { fetcher.fetch<Any>(any(), any()) } answers {
            val mapper = secondArg<ContentMapper<Any>>()
            mapper.map(firstArg<URI>(), null, content.byteInputStream())
        }
    }

    @Nested
    inner class Routing {

        @Test
        fun `routes to matching fetcher when URI matches pattern`() {
            setupFetcher(mediumFetcher, "medium content")
            val router = RoutingContentFetcher(
                default = defaultFetcher,
                routes = listOf("https://medium.com/**" to mediumFetcher),
            )
            router.fetch(URI("https://medium.com/some-article"), ContentMapper.BYTE_ARRAY)

            verify(exactly = 1) { mediumFetcher.fetch<Any>(any(), any()) }
            verify(exactly = 0) { defaultFetcher.fetch<Any>(any(), any()) }
        }

        @Test
        fun `falls back to default when no pattern matches`() {
            setupFetcher(defaultFetcher, "default content")
            val router = RoutingContentFetcher(
                default = defaultFetcher,
                routes = listOf("https://medium.com/**" to mediumFetcher),
            )
            router.fetch(URI("https://example.com/article"), ContentMapper.BYTE_ARRAY)

            verify(exactly = 1) { defaultFetcher.fetch<Any>(any(), any()) }
            verify(exactly = 0) { mediumFetcher.fetch<Any>(any(), any()) }
        }

        @Test
        fun `first matching route wins`() {
            setupFetcher(mediumFetcher, "medium content")
            val router = RoutingContentFetcher(
                default = defaultFetcher,
                routes = listOf(
                    "https://medium.com/**" to mediumFetcher,
                    "**/medium*/**" to substackFetcher,
                ),
            )
            router.fetch(URI("https://medium.com/article"), ContentMapper.BYTE_ARRAY)

            verify(exactly = 1) { mediumFetcher.fetch<Any>(any(), any()) }
            verify(exactly = 0) { substackFetcher.fetch<Any>(any(), any()) }
        }

        @Test
        fun `works with multiple routes`() {
            setupFetcher(substackFetcher, "substack content")
            val router = RoutingContentFetcher(
                default = defaultFetcher,
                routes = listOf(
                    "https://medium.com/**" to mediumFetcher,
                    "https://*.substack.com/**" to substackFetcher,
                ),
            )
            router.fetch(URI("https://blog.substack.com/p/my-post"), ContentMapper.BYTE_ARRAY)

            verify(exactly = 1) { substackFetcher.fetch<Any>(any(), any()) }
        }

        @Test
        fun `supports wildcard for subdomain matching`() {
            setupFetcher(substackFetcher, "substack content")
            val router = RoutingContentFetcher(
                default = defaultFetcher,
                routes = listOf("https://*.substack.com/**" to substackFetcher),
            )
            router.fetch(URI("https://myblog.substack.com/p/my-post"), ContentMapper.BYTE_ARRAY)

            verify(exactly = 1) { substackFetcher.fetch<Any>(any(), any()) }
        }

        @Test
        fun `supports double wildcard for path matching`() {
            setupFetcher(mediumFetcher, "api content")
            val router = RoutingContentFetcher(
                default = defaultFetcher,
                routes = listOf("**/api/v2/**" to mediumFetcher),
            )
            router.fetch(URI("https://example.com/api/v2/articles"), ContentMapper.BYTE_ARRAY)

            verify(exactly = 1) { mediumFetcher.fetch<Any>(any(), any()) }
        }
    }

    @Nested
    inner class JavaMapConstructor {

        @Test
        fun `works with map constructor`() {
            setupFetcher(mediumFetcher, "medium content")
            val router = RoutingContentFetcher(
                defaultFetcher,
                mapOf("https://medium.com/**" to mediumFetcher),
            )
            router.fetch(URI("https://medium.com/article"), ContentMapper.BYTE_ARRAY)

            verify(exactly = 1) { mediumFetcher.fetch<Any>(any(), any()) }
        }
    }
}
