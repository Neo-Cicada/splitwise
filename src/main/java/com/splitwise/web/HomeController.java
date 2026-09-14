package com.splitwise.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("greeting", "Splitwise is running");
        model.addAttribute("sampleAmount", new BigDecimal("12345.60"));
        return "index";
    }

    /**
     * Proves the flash-message round trip: set the attribute, redirect, and the
     * alerts fragment in the layout renders it on the next request. Every form
     * in this project follows this pattern.
     */
    @PostMapping("/demo/flash")
    public String demoFlash(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("success", "Flash messages are wired up.");
        return "redirect:/";
    }
}
