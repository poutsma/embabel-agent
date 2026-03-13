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

import org.slf4j.LoggerFactory
import org.springframework.util.MimeType
import org.springframework.web.util.HtmlUtils
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import kotlin.text.Charsets.UTF_8

/**
 * [ContentMapper] that extracts a single article's HTML from RSS/Atom feed XML.
 *
 * Given the raw bytes of an RSS feed and the target article URI, this mapper
 * locates the matching `<item>` by slug and returns the article content
 * wrapped in a minimal HTML document.
 *
 * Prefers `content:encoded` (full HTML) over `description` (summary).
 *
 * Uses StAX (streaming) parsing to avoid loading the full document into memory,
 * with external entity retrieval disabled to prevent XXE attacks.
 */
class RssContentMapper : ContentMapper<InputStream> {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val xmlInputFactory = XMLInputFactory.newInstance().apply {
        // Prevent XXE: disable external entity resolution and DTD loading
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
        // Coalesce adjacent CHARACTERS and CDATA into a single CHARACTERS event
        setProperty(XMLInputFactory.IS_COALESCING, true)
    }

    override fun map(
        uri: URI,
        contentType: MimeType?,
        stream: InputStream
    ): InputStream {
        val html = extractArticleContent(stream, uri)
            ?: throw IOException("Article not found in RSS feed for URI: $uri")
        logger.info("Extracted {} chars of article content from RSS", html.size)
        return ByteArrayInputStream(html)
    }

    private fun extractArticleContent(inputStream: InputStream, articleUri: URI): ByteArray? {
        val articleSlug = articleUri.path.trimEnd('/').substringAfterLast('/')
        val reader = xmlInputFactory.createXMLStreamReader(inputStream)
        try {
            var inItem = false
            var currentElement: String? = null
            var currentNs: String? = null
            val textBuf = StringBuilder()

            var link = ""
            var guid = ""
            var title = ""
            var description: String? = null
            var contentEncoded: String? = null

            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        val localName = reader.localName
                        if (!inItem) {
                            if (localName == "item") {
                                inItem = true
                                link = ""; guid = ""; title = ""; description = null; contentEncoded = null
                            }
                        } else {
                            currentElement = localName
                            currentNs = reader.namespaceURI
                            textBuf.clear()
                        }
                    }

                    XMLStreamConstants.CHARACTERS -> {
                        if (inItem && currentElement != null) {
                            textBuf.append(reader.text)
                        }
                    }

                    XMLStreamConstants.END_ELEMENT -> {
                        val localName = reader.localName
                        if (inItem) {
                            if (localName == currentElement) {
                                val text = textBuf.toString()
                                when {
                                    currentElement == "link" -> link = text
                                    currentElement == "guid" -> guid = text
                                    currentElement == "title" -> title = text
                                    currentElement == "description" -> description = text
                                    currentElement == "encoded" && currentNs == CONTENT_NS -> contentEncoded = text
                                }
                                currentElement = null
                            }
                            if (localName == "item") {
                                inItem = false
                                if (link.contains(articleSlug) || guid.contains(articleSlug)) {
                                    val rawTitle = title.ifEmpty { "Untitled" }
                                    val escapedTitle = HtmlUtils.htmlEscape(rawTitle)
                                    val html = contentEncoded ?: description
                                    if (html != null) {
                                        return """
                                            <html><head><title>$escapedTitle</title></head>
                                            <body>
                                            <h1>$escapedTitle</h1>
                                            $html
                                            </body></html>
                                        """.trimIndent().toByteArray(UTF_8)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            reader.close()
        }
        return null
    }

    companion object {
        private const val CONTENT_NS = "http://purl.org/rss/1.0/modules/content/"
    }
}
