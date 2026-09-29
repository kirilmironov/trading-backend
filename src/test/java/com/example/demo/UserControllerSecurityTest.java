package com.example.demo;

import com.example.demo.config.WebSecurityConfig;
import com.example.demo.controller.UserController;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(UserController.class)
@Import(WebSecurityConfig.class)
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void rejectsAnonymousBalanceRequests() throws Exception {
        mockMvc.perform(get("/api/users/balance").param("userId", "1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userRepository);
    }
}