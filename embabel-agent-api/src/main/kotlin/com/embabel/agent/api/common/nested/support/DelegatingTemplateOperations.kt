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

package com.embabel.agent.api.common.nested.support

import com.embabel.agent.api.common.nested.TemplateOperations
import com.embabel.agent.api.common.support.OperationContextDelegate
import com.embabel.chat.*
import com.embabel.common.textio.template.TemplateRenderer

internal class DelegatingTemplateOperations(
    private val delegate: OperationContextDelegate,
    templateName: String,
    templateRenderer: TemplateRenderer,
) : TemplateOperations {

    private val compiledTemplate = templateRenderer.compileLoadedTemplate(templateName)

    private fun renderToMessages(
        model: Map<String, Any>,
        messageCreator: (String) -> Message
    ): List<Message> = listOf(
        messageCreator(
            compiledTemplate.render(model = model),
        )
    )

    override fun <T> createObject(
        outputClass: Class<T>,
        model: Map<String, Any>
    ): T = delegate.createObject(
        messages = renderToMessages(
            model = model,
            messageCreator = ::UserMessage,
        ),
        outputClass = outputClass,
    )

    override fun generateText(
        model: Map<String, Any>
    ): String = delegate.createObject(
        messages = renderToMessages(
            model = model,
            messageCreator = ::UserMessage,
        ),
        outputClass = String::class.java,
    )

    override fun respondWithSystemPrompt(
        conversation: Conversation,
        model: Map<String, Any>
    ): AssistantMessage = AssistantMessage(
        delegate.createObject(
            messages = renderToMessages(
                model = model,
                messageCreator = ::SystemMessage,
            ) + conversation.messages,
            outputClass = String::class.java,
        )
    )

}