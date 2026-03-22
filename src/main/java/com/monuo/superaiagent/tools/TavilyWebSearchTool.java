package com.monuo.superaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tavily 网页搜索工具
 * 专为 AI Agent 设计的搜索引擎，返回结果已清洗，对 LLM 友好
 */
@Slf4j
public class TavilyWebSearchTool {

    // Tavily Search API 地址
    private static final String TAVILY_API_URL = "https://api.tavily.com/search";

    private final String apiKey;

    // 每次搜索返回的最大结果数
    private final int maxResults;

    public TavilyWebSearchTool(String apiKey) {
        this(apiKey, 5);//默认maxResults = 5
    }

    public TavilyWebSearchTool(String apiKey, int maxResults) {
        this.apiKey = apiKey;
        this.maxResults = maxResults;
    }

    @Tool(description = """
            Search the web for current information, news, or facts using Tavily AI Search Engine.
            This tool is optimized for AI agents and returns clean, relevant results.
            
            Use this tool when you need to:
            - Find current information or recent news
            - Look up facts or data from the internet
            - Get up-to-date information on any topic
            
            The tool automatically returns the most relevant results (up to 5).
            """)
    public String searchWeb(
            @ToolParam(description = "Search query - be specific and clear about what you're looking for") String query) {
        return executeSearch(query, "basic", null, this.maxResults);
    }

    /**
     * 高级搜索：支持更多参数定制
     * 注意：这个方法不应该被 LLM 直接调用，仅供内部使用或特殊场景
     */
    @Tool(description = """
            Advanced web search - USE ONLY when user explicitly requests specific parameters.
            For most searches, use searchWeb instead.
            
            Use this ONLY when user asks for:
            - Recent news with specific time range (e.g., "news from this week")
            - Specific number of results (e.g., "give me 10 results")
            - Deep/comprehensive search
            
            Parameters:
            - query: Search keywords (required)
            - searchDepth: "basic" for quick results, "advanced" for comprehensive (optional)
            - timeRange: "day", "week", "month", or "year" for recent results (optional)
            - maxResults: Number of results between 1-10 (optional)
            
            Examples:
            - "Search AI news from this week" → timeRange="week"
            - "Search AI, give me 10 results" → maxResults=10
            - "Deep search about quantum computing" → searchDepth="advanced"
            """)
    public String searchWebAdvanced(
            @ToolParam(description = "Search query - what to search for") String query,
            @ToolParam(description = "Search depth: 'basic' or 'advanced'. Use 'basic' for most searches.", required = false) String searchDepth,
            @ToolParam(description = "Time range: 'day', 'week', 'month', or 'year'. Only use when user asks for recent results.", required = false) String timeRange,
            @ToolParam(description = "Number of results (1-10). Only use when user specifies a number.", required = false) Integer maxResults) {

        // 参数验证和默认值处理
        if (searchDepth != null && !searchDepth.equals("basic") && !searchDepth.equals("advanced")) {
            log.warn("Invalid searchDepth: {}, using 'basic'", searchDepth);
            searchDepth = "basic";
        }
        
        if (timeRange != null) {
            String[] validRanges = {"day", "week", "month", "year"};
            boolean valid = false;
            for (String range : validRanges) {
                if (range.equals(timeRange)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                log.warn("Invalid timeRange: {}, ignoring", timeRange);
                timeRange = null;
            }
        }

        // 校验 maxResults 范围
        if (maxResults != null) {
            if (maxResults < 1) {
                log.warn("maxResults {} is too small, using minimum: 1", maxResults);
                maxResults = 1;
            } else if (maxResults > 10) {
                log.warn("maxResults {} exceeds limit, using maximum: 10", maxResults);
                maxResults = 10;
            }
        }

        return executeSearch(query, searchDepth, timeRange, maxResults);
    }

    /**
     * 执行搜索的公共方法
     */
    private String executeSearch(String query, String searchDepth, String timeRange, Integer maxResults) {
        // 参数验证
        if (query == null || query.trim().isEmpty()) {
            log.error("Search query is empty");
            return "搜索失败：查询关键词不能为空";
        }
        
        // 设置默认值
        String finalSearchDepth = (searchDepth != null && !searchDepth.isEmpty()) ? searchDepth : "basic";
        Integer finalMaxResults = (maxResults != null && maxResults > 0) ? maxResults : this.maxResults;
        
        log.info("Tavily search - query: {}, depth: {}, timeRange: {}, maxResults: {}",
                query, finalSearchDepth, timeRange, finalMaxResults);

        // 构建请求参数
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("api_key", apiKey);
        paramMap.put("query", query);
        paramMap.put("search_depth", finalSearchDepth);
        paramMap.put("max_results", finalMaxResults);
        paramMap.put("include_answer", true);
        paramMap.put("include_raw_content", false);

        if (timeRange != null && !timeRange.isEmpty()) {
            paramMap.put("time_range", timeRange);
        }

        try {
            // POST 请求，设置15秒超时
            String response = HttpUtil.createPost(TAVILY_API_URL)
                    .body(JSONUtil.toJsonStr(paramMap))
                    .timeout(15000)
                    .execute()
                    .body();
            
            log.debug("Tavily API response received");

            JSONObject jsonObject = JSONUtil.parseObj(response);

            // 检查是否有错误
            if (jsonObject.containsKey("detail")) {
                String error = jsonObject.getStr("detail");
                log.error("Tavily API error: {}", error);
                return "搜索错误: " + error;
            }

            // 优先返回 AI 生成的答案
            String answer = jsonObject.getStr("answer");
            if (answer != null && !answer.trim().isEmpty()) {
                log.info("Tavily search completed with AI answer");
                return "AI 摘要:\n" + answer + "\n\n---\n\n搜索结果:\n" +
                        formatResults(jsonObject.getJSONArray("results"));
            }

            // 返回搜索结果列表
            JSONArray results = jsonObject.getJSONArray("results");
            if (results == null || results.isEmpty()) {
                log.info("Tavily search completed with no results");
                return "未找到搜索结果。";
            }

            log.info("Tavily search completed with {} results", results.size());
            return formatResults(results);

        } catch (cn.hutool.http.HttpException e) {
            log.error("Tavily HTTP request failed for query: {}", query, e);
            if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                return "搜索超时（15秒），请稍后重试或使用更具体的搜索关键词。";
            }
            return "搜索请求失败: " + e.getMessage();
        } catch (Exception e) {
            log.error("Tavily search failed for query: {}", query, e);
            return "搜索时发生错误: " + e.getMessage();
        }
    }

    /**
     * 格式化搜索结果为易读的字符串
     */
    private String formatResults(JSONArray results) {
        if (results == null || results.isEmpty()) {
            return "No search results found.";
        }

        int limit = Math.min(maxResults, results.size());

        return results.stream()
                .limit(limit)
                .map(obj -> {
                    JSONObject item = (JSONObject) obj;
                    String title = item.getStr("title", "无标题");
                    String url = item.getStr("url", "无链接");
                    String content = item.getStr("content", "无内容");

                    return String.format("【%s】\n链接：%s\n摘要：%s", title, url, content);
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
