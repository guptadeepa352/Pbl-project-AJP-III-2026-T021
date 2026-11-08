package com.mca.securecloudstorage.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProtectedTestController {
    @GetMapping("/api/test/protected")
    public String protectedTestEndpoint() {
        return "authenticated";
    }
}
