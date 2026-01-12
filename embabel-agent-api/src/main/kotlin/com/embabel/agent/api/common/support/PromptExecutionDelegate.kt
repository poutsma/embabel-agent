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
package com.embabel.agent.api.common.support

import com.embabel.agent.api.common.*
import com.embabel.agent.api.common.nested.ObjectCreator
import com.embabel.agent.api.common.nested.TemplateOperations
import com.embabel.agent.core.Action
import com.embabel.agent.core.ToolGroupRequirement
import com.embabel.chat.Message
import com.embabel.common.ai.model.LlmOptions
import com.embabel.common.ai.prompt.PromptContributor
import org.springframework.ai.tool.ToolCallback
import java.util.function.Predicate

internal interface PromptExecutionDelegate {
    val context: OperationContext
    val llm: LlmOptions
    val messages: List<Message>
    val images: List<AgentImage>
    val toolGroups: Set<ToolGroupRequirement>
    val toolObjects: List<ToolObject>
    val promptContributors: List<PromptContributor>
    val generateExamples: Boolean?
    val propertyFilter: Predicate<String>
    val validation: Boolean
    val action: Action?

    fun withMessages(messages: List<Message>): OperationContextDelegate
    fun withImages(images: List<AgentImage>): OperationContextDelegate
    fun withLlm(llm: LlmOptions): OperationContextDelegate
    fun withToolGroup(toolGroup: ToolGroupRequirement): OperationContextDelegate
    fun withOtherToolCallbacks(toolCallbacks: List<ToolCallback>): OperationContextDelegate
    fun withToolObject(toolObject: ToolObject): OperationContextDelegate
    fun withPromptContributors(promptContributors: List<PromptContributor>): OperationContextDelegate
    fun withContextualPromptContributors(
        contextualPromptContributors: List<ContextualPromptElement>,
    ): OperationContextDelegate

    fun withGenerateExamples(generateExamples: Boolean): OperationContextDelegate
    fun withPropertyFilter(filter: Predicate<String>): OperationContextDelegate
    fun withValidation(validation: Boolean): OperationContextDelegate
    fun <T> createObject(
        messages: List<Message>,
        outputClass: Class<T>,
    ): T

    fun <T> createObjectIfPossible(
        messages: List<Message>,
        outputClass: Class<T>,
    ): T?

    /**
     * Combine stored images with messages.
     * If there are images, they are added to the last message or a new UserMessage is created.
     */
    fun combineImagesWithMessages(messages: List<Message>): List<Message>
    fun withInteractionId(interactionId: InteractionId): OperationContextDelegate
    fun <T> creating(outputClass: Class<T>): ObjectCreator<T>
    fun withTemplate(templateName: String): TemplateOperations
}
