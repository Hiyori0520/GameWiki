package com.gamekb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamekb.auth.UserContext;
import com.gamekb.common.AiChatResponse;
import com.gamekb.common.BizException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class AiAgentService {
    private final ChatClient chatClient;
    private final CatalogDatabase db;
    private final ObjectMapper json;

    public AiAgentService(ChatClient.Builder builder, CatalogDatabase db, ObjectMapper json) {
        this.db = db;
        this.json = json;
        this.chatClient = builder
                .defaultSystem("你是一名专业的AI游戏战术顾问，服务于游戏攻略知识库系统。遇到具体的游戏打法、载具机动、BOSS攻略或武器装备数值问题时，必须优先调用 searchGameGuideTool 或 queryItemStatsTool 查询本地知识库，并注明参考的本地攻略编号。如果知识库未收录，请诚实说明并给出通用建议。")
                .defaultToolNames("searchGameGuideTool", "queryItemStatsTool")
                .build();
    }

    public AiChatResponse chat(String sessionId, String userQuestion) {
        String session = resolveSession(sessionId, userQuestion);
        long userId = UserContext.get().getId();
        String reply = chatClient.prompt().messages(history(userId, session)).user(userQuestion).call().content();
        if (reply == null || reply.isBlank()) throw new BizException(502, "模型没有返回可显示的回答");
        save(userId, session, userQuestion, reply);
        return new AiChatResponse(session, reply);
    }

    /**
     * 流式回答：先推 session，再逐段推 chunk，结束推 done；落库在流结束时一次性写入。
     * 注意 UserContext 是 ThreadLocal，必须在当前线程取值，Reactor 回调线程里取不到。
     */
    public void chatStream(String sessionId, String userQuestion, SseEmitter emitter) {
        String session = resolveSession(sessionId, userQuestion);
        long userId = UserContext.get().getId();
        List<Message> history = history(userId, session);

        send(emitter, "session", session);

        StringBuilder reply = new StringBuilder();
        chatClient.prompt().messages(history).user(userQuestion).stream().content()
                .subscribe(
                        chunk -> {
                            reply.append(chunk);
                            send(emitter, "chunk", chunk);
                        },
                        error -> {
                            send(emitter, "error", error.getMessage() == null ? "模型调用失败" : error.getMessage());
                            emitter.complete();
                        },
                        () -> {
                            if (reply.length() == 0) {
                                send(emitter, "error", "模型没有返回可显示的回答");
                            } else {
                                save(userId, session, userQuestion, reply.toString());
                                send(emitter, "done", session);
                            }
                            emitter.complete();
                        });
    }

    private String resolveSession(String sessionId, String userQuestion) {
        String session = sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId;
        if (session.length() > 64 || userQuestion.length() > 8000) throw new BizException("对话参数过长");
        return session;
    }

    private List<Message> history(long userId, String session) {
        var rows = db.jdbc.queryForList("""
                SELECT role,content FROM ai_chat_log WHERE user_id=? AND session_id=? ORDER BY id DESC LIMIT 10
                """, userId, session);
        Collections.reverse(rows);
        var history = new ArrayList<Message>();
        for (var row : rows) {
            String text = CatalogDatabase.text(row, "content");
            if ("user".equals(row.get("role"))) history.add(new UserMessage(text));
            if ("assistant".equals(row.get("role"))) history.add(new AssistantMessage(text));
        }
        return history;
    }

    private void save(long userId, String session, String question, String reply) {
        db.jdbc.update("""
                INSERT INTO ai_chat_log(session_id,user_id,role,content) VALUES (?,?,'user',?),(?,?,'assistant',?)
                """, session, userId, question, session, userId, reply);
    }

    /** 事件数据统一 JSON 编码，避免正文里的换行破坏 SSE 帧格式 */
    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(data)));
        } catch (Exception ignored) {
            // 客户端断开或已完成，忽略即可，落库在 onComplete 里独立完成
        }
    }
}
