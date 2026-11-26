package com.gamekb.controller;

import com.gamekb.common.AiChatResponse;
import com.gamekb.common.Result;
import com.gamekb.dto.ChatRequest;
import com.gamekb.service.AiAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "AI游戏战术顾问智能体", description = "支持检索本地攻略库与装备数值的智能问答接口")
@RestController
@RequestMapping("/api/ai")
public class AiAgentController {

    @Resource
    private AiAgentService aiAgentService;

    @Operation(summary = "向AI战术顾问提问")
    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        // 提问为空的校验交给 ChatRequest 上的 @NotBlank，异常由全局处理器统一返回 400
        AiChatResponse data = aiAgentService.chat(request.getSessionId(), request.getQuestion());
        return Result.success(data);
    }

    /**
     * 流式问答：text/event-stream，事件依次为 session -> chunk* -> done（或 error）。
     * 前端用 fetch 读流，不能用 EventSource（无法携带 Authorization 头）。
     */
    @Operation(summary = "向AI战术顾问提问（流式输出）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        // 5 分钟：推理型模型单次回答可能超过默认的 30 秒
        SseEmitter emitter = new SseEmitter(300_000L);
        aiAgentService.chatStream(request.getSessionId(), request.getQuestion(), emitter);
        return emitter;
    }
}
