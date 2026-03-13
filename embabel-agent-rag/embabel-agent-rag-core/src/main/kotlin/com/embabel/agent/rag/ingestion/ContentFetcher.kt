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

import org.springframework.util.MimeType
import org.springframework.util.StreamUtils
import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset
import kotlin.text.Charsets.UTF_8

/**
 * Abstraction for fetching raw content from HTTP/HTTPS URIs.
 * Implementations can use different strategies such as HttpURLConnection,
 * headless browsers (Selenium), or other HTTP clients.
 */
interface ContentFetcher {

    /**
     * Fetch raw content from the given URI.
     * @param uri the HTTP/HTTPS URI to fetch
     * @return a [FetchResult] containing the raw bytes and HTTP metadata
     * @throws java.io.IOException if the fetch fails
     */
    fun <T> fetch(
        uri: URI,
        mapper: ContentMapper<T>,
    ): T
}

/**
 * Transforms fetched content bytes, e.g. extracting article HTML from an RSS feed,
 * stripping ads, or normalizing encoding.
 *
 * Mappers are composable via [then], allowing pipelines such as
 * `rssMapper.then(removeAds).then(translateTo(Language.FRENCH))`.
 */
fun interface ContentMapper<T> {

    fun map(
        uri: URI,
        contentType: MimeType?,
        stream: InputStream
    ): T

    companion object {
        @JvmField
        val BYTE_ARRAY: ContentMapper<ByteArray> = ContentMapper { _, _, stream -> stream.readBytes() }

        @JvmField
        val UTF_8_STRING: ContentMapper<String> =
            ContentMapper { _, _, stream -> stream.bufferedReader(UTF_8).readText() }

    }


}
