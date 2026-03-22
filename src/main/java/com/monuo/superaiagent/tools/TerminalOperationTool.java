package com.monuo.superaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 终端操作工具
 */
@Slf4j
public class TerminalOperationTool {

    @Tool(description = "Execute a command in the terminal. Note: This tool has a 20-second timeout. Do not use for long-running commands like servers or interactive programs.")
    public String executeTerminalCommand(
            @ToolParam(description = "Command to execute in the terminal. Avoid long-running commands.") String command) {
        
        log.info("执行终端命令: {}", command);
        final StringBuilder output = new StringBuilder();
        final StringBuilder errorOutput = new StringBuilder();
        Process process = null;
        
        try {
            // 使用 ProcessBuilder 执行命令
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", command);
            builder.redirectErrorStream(false); // 分别处理标准输出和错误输出
            
            process = builder.start();
            final Process finalProcess = process; // 创建 final 引用供 lambda 使用
            
            // 读取标准输出
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(finalProcess.getInputStream(), "GBK"))) { // 使用GBK编码处理中文
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("读取标准输出失败: {}", e.getMessage());
                }
            });
            
            // 读取错误输出
            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(finalProcess.getErrorStream(), "GBK"))) { // 使用GBK编码处理中文
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.error("读取错误输出失败: {}", e.getMessage());
                }
            });
            
            outputThread.start();
            errorThread.start();
            
            // 等待进程完成，最多等待20秒
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            
            if (!finished) {
                // 超时，强制终止进程
                process.destroyForcibly();
                log.warn("命令执行超时（20秒），已强制终止: {}", command);
                return "命令执行超时（20秒）。如果这是一个长时间运行的命令，请考虑在后台执行或使用其他方式。\n" +
                       "已捕获的输出:\n" + output.toString();
            }
            
            // 等待输出线程完成
            outputThread.join(2000);
            errorThread.join(2000);
            
            int exitCode = process.exitValue();
            
            // 构建返回结果
            StringBuilder result = new StringBuilder();
            
            if (output.length() > 0) {
                result.append("标准输出:\n").append(output.toString());
            }
            
            if (errorOutput.length() > 0) {
                result.append("\n错误输出:\n").append(errorOutput.toString());
            }
            
            if (exitCode != 0) {
                result.append("\n命令执行失败，退出码: ").append(exitCode);
            } else {
                if (result.length() == 0) {
                    result.append("命令执行成功，无输出");
                }
            }
            
            log.info("命令执行完成，退出码: {}", exitCode);
            return result.toString();
            
        } catch (IOException e) {
            log.error("执行命令失败: {}", e.getMessage());
            return "执行命令时发生IO错误: " + e.getMessage();
        } catch (InterruptedException e) {
            log.error("命令执行被中断: {}", e.getMessage());
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return "命令执行被中断: " + e.getMessage();
        } finally {
            // 确保进程被清理
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
