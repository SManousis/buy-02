package com.example.userservice.controller;

import com.example.userservice.dto.SellerSummaryResponse;
import com.example.userservice.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers")
@RequiredArgsConstructor
public class SellerController {
    private final UserService userService;

    @GetMapping
    public List<SellerSummaryResponse> listSellers() {
        return userService.listSellers();
    }
}
