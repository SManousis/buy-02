package com.example.userservice.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.userservice.dto.SellerSummaryResponse;
import com.example.userservice.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SellerControllerTest {
    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = org.mockito.Mockito.mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SellerController(userService)).build();
    }

    @Test
    void returnsOnlyThePublicSellerSummary() throws Exception {
        when(userService.listSellers()).thenReturn(List.of(new SellerSummaryResponse("seller-1", "alice")));

        mockMvc.perform(get("/sellers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("seller-1"))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].email").doesNotExist());

        verify(userService).listSellers();
    }
}
