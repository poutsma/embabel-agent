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
import com.embabel.agent.api.tool.Tool
import com.embabel.agent.core.ProcessOptions
import com.embabel.agent.core.ToolGroup
import com.embabel.agent.core.ToolGroupRequirement
import com.embabel.agent.core.Verbosity
import com.embabel.agent.experimental.primitive.Determination
import com.embabel.agent.spi.support.springai.toSpringToolCallback
import com.embabel.agent.tools.agent.AgentToolCallback
import com.embabel.agent.tools.agent.Handoffs
import com.embabel.agent.tools.agent.PromptedTextCommunicator
import com.embabel.chat.Message
import com.embabel.common.ai.model.LlmOptions
import com.embabel.common.ai.prompt.PromptContributor
import com.embabel.common.core.types.ZeroToOne
import com.embabel.common.util.loggerFor
import java.util.function.Predicate

internal class DelegatingPromptRunner(
    private val delegate: PromptExecutionDelegate
) : PromptRunner {

    override val llm: LlmOptions
        get() = delegate.llm

    override val generateExamples: Boolean?
        get() = delegate.generateExamples

    override val propertyFilter: Predicate<String>
        get() = delegate.propertyFilter

    override val validation: Boolean
        get() = delegate.validation;

    override val promptContributors: List<PromptContributor>
        get() = delegate.promptContributors

    override val toolGroups: Set<ToolGroupRequirement>
        get() = delegate.toolGroups

    override val toolObjects: List<ToolObject>
        get() = delegate.toolObjects

    override val messages: List<Message>
        get() = delegate.messages

    override val images: List<AgentImage>
        get() = delegate.images

    private fun copy(delegate: OperationContextDelegate): DelegatingPromptRunner {
        return DelegatingPromptRunner(delegate)
    }

    override fun withInteractionId(interactionId: InteractionId): PromptRunner =
        copy(delegate = delegate.withInteractionId(interactionId))

    override fun withLlm(llm: LlmOptions): PromptRunner =
        copy(delegate = delegate.withLlm(llm))

    override fun withMessages(messages: List<Message>): PromptRunner =
        copy(delegate = delegate.withMessages(messages))

    override fun withImages(images: List<AgentImage>): PromptRunner =
        copy(delegate = delegate.withImages(images))

    override fun withToolGroup(toolGroup: ToolGroup): PromptRunner =
        copy(delegate = delegate.withOtherToolCallbacks(toolGroup.toolCallbacks))

    override fun withToolGroup(toolGroup: ToolGroupRequirement): PromptRunner =
        copy(delegate = delegate.withToolGroup(toolGroup))

    override fun withToolObject(toolObject: ToolObject): PromptRunner =
        copy(delegate = delegate.withToolObject(toolObject))

    override fun withTool(tool: Tool): PromptRunner =
        copy(delegate = delegate.withOtherToolCallbacks(listOf(tool.toSpringToolCallback())))

    override fun withHandoffs(vararg outputTypes: Class<*>): PromptRunner {
        val handoffs = Handoffs(
            autonomy = delegate.context.agentPlatform().platformServices.autonomy(),
            outputTypes = outputTypes.toList(),
            applicationName = delegate.context.agentPlatform().name,
        )
        return copy(delegate = delegate.withOtherToolCallbacks(handoffs.toolCallbacks))
    }

    override fun withSubagents(vararg subagents: Subagent): PromptRunner {
        val newCallbacks = subagents.map { subagent ->
            val context = delegate.context
            val agent = subagent.resolve(context.agentPlatform())
            AgentToolCallback(
                autonomy = context.agentPlatform().platformServices.autonomy(),
                agent = agent,
                textCommunicator = PromptedTextCommunicator,
                objectMapper = context.agentPlatform().platformServices.objectMapper,
                inputType = subagent.inputClass,
                processOptionsCreator = { agentProcess ->
                    val blackboard = agentProcess.processContext.blackboard.spawn()
                    loggerFor<DelegatingPromptRunner>().info(
                        "Creating subagent process for {} with blackboard {}",
                        agent.name,
                        blackboard,
                    )
                    ProcessOptions(
                        verbosity = Verbosity(showPrompts = true),
                        blackboard = blackboard,
                    )
                },
            )
        }
        return copy(delegate = delegate.withOtherToolCallbacks(newCallbacks))
    }

    override fun withPromptContributors(promptContributors: List<PromptContributor>): PromptRunner =
        copy(delegate = delegate.withPromptContributors(promptContributors))

    override fun withContextualPromptContributors(contextualPromptContributors: List<ContextualPromptElement>): PromptRunner =
        copy(delegate = delegate.withContextualPromptContributors(contextualPromptContributors))

    override fun withGenerateExamples(generateExamples: Boolean): PromptRunner =
        copy(delegate = delegate.withGenerateExamples(generateExamples))

    override fun withPropertyFilter(filter: Predicate<String>): PromptRunner =
        copy(delegate = delegate.withPropertyFilter(filter))

    override fun withValidation(validation: Boolean): PromptRunner =
        copy(delegate = delegate.withValidation(validation))

    override fun <T> creating(outputClass: Class<T>): ObjectCreator<T> =
        delegate.creating(outputClass);

    override fun <T> createObject(
        messages: List<Message>,
        outputClass: Class<T>
    ): T =
        delegate.createObject(messages, outputClass)

    override fun <T> createObjectIfPossible(
        messages: List<Message>,
        outputClass: Class<T>
    ): T? =
        delegate.createObjectIfPossible(messages, outputClass)

    override fun withTemplate(templateName: String): TemplateOperations =
        delegate.withTemplate(templateName)

    override fun evaluateCondition(
        condition: String,
        context: String,
        confidenceThreshold: ZeroToOne
    ): Boolean {
        val prompt =
            """
            Evaluate this condition given the context.
            Return "result": whether you think it is true, your confidence level from 0-1,
            and an explanation of what you base this on.

            # Condition
            $condition

            # Context
            $context
            """.trimIndent()
        val determination = createObject(
            prompt = prompt,
            outputClass = Determination::class.java,
        )
        loggerFor<DelegatingPromptRunner>().info(
            "Condition {}: determination from {} was {}",
            condition,
            llm.criteria,
            determination,
        )
        return determination.result && determination.confidence >= confidenceThreshold
    }
}
