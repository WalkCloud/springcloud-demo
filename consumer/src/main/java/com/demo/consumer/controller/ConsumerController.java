package com.demo.consumer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model; // 修正导入
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

@Controller
public class ConsumerController {

    @Autowired
    private RestTemplate restTemplate;

    @GetMapping("/")
    public String getProvidersInfo(Model model) { // 使用正确的Model类
        model.addAttribute("providerA",
                restTemplate.getForObject("http://provider-a/info", Object.class));
        model.addAttribute("providerB",
                restTemplate.getForObject("http://provider-b/info", Object.class));
        return "index";
    }
}