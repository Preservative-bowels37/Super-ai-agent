package com.monuo.superaiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 恋爱场景图片搜索工具
 *
 * 提供恋爱场景专用的图片搜索接口：
 * - 情侣头像搜索
 * - 浪漫背景图搜索
 * - 约会地点参考图搜索
 * - 礼物推荐图搜索
 *
 * 使用 Pexels API 进行图片搜索
 */
@Component
@Slf4j
public class RomanceImageSearchTool {

    // Pexels API 密钥 - 从环境变量读取
    private static final String API_KEY = System.getenv("PEXELS_API_KEY");

    // Pexels 常规搜索接口
    private static final String API_URL = "https://api.pexels.com/v1/search";

    /**
     * 搜索情侣头像
     *
     * @param style 风格（如：可爱、浪漫、简约、动漫）
     * @return 图片 URL 列表（逗号分隔）
     */
    @Tool(description = "搜索情侣头像图片，风格可选：可爱、浪漫、简约、动漫、写实等")
    public String searchCoupleAvatar(@ToolParam(description = "头像风格，如：可爱、浪漫、简约、动漫") String style) {
        String query = "couple avatar " + style;
        log.info("搜索情侣头像，风格：{}", style);
        return searchImages(query);
    }

    /**
     * 搜索浪漫背景图
     *
     * @param theme 主题（如：星空、樱花、海边、日落）
     * @return 图片 URL 列表（逗号分隔）
     */
    @Tool(description = "搜索浪漫背景图，主题可选：星空、樱花、海边、日落、雪山、花海等")
    public String searchRomanticBackground(@ToolParam(description = "背景图主题，如：星空、樱花、海边、日落") String theme) {
        String query = "romantic background " + theme;
        log.info("搜索浪漫背景图，主题：{}", theme);
        return searchImages(query);
    }

    /**
     * 搜索约会穿搭参考
     *
     * @param gender 性别（male/female）
     * @param occasion 场合（如：第一次约会、纪念日、日常约会）
     * @return 图片 URL 列表（逗号分隔）
     */
    @Tool(description = "搜索约会穿搭参考图")
    public String searchDateOutfit(
            @ToolParam(description = "性别：male(男) 或 female(女)") String gender,
            @ToolParam(description = "场合：第一次约会、纪念日、日常约会等") String occasion
    ) {
        String genderStr = gender.equals("male") ? "men" : "women";
        String query = genderStr + " outfit for " + occasion;
        log.info("搜索约会穿搭参考：性别={}，场合={}", gender, occasion);
        return searchImages(query);
    }

    /**
     * 搜索礼物推荐
     *
     * @param recipient 接收者（boyfriend/girlfriend）
     * @param occasion 场合（如：生日、纪念日、情人节、圣诞节）
     * @return 图片 URL 列表（逗号分隔）
     */
    @Tool(description = "搜索礼物推荐图片")
    public String searchGiftIdea(
            @ToolParam(description = "接收者：boyfriend(男友) 或 girlfriend(女友)") String recipient,
            @ToolParam(description = "场合：生日、纪念日、情人节、圣诞节等") String occasion
    ) {
        String recipientStr = recipient.equals("boyfriend") ? "boyfriend" : "girlfriend";
        String query = "gift for " + recipientStr + " " + occasion;
        log.info("搜索礼物推荐：接收者={}，场合={}", recipient, occasion);
        return searchImages(query);
    }

    /**
     * 搜索约会地点参考图
     *
     * @param locationType 地点类型（如：餐厅、咖啡厅、公园、海边、游乐园）
     * @return 图片 URL 列表（逗号分隔）
     */
    @Tool(description = "搜索约会地点参考图，地点类型可选：餐厅、咖啡厅、公园、海边、游乐园、美术馆等")
    public String searchDateLocationPhoto(
            @ToolParam(description = "地点类型，如：餐厅、咖啡厅、公园、海边、游乐园") String locationType
    ) {
        String query = "romantic date location " + locationType;
        log.info("搜索约会地点参考图：类型={}", locationType);
        return searchImages(query);
    }

    /**
     * 搜索纪念日庆祝创意
     *
     * @param type 类型（如：布置、蛋糕、礼物包装、拍照姿势）
     * @return 图片 URL 列表（逗号分隔）
     */
    @Tool(description = "搜索纪念日庆祝创意图片")
    public String searchAnniversaryIdea(
            @ToolParam(description = "类型：布置、蛋糕、礼物包装、拍照姿势等") String type
    ) {
        String query = "anniversary celebration " + type;
        log.info("搜索纪念日庆祝创意：类型={}", type);
        return searchImages(query);
    }

    /**
     * 通用图片搜索方法
     *
     * @param query 搜索关键词
     * @return 图片 URL 列表（逗号分隔）
     */
    private String searchImages(String query) {
        try {
            List<String> imageUrls = searchMediumImages(query);
            if (imageUrls.isEmpty()) {
                return "未找到相关图片，请尝试其他关键词。";
            }
            return String.join(",", imageUrls);
        } catch (Exception e) {
            log.error("搜索图片失败：query={}", query, e);
            return "搜索图片失败：" + e.getMessage();
        }
    }

    /**
     * 搜索中等尺寸的图片列表
     *
     * @param query 搜索关键词
     * @return 图片 URL 列表
     */
    private List<String> searchMediumImages(String query) {
        // 设置请求头（包含 API 密钥）
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", API_KEY);

        // 设置请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);
        params.put("size", "medium");

        // 发送 GET 请求
        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .execute()
                .body();

        // 解析响应 JSON
        return JSONUtil.parseObj(response)
                .getJSONArray("photos")
                .stream()
                .map(photoObj -> (JSONObject) photoObj)
                .map(photoObj -> photoObj.getJSONObject("src"))
                .map(photo -> photo.getStr("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}
