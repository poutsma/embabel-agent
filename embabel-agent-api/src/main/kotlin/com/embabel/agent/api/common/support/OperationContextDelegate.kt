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
import com.embabel.agent.api.common.nested.support.DelegatingObjectCreator
import com.embabel.agent.api.common.nested.support.DelegatingTemplateOperations
import com.embabel.agent.core.ToolGroupRequirement
import com.embabel.agent.core.support.safelyGetToolCallbacks
import com.embabel.agent.spi.LlmInteraction
import com.embabel.chat.ImagePart
import com.embabel.chat.Message
import com.embabel.chat.UserMessage
import com.embabel.common.ai.model.LlmOptions
import com.embabel.common.ai.prompt.PromptContributor
import com.embabel.common.util.loggerFor
import org.springframework.ai.tool.ToolCallback
import java.util.function.Predicate

internal data class OperationContextDelegate(
    override val context: OperationContext,
    private val interactionId: InteractionId? = null,
    override val llm: LlmOptions,
    override val messages: List<Message> = emptyList(),
    override val images: List<AgentImage> = emptyList(),
    override val toolGroups: Set<ToolGroupRequirement>,
    override val toolObjects: List<ToolObject>,
    override val promptContributors: List<PromptContributor>,
    private val contextualPromptContributors: List<ContextualPromptElement>,
    override val generateExamples: Boolean?,
    override val propertyFilter: Predicate<String> = Predicate { true },
    override val validation: Boolean = true,
    private val otherToolCallbacks: List<ToolCallback> = emptyList(),
) : PromptExecutionDelegate {

    override val action = (context as? ActionContext)?.action

    override fun withInteractionId(interactionId: InteractionId): OperationContextDelegate =
        copy(interactionId = interactionId)

    override fun withMessages(messages: List<Message>): OperationContextDelegate =
        copy(messages = this.messages + messages)

    override fun withImages(images: List<AgentImage>): OperationContextDelegate =
        copy(images = this.images + images)

    override fun withLlm(llm: LlmOptions): OperationContextDelegate =
        copy(llm = llm)

    override fun withToolGroup(toolGroup: ToolGroupRequirement): OperationContextDelegate =
        copy(toolGroups = this.toolGroups + toolGroup)

    override fun withOtherToolCallbacks(toolCallbacks: List<ToolCallback>): OperationContextDelegate =
        copy(otherToolCallbacks = this.otherToolCallbacks + toolCallbacks)

    override fun withToolObject(toolObject: ToolObject): OperationContextDelegate =
        copy(toolObjects = this.toolObjects + toolObject)

    override fun withPromptContributors(promptContributors: List<PromptContributor>): OperationContextDelegate =
        copy(promptContributors = this.promptContributors + promptContributors)

    override fun withContextualPromptContributors(
        contextualPromptContributors: List<ContextualPromptElement>,
    ): OperationContextDelegate =
        copy(contextualPromptContributors = this.contextualPromptContributors + contextualPromptContributors)

    override fun withGenerateExamples(generateExamples: Boolean): OperationContextDelegate =
        copy(generateExamples = generateExamples)

    override fun withPropertyFilter(filter: Predicate<String>): OperationContextDelegate =
        copy(propertyFilter = this.propertyFilter.and(filter))

    override fun withValidation(validation: Boolean): OperationContextDelegate =
        copy(validation = validation)

    override fun <T> creating(outputClass: Class<T>): ObjectCreator<T> =
        DelegatingObjectCreator(
            delegate = this,
            outputClass = outputClass,
            objectMapper = context.agentPlatform().platformServices.objectMapper,
        )

    override fun withTemplate(templateName: String): TemplateOperations =
        DelegatingTemplateOperations(
            delegate = this,
            templateName = templateName,
            templateRenderer = context.agentPlatform().platformServices.templateRenderer,
        )

    override fun <T> createObject(
        messages: List<Message>,
        outputClass: Class<T>,
    ): T {
        val allPromptContributors = promptContributors + contextualPromptContributors.map {
            it.toPromptContributor(
                context
            )
        }
        val combinedMessages = combineImagesWithMessages(this.messages + messages)
        return context.processContext.createObject(
            messages = combinedMessages,
            interaction = LlmInteraction(
                llm = llm,
                toolGroups = this.toolGroups + toolGroups,
                toolCallbacks = safelyGetToolCallbacks(toolObjects) + otherToolCallbacks,
                promptContributors = allPromptContributors,
                id = interactionId ?: idForPrompt(outputClass),
                generateExamples = generateExamples,
                propertyFilter = propertyFilter,
                validation = validation,
            ),
            outputClass = outputClass,
            agentProcess = context.processContext.agentProcess,
            action = action,
        )
    }

    override fun <T> createObjectIfPossible(
        messages: List<Message>,
        outputClass: Class<T>,
    ): T? {
        val combinedMessages = combineImagesWithMessages(this.messages + messages)
        val result = context.processContext.createObjectIfPossible<T>(
            messages = combinedMessages,
            interaction = LlmInteraction(
                llm = llm,
                toolGroups = this.toolGroups + toolGroups,
                toolCallbacks = safelyGetToolCallbacks(toolObjects) + otherToolCallbacks,
                promptContributors = promptContributors + contextualPromptContributors.map {
                    it.toPromptContributor(
                        context
                    )
                },
                id = interactionId ?: idForPrompt(outputClass),
                generateExamples = generateExamples,
                propertyFilter = propertyFilter,
                validation = validation,
            ),
            outputClass = outputClass,
            agentProcess = context.processContext.agentProcess,
            action = action,
        )
        if (result.isFailure) {
            loggerFor<OperationContextDelegate>().warn(
                "Failed to create object of type {} with messages {}: {}",
                outputClass.name,
                messages,
                result.exceptionOrNull()?.message,
            )
        }
        return result.getOrNull()
    }

    /**
     * Combine stored images with messages.
     * If there are images, they are added to the last message or a new UserMessage is created.
     */
    override fun combineImagesWithMessages(messages: List<Message>): List<Message> {
        if (images.isEmpty()) {
            return messages
        }

        val imageParts = images.map { ImagePart(it.mimeType, it.data) }

        // If there are no messages, create a UserMessage with just images
        if (messages.isEmpty()) {
            return listOf(UserMessage(parts = imageParts))
        }

        // Add images to the last message if it's a UserMessage
        val lastMessage = messages.last()
        if (lastMessage is UserMessage) {
            val updatedLastMessage = UserMessage(
                parts = lastMessage.parts + imageParts,
                name = lastMessage.name,
                timestamp = lastMessage.timestamp
            )
            return messages.dropLast(1) + updatedLastMessage
        } else {
            // If last message is not a UserMessage, append a new UserMessage with images
            return messages + UserMessage(parts = imageParts)
        }
    }

    private fun idForPrompt(
        outputClass: Class<*>,
    ): InteractionId {
        return InteractionId("${context.operation.name}-${outputClass.name}")
    }

}
