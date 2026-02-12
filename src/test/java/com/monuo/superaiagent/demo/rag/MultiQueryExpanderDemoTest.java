package com.monuo.superaiagent.demo.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.rag.Query;
import java.util.List;

@SpringBootTest
class MultiQueryExpanderDemoTest {

    @Resource
    private MultiQueryExpanderDemo multiQueryExpanderDemo;

    @Test
    void expand() {
        List<Query> queries = multiQueryExpanderDemo.expand("啥是程序员monuo啊啊啊啊啊啊啊？！请回答我哈哈哈哈");
        Assertions.assertNotNull(queries);
    }
}