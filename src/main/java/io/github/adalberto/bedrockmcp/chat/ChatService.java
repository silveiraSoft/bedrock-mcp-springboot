package io.github.adalberto.bedrockmcp.chat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.adalberto.bedrockmcp.config.AppProperties;
import io.github.adalberto.bedrockmcp.guardrail.GuardrailResult;
import io.github.adalberto.bedrockmcp.guardrail.GuardrailService;
import io.github.adalberto.bedrockmcp.tools.CatalogTools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Natural language -> REST API, with guardrails on both sides of the model.
 *
 * <pre>
 *  user text ──► [Guardrail INPUT] ──► LLM (Bedrock Converse, tool use) ──► tools ──► REST API
 *                     │ BLOCKED                         │ answer
 *                     ▼                                 ▼
 *               blocked message               [Guardrail OUTPUT] ──► response
 * </pre>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final GuardrailService guardrail;
    private final CatalogTools tools;
    private final AppProperties props;
    private final String systemPrompt;

    public ChatService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, GuardrailService guardrail,
            CatalogTools tools, AppProperties props, ResourceLoader resourceLoader) throws IOException {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.guardrail = guardrail;
        this.tools = tools;
        this.props = props;
        this.systemPrompt = resourceLoader.getResource(props.chat().systemPromptLocation())
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public ChatResponse chat(ChatRequest request) {
        String conversationId = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();
        String message = request.message().length() > props.chat().maxUserMessageChars()
                ? request.message().substring(0, props.chat().maxUserMessageChars())
                : request.message();

        // 1) INPUT guardrail - before spending tokens or running any tool
        GuardrailResult in = guardrail.evaluate(message, GuardrailService.Source.INPUT);
        if (in.blocked()) {
            log.info("conversation={} input blocked reasons={}", conversationId, in.reasons());
            return new ChatResponse(conversationId, in.text(),
                    new ChatResponse.GuardrailInfo(in.action(), GuardrailResult.Action.NONE, in.reasons()),
                    ChatResponse.Usage.none());
        }

        // 2) LLM with tool use (tools call the REST API with the caller's identity)
        org.springframework.ai.chat.model.ChatResponse llm = chatClient.prompt()
                .system(systemPrompt)
                .user(in.text())
                .tools(tools)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();

        String answer = llm != null && llm.getResult() != null && llm.getResult().getOutput() != null
                ? llm.getResult().getOutput().getText()
                : "";

        // 3) OUTPUT guardrail - PII leakage, toxic content, denied topics in the model answer
        GuardrailResult out = props.guardrail().checkOutput()
                ? guardrail.evaluate(answer, GuardrailService.Source.OUTPUT)
                : GuardrailResult.pass(answer);

        List<String> reasons = new ArrayList<>(in.reasons());
        reasons.addAll(out.reasons());
        return new ChatResponse(conversationId, out.text(),
                new ChatResponse.GuardrailInfo(in.action(), out.action(), List.copyOf(reasons)),
                usage(llm));
    }

    private static ChatResponse.Usage usage(org.springframework.ai.chat.model.ChatResponse llm) {
        if (llm == null || llm.getMetadata() == null || llm.getMetadata().getUsage() == null) {
            return ChatResponse.Usage.none();
        }
        Usage u = llm.getMetadata().getUsage();
        return new ChatResponse.Usage(u.getPromptTokens(), u.getCompletionTokens(), u.getTotalTokens(),
                llm.getMetadata().getModel());
    }
}
