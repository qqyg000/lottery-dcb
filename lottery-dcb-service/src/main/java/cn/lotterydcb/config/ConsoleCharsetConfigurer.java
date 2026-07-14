package cn.lotterydcb.config;

import java.io.Console;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;

/**
 * 在 Spring Boot 初始化日志系统前选择与当前终端一致的控制台编码
 */
public final class ConsoleCharsetConfigurer {

    private static final String SPRING_PROPERTY = "logging.charset.console";
    private static final String LOGGING_SYSTEM_PROPERTY = "CONSOLE_LOG_CHARSET";

    private ConsoleCharsetConfigurer() {
    }

    public static String configure() {
        String explicitlyConfigured = firstSupportedOrNull(
                System.getProperty(SPRING_PROPERTY),
                System.getProperty(LOGGING_SYSTEM_PROPERTY),
                System.getenv(LOGGING_SYSTEM_PROPERTY)
        );
        String charset = explicitlyConfigured != null
                ? explicitlyConfigured
                : selectRuntimeCharset(
                isIdeaProcess(),
                System.getProperty("console.encoding"),
                consoleCharset(),
                System.getProperty("stdout.encoding"),
                System.getProperty("sun.stdout.encoding"),
                System.getProperty("native.encoding"),
                System.getProperty("file.encoding")
        );

        // 同时设置 Spring 属性和日志系统属性，确保启动横幅之后的日志也使用相同编码
        System.setProperty(SPRING_PROPERTY, charset);
        System.setProperty(LOGGING_SYSTEM_PROPERTY, charset);
        return charset;
    }

    static String firstSupported(String... candidates) {
        String charset = firstSupportedOrNull(candidates);
        return charset == null ? "UTF-8" : charset;
    }

    static String selectRuntimeCharset(
            boolean ideaProcess,
            String consoleEncoding,
            String attachedConsoleEncoding,
            String stdoutEncoding,
            String sunStdoutEncoding,
            String nativeEncoding,
            String fileEncoding
    ) {
        if (ideaProcess) {
            // IDEA 默认使用系统编码解析 Run 控制台，因此优先匹配 native.encoding
            return firstSupported(
                    consoleEncoding,
                    attachedConsoleEncoding,
                    nativeEncoding,
                    stdoutEncoding,
                    sunStdoutEncoding,
                    fileEncoding,
                    "UTF-8"
            );
        }
        return firstSupported(
                consoleEncoding,
                attachedConsoleEncoding,
                stdoutEncoding,
                sunStdoutEncoding,
                nativeEncoding,
                fileEncoding,
                "UTF-8"
        );
    }

    public static boolean isIdeaProcess() {
        String classPath = System.getProperty("java.class.path", "");
        return System.getProperty("idea.launcher.port") != null
                || System.getProperty("idea.vendor.name") != null
                || System.getenv("IDEA_INITIAL_DIRECTORY") != null
                || classPath.contains("idea_rt.jar");
    }

    private static String firstSupportedOrNull(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                return Charset.forName(candidate.trim()).name();
            } catch (IllegalCharsetNameException | java.nio.charset.UnsupportedCharsetException ignored) {
                // 忽略无效的手工配置并继续探测可用编码
            }
        }
        return null;
    }

    private static String consoleCharset() {
        Console console = System.console();
        return console == null ? null : console.charset().name();
    }
}
