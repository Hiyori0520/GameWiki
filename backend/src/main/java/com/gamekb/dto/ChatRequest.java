package com.gamekb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    /** 会话 ID，为空时后端自动生成，前端拿到后带上即可实现多轮记忆 */
    private String sessionId;

    @NotBlank(message = "提问内容不能为空")
    @Size(max = 500, message = "提问内容最多 500 字")
    private String question;
}
