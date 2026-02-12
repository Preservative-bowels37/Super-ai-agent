package com.monuo.superaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 *  恋爱大师应用加载器
 */
@Component
@Slf4j
public class LoveAppDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    LoveAppDocumentLoader (ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 加载所有markdown文件
     * @return
     */
    public List<Document> loadMarkdowns () {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath*:document/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                // 从文件名中提取状态：格式为 "xxx - 状态篇.md"
                // 例如: "恋爱常见问题和回答 - 单身篇.md" → status = "单身"
                String status = "";
                int dashIndex = filename.lastIndexOf(" - ");
                int suffixIndex = filename.lastIndexOf("篇.md");
                if (dashIndex != -1 && suffixIndex != -1) {
                    status = filename.substring(dashIndex + 3, suffixIndex);
                }
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("status", status)
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(markdownDocumentReader.get());
            }
        } catch (Exception e) {
            log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }

}
