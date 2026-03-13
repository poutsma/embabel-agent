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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets

class RssContentFetcherTest {

    @Nested
    inner class TemplateResolver {

        @Test
        fun `replaces single placeholder with first path segment`() {
            val resolver = RssContentFetcher.templateResolver("https://medium.com/feed/{0}")
            val result = resolver.resolve(URI("https://medium.com/embabel/my-article"))

            assertEquals(URI("https://medium.com/feed/embabel"), result)
        }

        @Test
        fun `replaces multiple placeholders with path segments`() {
            val resolver = RssContentFetcher.templateResolver("https://example.com/{0}/feed/{1}")
            val result = resolver.resolve(URI("https://example.com/blog/posts/article-slug"))

            assertEquals(URI("https://example.com/blog/feed/posts"), result)
        }

        @Test
        fun `template with no placeholders returns constant URL`() {
            val resolver = RssContentFetcher.templateResolver("https://myblog.com/feed")
            val result = resolver.resolve(URI("https://myblog.com/2024/some-article"))

            assertEquals(URI("https://myblog.com/feed"), result)
        }

        @Test
        fun `handles URIs with leading slash in path`() {
            val resolver = RssContentFetcher.templateResolver("https://medium.com/feed/{0}")
            val result = resolver.resolve(URI("https://medium.com/my-publication/my-post"))

            assertEquals(URI("https://medium.com/feed/my-publication"), result)
        }

        @Test
        fun `placeholder beyond available segments causes URISyntaxException`() {
            val resolver = RssContentFetcher.templateResolver("https://example.com/{0}/{5}")

            assertThrows<java.net.URISyntaxException> {
                resolver.resolve(URI("https://example.com/blog/post"))
            }
        }
    }

    @Nested
    inner class Fetch {

        private val feedWithContentEncoded = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
                <channel>
                    <title>Test Blog</title>
                    <item>
                        <title>First Article</title>
                        <link>https://blog.com/pub/first-article</link>
                        <guid>https://blog.com/pub/first-article</guid>
                        <description>Short description</description>
                        <content:encoded><![CDATA[<p>Full article content here</p>]]></content:encoded>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        private val delegate = mockk<ContentFetcher>()

        private fun setupDelegate() {
            every { delegate.fetch<Any>(any(), any()) } answers {
                val mapper = secondArg<ContentMapper<Any>>()
                mapper.map(
                    firstArg<URI>(),
                    null,
                    ByteArrayInputStream(feedWithContentEncoded.toByteArray(StandardCharsets.UTF_8)),
                )
            }
        }

        private fun createFetcher(): RssContentFetcher {
            return RssContentFetcher(
                feedResolver = FeedResolver { URI("https://blog.com/feed") },
                delegate = delegate,
            )
        }

        @Test
        fun `delegates fetching to injected ContentFetcher`() {
            setupDelegate()
            val fetcher = createFetcher()
            fetcher.fetch(URI("https://blog.com/pub/first-article"), ContentMapper.BYTE_ARRAY)

            verify(exactly = 1) { delegate.fetch<Any>(URI("https://blog.com/feed"), any()) }
        }

        @Test
        fun `extracts article content from fetched feed`() {
            setupDelegate()
            val result = createFetcher().fetch(URI("https://blog.com/pub/first-article"), ContentMapper.UTF_8_STRING)

            assertTrue(result.contains("Full article content here"))
        }

        @Test
        fun `throws IOException when article not found in feed`() {
            setupDelegate()

            assertThrows<IOException> {
                createFetcher().fetch(URI("https://blog.com/pub/nonexistent-article"), ContentMapper.BYTE_ARRAY)
            }
        }

        @Test
        fun `uses FeedResolver to determine feed URL`() {
            val customResolver = FeedResolver { URI("https://custom.com/rss/${it.path.trimStart('/')}") }
            setupDelegate()
            val fetcher = RssContentFetcher(feedResolver = customResolver, delegate = delegate)

            fetcher.fetch(URI("https://blog.com/pub/first-article"), ContentMapper.BYTE_ARRAY)

            verify { delegate.fetch<Any>(URI("https://custom.com/rss/pub/first-article"), any()) }
        }
    }
}
