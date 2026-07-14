package cn.lotterydcb;

import cn.lotterydcb.config.ConsoleCharsetConfigurer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LotteryDcbApplication {

    public static void main(String[] args) {
        String consoleCharset = ConsoleCharsetConfigurer.configure();
        String launchMode = ConsoleCharsetConfigurer.isIdeaProcess() ? "IDEA" : "STANDARD";
        System.out.println("[lottery-dcb] console charset=" + consoleCharset + ", launch=" + launchMode);
        SpringApplication.run(LotteryDcbApplication.class, args);
    }

}
