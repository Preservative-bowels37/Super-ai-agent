package com.monuo.superaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Map;

/**
 * 自定义 Re2 Advisor
 * 通过重新阅读和推理来提高大模型的推理能力
 * Re2: Re-Reading and Reasoning
 */
@Slf4j
public class ReReadingAdvisor implements BaseAdvisor {

	private static final String DEFAULT_RE2_TEMPLATE = """
			{re2_input_query}
			Read the question again: {re2_input_query}
			""";

	private final String re2Template;
	private int order = 0;

	public ReReadingAdvisor() {
		this(DEFAULT_RE2_TEMPLATE);
	}

	public ReReadingAdvisor(String re2Template) {
		this.re2Template = re2Template;
	}

	/**
	 * 在执行请求前，增强原始提示词
	 * @param chatClientRequest 原始请求
	 * @param advisorChain 顾问链
	 * @return 增强后的请求
	 */
	@Override
	public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
		try {
			String re2InputQuery = extractOriginalQuery(chatClientRequest);
			String enhancedPrompt = createEnhancedPrompt(re2InputQuery);

			log.debug("Re2 Advisor: 原始查询 -> {}", re2InputQuery);
			log.debug("Re2 Advisor: 增强提示词 -> {}", enhancedPrompt);

			return chatClientRequest.mutate()
					.prompt(chatClientRequest.prompt().augmentUserMessage(enhancedPrompt))
					.build();
		} catch (Exception e) {
			log.warn("Re2 Advisor: 处理请求时发生错误，使用原始请求 -> {}", e.getMessage());
			return chatClientRequest;
		}
	}

	/**
	 * 处理响应后的逻辑
	 * @param chatClientResponse 响应对象
	 * @param advisorChain 顾问链
	 * @return 处理后的响应
	 */
	@Override
	public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
		// 可以在这里添加对响应的后处理逻辑
		// 例如：验证推理质量、提取关键信息等
		return chatClientResponse;
	}

	/**
	 * 从原始请求中提取查询内容
	 * @param chatClientRequest 请求对象
	 * @return 原始查询文本
	 */
	private String extractOriginalQuery(ChatClientRequest chatClientRequest) {
		return chatClientRequest.prompt().getUserMessage().getText();
	}

	/**
	 * 创建增强的提示词
	 * @param re2InputQuery 原始查询
	 * @return 增强后的提示词
	 */
	private String createEnhancedPrompt(String re2InputQuery) {
		Map<String, Object> variables = Map.of("re2_input_query", re2InputQuery);
		return PromptTemplate.builder()
				.template(this.re2Template)
				.variables(variables)
				.build()
				.render();
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	/**
	 * 设置顾问的执行顺序
	 * @param order 执行顺序
	 * @return 当前顾问实例
	 */
	public ReReadingAdvisor withOrder(int order) {
		this.order = order;
		return this;
	}

	/**
	 * 创建Re2 Advisor构建器
	 * @return 构建器实例
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Re2 Advisor构建器
	 */
	public static class Builder {
		private String template = DEFAULT_RE2_TEMPLATE;
		private int order = 0;

		/**
		 * 自定义Re2模板
		 * @param template 自定义模板
		 * @return 构建器
		 */
		public Builder template(String template) {
			this.template = template;
			return this;
		}

		/**
		 * 设置执行顺序
		 * @param order 执行顺序
		 * @return 构建器
		 */
		public Builder order(int order) {
			this.order = order;
			return this;
		}

		/**
		 * 构建Re2 Advisor实例
		 * @return Re2 Advisor实例
		 */
		public ReReadingAdvisor build() {
			ReReadingAdvisor advisor = new ReReadingAdvisor(this.template);
			advisor.order = this.order;
			return advisor;
		}
	}
}