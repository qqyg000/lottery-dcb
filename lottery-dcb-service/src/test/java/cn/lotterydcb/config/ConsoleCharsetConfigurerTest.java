package cn.lotterydcb.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsoleCharsetConfigurerTest {

    @Test
    void shouldUseFirstSupportedCharset() {
        assertEquals("GBK", ConsoleCharsetConfigurer.firstSupported("not-a-charset", "GBK", "UTF-8"));
    }

    @Test
    void shouldFallbackToUtf8WhenCandidatesAreEmpty() {
        assertEquals("UTF-8", ConsoleCharsetConfigurer.firstSupported(null, "", "  "));
    }

    @Test
    void ideaShouldPreferNativeEncodingOverRedirectedStdoutEncoding() {
        assertEquals("GBK", ConsoleCharsetConfigurer.selectRuntimeCharset(
                true,
                null,
                null,
                "UTF-8",
                "UTF-8",
                "GBK",
                "UTF-8"
        ));
    }

    @Test
    void standardLaunchShouldPreferStdoutEncoding() {
        assertEquals("UTF-8", ConsoleCharsetConfigurer.selectRuntimeCharset(
                false,
                null,
                null,
                "UTF-8",
                "UTF-8",
                "GBK",
                "GBK"
        ));
    }
}
