package com.monuo.superaiagent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.monuo.superaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *  monuo的 AI 超级智能体（拥有自主规划能力）
 */
@Component
public class MonuoManus extends ToolCallAgent {

    @Autowired
    public MonuoManus(ToolCallback[] allTools, DashScopeChatModel dashScopeChatModel) {
        super(allTools);  
        this.setName("monuoManus");
        String SYSTEM_PROMPT = """  
                You are monuoManus, an all-capable AI assistant, aimed at solving any task presented by the user.  
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.  
                """;  
        this.setSystemPrompt(SYSTEM_PROMPT);  
        String NEXT_STEP_PROMPT = """  
                Based on user needs, proactively select the most appropriate tool or combination of tools.  
                For complex tasks, you can break down the problem and use different tools step by step to solve it.  
                After using each tool, clearly explain the execution results and suggest the next steps.  
                If you want to stop the interaction at any point, use the `terminate` tool/function call.  
                """;  
        this.setNextStepPrompt(NEXT_STEP_PROMPT);  
        this.setMaxSteps(20);  
        // 初始化客户端  
        ChatClient chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();  
        this.setChatClient(chatClient);  
    }

    public MonuoManus(ToolCallback[] availableTools) {
        super(availableTools);
    }
}
