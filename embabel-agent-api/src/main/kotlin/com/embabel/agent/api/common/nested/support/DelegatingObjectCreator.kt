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

import com.embabel.agent.api.common.nested.ObjectCreationExample
import com.embabel.agent.api.common.nested.ObjectCreator
import com.embabel.agent.api.common.support.OperationContextDelegate
import com.embabel.chat.Message
import com.embabel.common.ai.prompt.PromptContributor
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.function.Predicate

internal class DelegatingObjectCreator<T>(
    private val delegate: OperationContextDelegate,
    private val outputClass: Class<T>,
    private val objectMapper: ObjectMapper,
) : ObjectCreator<T> {

    private fun copy(delegate: OperationContextDelegate): DelegatingObjectCreator<T> {
        return DelegatingObjectCreator(
            delegate = delegate,
            outputClass = outputClass,
            objectMapper = objectMapper
        )
    }


    override fun withExample(example: ObjectCreationExample<T>): ObjectCreator<T> {
        val promptContributor = PromptContributor.fixed(
            """
                        Example: ${example.description}
                        ${objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(example.value)}
                        """.trimIndent()
        )
        return copy(
            delegate
                .withGenerateExamples(false)
                .withPromptContributors(listOf(promptContributor))
        )
    }

    override fun withPropertyFilter(filter: Predicate<String>): ObjectCreator<T> =
        copy(delegate = delegate.withPropertyFilter(filter))

    override fun withValidation(validation: Boolean): ObjectCreator<T> =
        copy(delegate = delegate.withValidation(validation))

    override fun fromTemplate(
        templateName: String,
        model: Map<String, Any>
    ): T =
        delegate
            .withTemplate(templateName)
            .createObject(
                outputClass = outputClass,
                model = model,
            )

    override fun fromMessages(messages: List<Message>): T =
        delegate.createObject(
            messages = messages,
            outputClass = outputClass,
        )
}