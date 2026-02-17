package com.monuo.superaiagent.config;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.monuo.superaiagent.rag.LoveAppDocumentLoader;
import com.monuo.superaiagent.rag.MyKeywordEnricher;
import com.monuo.superaiagent.rag.MyTokenTextSplitter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 恋爱大师向量数据库配置（懒加载 + 缓存版本）
 * 对比原版：启动时不立即加载，只有首次使用时才初始化，并支持缓存
 */
//@Configuration
@Slf4j
public class LoveAppStoreConfig {

    private static final String CACHE_FILE = "loveapp-vectorstore-cache.ser";
    private static final ReentrantLock initLock = new ReentrantLock();

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;

    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    @Lazy
    private DashScopeEmbeddingModel dashscopeEmbeddingModel;

    private volatile VectorStore vectorStore;

    @Bean
    public VectorStore loveAppVectorStore() {
        return getVectorStore();
    }

    /**
     * 获取向量存储，懒加载 + 缓存
     */
    public VectorStore getVectorStore() {
        if (vectorStore == null) {
            initLock.lock();
            try {
                if (vectorStore == null) {
                    vectorStore = initVectorStore();
                }
            } finally {
                initLock.unlock();
            }
        }
        return vectorStore;
    }

    /**
     * 初始化向量库：优先从缓存加载，否则加载文档
     */
    private VectorStore initVectorStore() {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();

        // 1. 尝试从缓存加载
        if (loadFromCache(simpleVectorStore)) {
            log.info("✅ 从缓存加载向量库成功，秒级启动！");
            return simpleVectorStore;
        }

        // 2. 缓存不存在，加载文档
        log.info("📚 开始加载文档到向量库...");
        long startTime = System.currentTimeMillis();

        List<Document> documents = loveAppDocumentLoader.loadMarkdowns();
        // 自主切分文档（如需启用）
        // documents = myTokenTextSplitter.splitCustomized(documents);
        // 自动补充关键词元信息
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documents);

        simpleVectorStore.add(enrichedDocuments);

        // 3. 保存到缓存
        saveToCache(enrichedDocuments);

        long costTime = System.currentTimeMillis() - startTime;
        log.info("📚 文档加载完成，共 {} 条，耗时 {} ms", enrichedDocuments.size(), costTime);

        return simpleVectorStore;
    }

    /**
     * 从文件缓存加载向量库
     */
    @SuppressWarnings("unchecked")
    private boolean loadFromCache(SimpleVectorStore simpleVectorStore) {
        Path cachePath = Paths.get(CACHE_FILE);
        if (!Files.exists(cachePath)) {
            return false;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(CACHE_FILE))) {
            List<Document> documents = (List<Document>) ois.readObject();
            if (documents != null && !documents.isEmpty()) {
                simpleVectorStore.add(documents);
                return true;
            }
        } catch (Exception e) {
            log.warn("⚠️ 从缓存加载失败: {}，将重新加载文档", e.getMessage());
            try {
                Files.deleteIfExists(cachePath);
            } catch (IOException ignored) {}
        }
        return false;
    }

    /**
     * 保存向量库到文件缓存
     */
    private void saveToCache(List<Document> documents) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CACHE_FILE))) {
            oos.writeObject(documents);
            log.info("💾 向量库已缓存到文件: {}", CACHE_FILE);
        } catch (Exception e) {
            log.warn("⚠️ 保存缓存失败: {}", e.getMessage());
        }
    }

}
