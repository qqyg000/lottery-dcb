package cn.lotterydcb.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 确保 IDEA 和可执行 JAR 两种启动方式都能打开前端首页
 */
@Controller
public class FrontendController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
