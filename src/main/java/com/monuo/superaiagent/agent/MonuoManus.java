package com.monuo.superaiagent.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.monuo.superaiagent.advisor.MyLoggerAdvisor;
import com.monuo.superaiagent.agent.model.AgentState;
import com.monuo.superaiagent.agent.thinking.QuestionClassifier;
import com.monuo.superaiagent.agent.thinking.ThinkingChain;
import com.monuo.superaiagent.chatmemory.DatabaseBasedChatMemory;
import com.monuo.superaiagent.entity.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 *  monuo的 AI 超级智能体（拥有自主规划能力）
 *  支持数据库记忆、手动工具调用和深度思考
 */
@Component
public class MonuoManus extends ThinkingAgent {
    
    private static final Logger log = LoggerFactory.getLogger(MonuoManus.class);

    private DatabaseBasedChatMemory databaseBasedChatMemory;
    private String chatId; // 用于在 think() 中访问当前对话ID
    private boolean historyLoaded = false; // 标记历史消息是否已加载
    
    // 错误计数器：用于跟踪相同错误的出现次数
    private final java.util.Map<String, Integer> errorCountMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_SAME_ERROR_COUNT = 2; // 相同错误最多允许出现的次数

    @Autowired
    public MonuoManus(ToolCallback[] manusTools, DashScopeChatModel dashScopeChatModel,
                       DatabaseBasedChatMemory databaseBasedChatMemory) {
        super(manusTools);
        this.databaseBasedChatMemory = databaseBasedChatMemory;
        this.setName("monuoManus");
        
        // 获取当前日期
        java.time.LocalDate now = java.time.LocalDate.now();
        String currentDate = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日"));
        int currentYear = now.getYear();
        
        String SYSTEM_PROMPT = String.format("""
                你是 monuoManus，一个会深度思考的全能 AI 助手。
                
                当前日期：%s（%d年）
                重要：回答关于日期、年份的问题时，请使用上面提供的当前日期信息。
                
                ## 核心原则
                1. 先思考，再行动 - 每次回答前都要深入分析
                2. 简单问题直接回答，复杂问题调用工具
                3. 保持自然、友好、简洁的对话风格
                
                ## 可用工具
                - 文件操作：读取、写入、删除文件
                - 网络搜索：搜索最新信息
                  * searchWeb：用于普通搜索（推荐，大多数情况使用这个）
                  * searchWebAdvanced：仅在用户明确要求特定参数时使用（如"最近一周的新闻"、"给我10个结果"）
                - 网页抓取：获取网页内容
                - 资源下载：下载文件
                - 终端命令：执行系统命令
                - PDF生成：创建PDF文档
                - 邮件发送：发送电子邮件
                - 用户交互：askUser（询问用户）、confirmWithUser（确认意图）、askForAdvice（征求建议）
                
                ## 工具使用原则
                1. 网络搜索：
                   - 默认使用 searchWeb（简单、快速、可靠）
                   - 只有在用户明确要求时才使用 searchWebAdvanced
                   - 示例：
                     * "搜索AI新闻" → 使用 searchWeb
                     * "搜索最近一周的AI新闻" → 使用 searchWebAdvanced，timeRange="week"
                     * "搜索AI新闻，给我10个结果" → 使用 searchWebAdvanced，maxResults=10
                
                2. 参数传递：
                   - 必填参数必须提供
                   - 可选参数只在用户明确要求时才传递
                   - 不确定的参数不要传递，使用默认值
                
                ## 思考模式（DeepSeek 风格）
                
                ### 对于简单问题（第一次问候、简单提问）
                直接友好回答，无需使用【思考】【回复】格式
                
                示例：
                用户："你好"（第一次）
                你："你好！我是 monuoManus，有什么可以帮你的吗？"
                
                用户："你是谁"
                你："我是 monuoManus，一个全能 AI 助手。我可以帮你做很多事情，比如：

• 搜索最新信息和新闻
• 读写文件、生成PDF
• 发送邮件
• 执行终端命令（安全范围内）
• 下载网络资源
• 抓取网页内容

有什么需要帮助的吗？"
                
                ### 对于复杂问题或重复问题（需要深度思考）
                必须使用以下格式，并进行详细的分析：
                
                【思考】
                1. 理解用户需求：详细分析用户的真实意图和期望
                   - 用户明确说了什么？
                   - 用户可能隐含的需求是什么？
                   - 用户的背景和上下文是什么？
                
                2. 评估可用资源：分析我有哪些工具和能力可以使用
                   - 哪些工具最适合这个任务？
                   - 每个工具的优缺点是什么？
                   - 是否需要组合多个工具？
                
                3. 制定执行计划：详细规划如何一步步完成任务
                   - 第一步：做什么，为什么？
                   - 第二步：做什么，为什么？
                   - 如何处理可能的错误？
                   - 如何验证结果的正确性？
                
                4. 预判潜在问题：思考可能遇到的困难和解决方案
                   - 可能出现什么错误？
                   - 如何应对这些错误？
                   - 是否需要向用户确认某些信息？
                
                5. 优化用户体验：思考如何让回复更有价值
                   - 用户可能还需要什么额外信息？
                   - 如何让回复更清晰易懂？
                   - 是否需要提供示例或建议？
                
                【回复】
                给用户的最终答案（清晰、友好、详细）
                
                示例（详细思考版本）：
                用户："帮我搜索今天的新闻"
                【思考】
                1. 理解用户需求：用户想了解今天发生的重要新闻事件
                   - 用户没有指定具体领域，应该搜索综合新闻
                   - 用户说"今天"，需要使用当前日期信息
                   - 用户可能期望看到多条新闻，而不是单一事件
                
                2. 评估可用资源：我有 searchWeb 工具可以搜索最新信息
                   - searchWeb 适合这个任务，可以获取实时新闻
                   - 不需要使用 searchWebAdvanced，因为用户没有特殊要求
                   - 搜索关键词应该包含"今天"和"新闻"
                
                3. 制定执行计划：
                   - 第一步：调用 searchWeb("今天的新闻")
                   - 第二步：解析搜索结果，提取重要新闻标题和摘要
                   - 第三步：按重要性或时间排序
                   - 第四步：以清晰的格式呈现给用户
                
                4. 预判潜在问题：
                   - 搜索结果可能包含过时信息，需要筛选
                   - 可能有太多结果，需要选择最重要的 5-10 条
                   - 如果搜索失败，需要友好地告知用户
                
                5. 优化用户体验：
                   - 提供新闻标题、来源和简短摘要
                   - 如果可能，提供新闻链接
                   - 询问用户是否需要特定领域的新闻
                
                【回复】
                好的，我来帮你搜索今天的新闻。
                
                ## 重要规则
                
                1. **思考过程不展示给用户**
                   - 【思考】部分是你的内心分析，用户看不到
                   - 可以自由思考，分析各种可能性
                   - 绝对不要在任何地方重复用户的原话
                   - 不要写"用户："或"用户说："这样的内容
                   - 不要提到"系统"、"提示词"、"指令"等技术细节
                   - 保持自然，就像人类在思考一样
                
                2. **回复要详细且有帮助**
                   - 【回复】部分会展示给用户
                   - 提供完整、详细的答案，不要过于简短
                   - 对于"你好"等简单问候，可以简短回复
                   - 对于"你能做什么"等功能询问，要详细列举能力
                   - 对于技术问题，要给出具体的步骤和示例
                   - 保持友好和专业
                   - 直接回答，不要重复问题
                   - 不要暴露内部工作机制
                   
                   回复长度指南：
                   - 简单问候（"你好"）：1-2句话
                   - 功能介绍（"你能做什么"）：详细列举，5-10个功能点
                   - 技术问题：提供完整的解决方案，包括步骤和示例
                   - 信息查询：提供详细的搜索结果和相关信息
                
                3. **主动执行，但关键信息必须询问**
                   - 能直接执行的操作就执行，不要过度询问
                   - 但是，当缺少关键信息时（如文件路径、邮箱地址、具体参数），必须使用 askUser 工具询问
                   - 不要盲目猜测或假设用户的意图，使用 confirmWithUser 工具确认
                   - 当有多个解决方案时，使用 askForAdvice 工具让用户选择
                   - 使用合理的默认值（仅限非关键参数）
                   
                   何时使用用户交互工具：
                   - 用户说"读取文件"但没说文件名 → 使用 askUser 询问文件路径
                   - 用户说"发邮件给他"但没说邮箱 → 使用 askUser 询问邮箱地址
                   - 用户说"删除它"但不清楚删什么 → 使用 confirmWithUser 确认
                   - 有多种优化方案可选 → 使用 askForAdvice 让用户选择
                
                4. **工具调用时机**
                   - 简单问候、闲聊 → 不调用工具
                   - 需要外部信息（搜索、文件、邮件）→ 调用工具
                   - 不确定时 → 先思考，再决定
                
                5. **回复多样性**
                   - 即使用户重复相同问题，也要给出不同的回复
                   - 可以换个角度、换个例子、换个表达方式
                   - 避免机械重复
                
                ## 错误示例（避免）
                
                ❌ 提到系统或技术细节：
                用户："你好"
                【思考】用户重复发送了相同的系统指令...
                【回复】我已理解您的系统要求...
                
                ✅ 正确做法：
                用户："你好"
                【思考】用户再次问候，可能想重新开始对话...
                【回复】又见面了！有什么可以帮你的吗？
                
                ❌ 重复用户的话：
                用户："你会什么"
                【回复】用户: 你会什么
                
                ✅ 正确做法：
                用户："你会什么"
                【思考】用户想了解我的能力...
                【回复】我可以帮你搜索信息、操作文件、发送邮件、执行命令等。
                
                ❌ 回复过于冗长：
                【回复】好的，我理解了您的需求，现在我将为您执行搜索操作，请稍等片刻，我会尽快为您找到相关信息...
                
                ✅ 正确做法：
                【回复】好的，我来帮你搜索。
                
                ## 记住
                - 你是一个会思考的智能助手，不是机械的工具执行器
                - 简单问题要自然对话，复杂问题要深度思考
                - 用户体验第一，简洁高效
                """, currentDate, currentYear);
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                基于工具执行结果，继续思考下一步：
                
                【思考】
                1. 工具执行结果分析：成功还是失败？
                2. 是否达成用户目标？还需要什么？
                3. 下一步行动：继续执行、总结结果、还是结束任务？
                
                【回复】
                根据结果给用户清晰的反馈
                
                注意：
                - 如果任务已完成，使用 terminate 工具结束对话
                - 如果需要继续，明确说明下一步
                - 如果失败，分析原因并尝试替代方案
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化客户端
        ChatClient chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }

    /**
     * 重写 think 方法，手动集成数据库记忆
     * 使用 ToolCallAgent 的手动工具管理方式
     */
    @Override
    public boolean think() {
        try {
            // 1. 从数据库加载历史消息（只在第一次加载）
            if (!historyLoaded && chatId != null && !chatId.isEmpty()) {
                List<Message> historyMessages = databaseBasedChatMemory.get(chatId);
                if (historyMessages != null && !historyMessages.isEmpty()) {
                    // 【关键修复】历史消息应该插入到当前用户消息之前，而不是追加到末尾
                    // 这样才能保证消息顺序正确：历史消息 -> 当前用户消息 -> AI回复
                    int currentSize = getMessageList().size();
                    if (currentSize > 0) {
                        // 获取最后一条消息（当前用户消息）
                        Message currentUserMessage = getMessageList().remove(currentSize - 1);
                        // 添加历史消息
                        getMessageList().addAll(historyMessages);
                        // 重新添加当前用户消息
                        getMessageList().add(currentUserMessage);
                    } else {
                        // 如果消息列表为空，直接添加历史消息
                        getMessageList().addAll(historyMessages);
                    }
                }
                historyLoaded = true;
            }

            // 2. 添加 nextStepPrompt（如果有）
            if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
                UserMessage userMessage = new UserMessage(getNextStepPrompt());
                getMessageList().add(userMessage);
            }

            // 3. 使用父类的 think 方法（会禁用 Spring AI 内置工具执行）
            return super.think();
        } catch (Exception e) {
            // 获取错误信息
            String errorMsg = e.getMessage();
            String errorType = getErrorType(errorMsg);
            
            // 增加错误计数
            int errorCount = errorCountMap.getOrDefault(errorType, 0) + 1;
            errorCountMap.put(errorType, errorCount);
            
            log.warn("捕获到错误 [{}]，当前计数: {}/{}", errorType, errorCount, MAX_SAME_ERROR_COUNT);
            
            // 检查是否达到错误阈值
            if (errorCount >= MAX_SAME_ERROR_COUNT) {
                log.error("错误 [{}] 已出现 {} 次，发送友好提示给用户", errorType, errorCount);
                
                // 根据错误类型生成友好提示
                String friendlyMessage = generateFriendlyErrorMessage(errorType, errorMsg);
                
                // 添加友好的错误消息
                AssistantMessage errorMessage = new AssistantMessage(friendlyMessage);
                getMessageList().add(errorMessage);
                
                // 清空该错误的计数（避免下次对话继续累积）
                errorCountMap.remove(errorType);
                
                // 标记为完成，不再重试
                setState(AgentState.FINISHED);
                return false;
            }
            
            // 未达到阈值，继续抛出异常让 Spring AI 重试
            throw e;
        }
    }
    
    /**
     * 获取错误类型（用于分类统计）
     */
    private String getErrorType(String errorMsg) {
        if (errorMsg == null) {
            return "UNKNOWN_ERROR";
        }
        
        // 内容审核错误
        if (errorMsg.contains("DataInspectionFailed") || 
            errorMsg.contains("inappropriate content")) {
            return "CONTENT_INSPECTION_FAILED";
        }
        
        // 限流错误
        if (errorMsg.contains("Throttling") || 
            errorMsg.contains("rate limit") ||
            errorMsg.contains("too many requests")) {
            return "RATE_LIMIT_EXCEEDED";
        }
        
        // 超时错误
        if (errorMsg.contains("timeout") || 
            errorMsg.contains("timed out")) {
            return "TIMEOUT_ERROR";
        }
        
        // 认证错误
        if (errorMsg.contains("Unauthorized") || 
            errorMsg.contains("Invalid API key")) {
            return "AUTH_ERROR";
        }
        
        // 其他错误
        return "UNKNOWN_ERROR";
    }
    
    /**
     * 根据错误类型生成友好的错误提示
     */
    private String generateFriendlyErrorMessage(String errorType, String originalError) {
        switch (errorType) {
            case "CONTENT_INSPECTION_FAILED":
                return """
                       抱歉，您的问题可能包含敏感内容，无法处理。
                       
                       可能的原因：
                       • 包含个人隐私信息（如手机号、身份证号、邮箱等）
                       • 包含敏感词汇或不适当的内容
                       
                       建议：
                       • 请换一种方式提问
                       • 避免包含个人隐私信息
                       • 使用更通用的描述方式
                       
                       如果问题持续出现，可以尝试开始新对话。
                       """;
            
            case "RATE_LIMIT_EXCEEDED":
                return """
                       抱歉，请求过于频繁，已达到速率限制。
                       
                       建议：
                       • 请稍等片刻后再试
                       • 避免短时间内发送大量请求
                       
                       通常等待 1-2 分钟后即可恢复正常。
                       """;
            
            case "TIMEOUT_ERROR":
                return """
                       抱歉，请求超时了。
                       
                       可能的原因：
                       • 网络连接不稳定
                       • 服务器响应较慢
                       
                       建议：
                       • 请重试一次
                       • 如果问题持续，请稍后再试
                       """;
            
            case "AUTH_ERROR":
                return """
                       抱歉，身份验证失败。
                       
                       这是一个系统配置问题，请联系管理员检查：
                       • API Key 是否正确
                       • API Key 是否已过期
                       • 账户余额是否充足
                       """;
            
            default:
                return String.format("""
                       抱歉，遇到了一个技术问题，暂时无法处理您的请求。
                       
                       错误信息：%s
                       
                       建议：
                       • 请重试一次
                       • 如果问题持续，请联系技术支持
                       """, originalError != null ? originalError.substring(0, Math.min(100, originalError.length())) : "未知错误");
        }
    }

    /**
     * 重写 act 方法
     * 注意：数据库保存逻辑已在 runStream 方法中统一处理
     */
    @Override
    public String act() {
        // 调用父类的 act 方法
        return super.act();
    }

    /**
     * 流式运行（带数据库记忆）
     * 不使用 Spring AI 的自动工具调用，而是使用手动工具管理
     *
     * @param userPrompt 用户提示词
     * @param chatId     对话ID
     * @return SseEmitter
     */
    public SseEmitter runStream(String userPrompt, String chatId) {
        SseEmitter sseEmitter = new SseEmitter(300000L);
        this.chatId = chatId; // 保存 chatId 供 think() 使用
        this.historyLoaded = false; // 重置历史消息加载标志
        getMessageList().clear(); // 清空消息列表
        
        // 【新增】重置错误计数器（每次新对话都重置）
        errorCountMap.clear();
        
        // 【关键修复】重置状态和辅助组件，确保每次新对话都是从头开始
        setState(AgentState.RUNNING);
        getDeadLoopDetector().reset();
        getExecutionMonitor().start();

        Consumer<String> sendSse = (data) -> {
            try {
                sseEmitter.send(SseEmitter.event().data(data).build());
            } catch (IOException e) {
                log.error("SSE send error: " + e.getMessage());
            }
        };

        // 记录当前消息列表的起始位置（必须在 think() 加载历史消息之后！）
        // 这样保存时 subList(initialMessageCount, size) 只会包含：用户消息 + 助手回复
        // 历史消息不会被重复保存
        final int[] initialMessageCount = new int[1];
        initialMessageCount[0] = 0;
        
        // 标志位，记录是否已发送最终回复
        final boolean[] responseSent = new boolean[1];
        responseSent[0] = false;

        CompletableFuture.runAsync(() -> {
            try {
                // 添加用户消息
                getMessageList().add(new UserMessage(userPrompt));
                // 不需要发送用户消息到前端，前端已经显示了
                
                // 【关键修复】在添加用户消息后立即记录起始位置
                // 这样无论 think() 是否加载历史消息，都能正确包含用户消息
                initialMessageCount[0] = getMessageList().size() - 1;

                // 发送思考开始事件
                try {
                    sseEmitter.send(SseEmitter.event()
                        .name("thinking_start")
                        .data("{\"status\": \"started\"}"));
                } catch (IOException e) {
                    log.error("发送思考开始事件失败", e);
                }

                long thinkingStartTime = System.currentTimeMillis();

                // 执行多步推理和行动循环
                for (int i = 0; i < getMaxSteps(); i++) {
                    // Think - 使用流式思考（带实时步骤输出）
                    boolean shouldAct = false;
                    
                    // 获取最后一条用户消息
                    String lastUserInput = null;
                    for (int j = getMessageList().size() - 1; j >= 0; j--) {
                        org.springframework.ai.chat.messages.Message msg = getMessageList().get(j);
                        if (msg instanceof UserMessage) {
                            lastUserInput = ((UserMessage) msg).getText();
                            break;
                        }
                    }
                    
                    if (lastUserInput != null) {
                        // 判断问题类型
                        ThinkingChain.QuestionType type = QuestionClassifier.classify(lastUserInput);
                        log.info("问题分类: {} - {}", type, lastUserInput.length() > 50 ? lastUserInput.substring(0, 50) + "..." : lastUserInput);
                        
                        if (type == ThinkingChain.QuestionType.COMPLEX) {
                            // 复杂问题：使用流式思考
                            shouldAct = deepThinkStream(lastUserInput, (step) -> {
                                try {
                                    // 发送思考步骤到前端
                                    String stepJson = String.format(
                                        "{\"step\": \"%s\", \"timestamp\": %d}",
                                        escapeJson(step),
                                        System.currentTimeMillis()
                                    );
                                    sseEmitter.send(SseEmitter.event()
                                        .name("thinking_step")
                                        .data(stepJson));
                                } catch (IOException e) {
                                    log.error("发送思考步骤失败", e);
                                }
                            });
                        } else {
                            // 简单问题：直接思考，不输出步骤
                            shouldAct = think();
                        }
                    } else {
                        // 没有用户输入，使用默认思考
                        shouldAct = think();
                    }

                    // 计算思考时间并发送思考结束事件
                    long thinkingEndTime = System.currentTimeMillis();
                    double thinkingTime = (thinkingEndTime - thinkingStartTime) / 1000.0;
                    try {
                        sseEmitter.send(SseEmitter.event()
                            .name("thinking_end")
                            .data(String.format("{\"status\": \"completed\", \"time\": %.1f}", thinkingTime)));
                    } catch (IOException e) {
                        log.error("发送思考结束事件失败", e);
                    }

                    if (!shouldAct) {
                        // 没有工具调用，获取最终响应
                        String finalResponse = getLastAssistantMessageText();
                        sendThinkingAndMessage(sseEmitter, sendSse, finalResponse);
                        responseSent[0] = true; // 标记已发送
                        break;
                    }

                    // Act - 执行工具
                    log.info("开始执行工具调用");
                    final String[] actResult = new String[1];
                    final boolean[] actCompleted = new boolean[1];
                    final Exception[] actException = new Exception[1]; // 记录异常
                    actCompleted[0] = false;
                    
                    // 在单独的线程中执行工具，设置超时时间
                    Thread actThread = new Thread(() -> {
                        try {
                            actResult[0] = act();
                            actCompleted[0] = true;
                            log.info("工具执行完成");
                        } catch (Exception e) {
                            log.error("工具执行异常: {}", e.getMessage(), e);
                            actException[0] = e;
                            actResult[0] = "工具执行失败: " + e.getMessage();
                            actCompleted[0] = true;
                        }
                    });
                    
                    actThread.start();
                    
                    // 等待工具执行完成，最多等待30秒
                    try {
                        actThread.join(30000); // 30秒超时
                    } catch (InterruptedException e) {
                        log.error("等待工具执行被中断", e);
                    }
                    
                    if (!actCompleted[0]) {
                        // 超时了，中断线程
                        actThread.interrupt();
                        
                        // 获取最后调用的工具名称
                        String lastToolName = "未知工具";
                        try {
                            if (getToolCallChatResponse() != null && getToolCallChatResponse().hasToolCalls()) {
                                var toolCalls = getToolCallChatResponse().getResult().getOutput().getToolCalls();
                                if (!toolCalls.isEmpty()) {
                                    lastToolName = toolCalls.get(toolCalls.size() - 1).name();
                                }
                            }
                        } catch (Exception e) {
                            log.error("获取工具名称失败", e);
                        }
                        
                        log.warn("工具执行超时（30秒），工具: {}", lastToolName);
                        
                        // 超时后，直接回复用户，不再等待工具结果
                        String timeoutResponse = String.format(
                            "抱歉，工具 %s 执行时间过长（超过30秒）。让我直接回答你的问题。", 
                            lastToolName
                        );
                        
                        // 添加一个助手消息，包含超时提示
                        AssistantMessage timeoutMessage = new AssistantMessage(timeoutResponse);
                        getMessageList().add(timeoutMessage);
                        
                        // 发送超时提示给用户
                        sendThinkingAndMessage(sseEmitter, sendSse, timeoutResponse);
                        responseSent[0] = true;
                        break;
                    }
                    
                    // 检查是否结束
                    if (getState() == AgentState.FINISHED) {
                        // 获取最终响应并发送
                        String finalResponse = getLastAssistantMessageText();
                        if (finalResponse != null && !finalResponse.isEmpty()) {
                            sendThinkingAndMessage(sseEmitter, sendSse, finalResponse);
                            responseSent[0] = true; // 标记已发送
                        }
                        break;
                    }
                    
                    // 重置思考开始时间（为下一轮思考计时）
                    thinkingStartTime = System.currentTimeMillis();
                }

                // 【关键修复】循环结束后，如果还没有发送回复，发送最后的回复
                // 这样无论循环如何结束（达到最大步数、工具执行完成等），都会发送回复
                if (!responseSent[0]) {
                    String finalResponse = getLastAssistantMessageText();
                    if (finalResponse != null && !finalResponse.isEmpty()) {
                        sendThinkingAndMessage(sseEmitter, sendSse, finalResponse);
                    }
                }

                // 无论对话如何结束，都保存到数据库（只保存当前轮次新增的消息）
                if (chatId != null && !chatId.isEmpty()) {
                    try {
                        List<Message> currentMessages = getMessageList();
                        // 只获取当前轮次新增的消息（从 initialMessageCount[0] 开始）
                        List<Message> newMessages = currentMessages.subList(initialMessageCount[0], currentMessages.size());
                        List<Message> filteredMessages = filterMessages(newMessages);
                        databaseBasedChatMemory.add(chatId, filteredMessages);
                    } catch (Exception saveException) {
                        log.error("保存对话失败：{}", saveException.getMessage());
                    }
                }

                sseEmitter.complete();
            } catch (Exception e) {
                log.error("Stream error: " + e.getMessage(), e);
                sendSse.accept("错误：" + e.getMessage());
                // 异常发生时也尝试保存对话（只保存当前轮次新增的消息）
                if (chatId != null && !chatId.isEmpty()) {
                    try {
                        List<Message> currentMessages = getMessageList();
                        List<Message> newMessages = currentMessages.subList(initialMessageCount[0], currentMessages.size());
                        List<Message> filteredMessages = filterMessages(newMessages);
                        databaseBasedChatMemory.add(chatId, filteredMessages);
                    } catch (Exception saveException) {
                        log.error("保存对话失败：{}", saveException.getMessage());
                    }
                }
                sseEmitter.completeWithError(e);
            }
        });

        return sseEmitter;
    }

    /**
     * 获取最后一条助手消息的文本
     */
    private String getLastAssistantMessageText() {
        List<Message> messages = getMessageList();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage) {
                return ((AssistantMessage) msg).getText();
            }
        }
        return null;
    }

    /**
     * 过滤消息列表，只保留 USER 和 ASSISTANT 类型（用于保存到数据库）
     * 过滤掉：
     * 1. 系统消息、工具响应消息
     * 2. 内部提示（如 nextStepPrompt）
     * 3. 只有思考没有回复的消息（工具调用阶段的中间消息）
     * 4. 处理 AssistantMessage 中的思考内容，将思考步骤保存到 metadata，只保存回复部分到 content
     */
    private List<Message> filterMessages(List<Message> messages) {
        return messages.stream()
                .map(msg -> {
                    // 过滤掉 TOOL 响应消息（不保存到数据库）
                    if (msg.getMessageType() == MessageType.TOOL) {
                        return null;
                    }

                    if (msg instanceof UserMessage userMsg) {
                        // 过滤掉 nextStepPrompt（内部决策提示）
                        String text = userMsg.getText();
                        if (text != null && (
                                text.contains("基于工具") ||
                                text.contains("继续思考") ||
                                text.contains("基于工具执行结果") ||
                                text.contains("基于工具返回的结果做决策"))) {
                            return null; // 过滤掉内部提示
                        }
                    }
                    if (msg instanceof AssistantMessage assistantMsg) {
                        // 处理 AssistantMessage，提取回复部分和思考部分
                        String text = assistantMsg.getText();
                        if (text != null && text.contains("【思考】")) {
                            // 【关键修复】检查是否包含【回复】标记
                            // 如果只有【思考】没有【回复】，说明是工具调用阶段的中间消息，应该过滤掉
                            if (!text.contains("【回复】")) {
                                return null; // 过滤掉只有思考的消息
                            }

                            // 有【思考】和【回复】标记，提取思考内容保存到 metadata
                            String thinkingContent = extractContent(text, "【思考】", "【回复】");
                            String replyContent = extractReplyContent(text);

                            // 将思考内容按行分割，保存到 metadata
                            java.util.Map<String, Object> metadata = new java.util.HashMap<>(assistantMsg.getMetadata());
                            if (thinkingContent != null && !thinkingContent.isEmpty()) {
                                List<String> thinkingSteps = java.util.Arrays.stream(thinkingContent.split("\n"))
                                        .map(String::trim)
                                        .filter(line -> !line.isEmpty())
                                        .collect(Collectors.toList());
                                metadata.put("thinkingSteps", thinkingSteps);
                            }

                            // 【关键修复】创建新的 AssistantMessage，只包含回复内容，但保留 metadata
                            // 使用 replyContent 而不是原始 text
                            return new AssistantMessage(replyContent, metadata);
                        }
                    }
                    return msg;
                })
                .filter(msg -> msg != null)
                .collect(Collectors.toList());
    }

    /**
     * 解析并发送思考和回复内容（流式输出）
     * 支持 DeepSeek 风格的【思考】和【回复】格式
     * 如果包含这两个标记，分别流式发送 thinking 和 message 事件
     * 否则流式发送 message 事件
     */
    private void sendThinkingAndMessage(SseEmitter sseEmitter, Consumer<String> sendSse, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        
        // 检查是否包含【思考】标记
        if (content.contains("【思考】")) {
            // 检查是否包含【回复】标记
            if (content.contains("【回复】")) {
                // 完整格式：【思考】...【回复】...
                String thinkingContent = extractContent(content, "【思考】", "【回复】");
                String replyContent = extractReplyContent(content);

                // 流式发送思考内容（使用 thinking 事件名）
                if (thinkingContent != null && !thinkingContent.isEmpty()) {
                    sendStreamContent(sseEmitter, "thinking", thinkingContent);
                }

                // 流式发送回复内容（使用 message 事件名）
                if (replyContent != null && !replyContent.isEmpty()) {
                    sendStreamContent(sseEmitter, "message", replyContent);
                }
            } else {
                // 只有【思考】，没有【回复】（通常是工具调用阶段）
                // 这种情况下，不发送任何内容给用户
                // 因为工具执行后会有新的回复
            }
        } else {
            // 没有思考标记，直接流式发送回复内容（使用 message 事件名）
            sendStreamContent(sseEmitter, "message", content);
        }
    }

    /**
     * 流式发送内容（逐字发送，模拟打字效果）
     */
    private void sendStreamContent(SseEmitter sseEmitter, String eventName, String content) {
        try {
            // 按行分割内容
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    // 发送每一行
                    sseEmitter.send(SseEmitter.event().name(eventName).data(line).build());
                    // 短暂延迟，模拟流式输出（可选）
                    try {
                        Thread.sleep(50); // 50ms 延迟
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (IOException e) {
            log.error("流式发送内容失败: {}", e.getMessage());
        }
    }

    /**
     * 从内容中提取指定标记之间的内容
     */
    private String extractContent(String content, String startTag, String endTag) {
        int startIndex = content.indexOf(startTag);
        if (startIndex == -1) {
            return null;
        }
        startIndex += startTag.length();
        int endIndex = content.indexOf(endTag, startIndex);
        if (endIndex == -1) {
            return content.substring(startIndex).trim();
        }
        return content.substring(startIndex, endIndex).trim();
    }

    /**
     * 提取【回复】标记后的内容（用于数据库存储，只保存回复部分）
     */
    private String extractReplyContent(String content) {
        int replyIndex = content.indexOf("【回复】");
        if (replyIndex == -1) {
            return content.trim();
        }
        return content.substring(replyIndex + 4).trim();
    }

    /**
     * 转义 JSON 特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    public MonuoManus(ToolCallback[] availableTools) {
        super(availableTools);
    }
}
