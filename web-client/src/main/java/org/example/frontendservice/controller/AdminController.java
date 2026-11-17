package org.example.frontendservice.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Controller
public class AdminController {

    public AdminController(RestTemplate restTemplate) {
    }

    @GetMapping("/admin")
    public String adminPage(HttpSession session) {

        if (session.getAttribute("token") == null) {
            return "redirect:/login";
        }

        Object roles = session.getAttribute("roles");

        if (!(roles instanceof List<?> roleList)
                || !roleList.contains("ROLE_ADMIN")) {
            return "redirect:/catalog";
        }

        return "admin/index";
    }
}