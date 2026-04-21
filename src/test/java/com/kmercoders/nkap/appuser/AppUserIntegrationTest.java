package com.kmercoders.nkap.appuser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppUserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_duplicateEmail_returnsRegisterViewWithError() throws Exception {
        AppUser existingUser = new AppUser("taken@example.com", "somepassword");
        userRepository.save(existingUser);

        mockMvc.perform(post("/register")
                .param("email", "taken@example.com")
                .param("password", "anotherpassword"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("error"));
    }

    @Test
    void register_validCredentials_redirectsToHome() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "new@example.com")
                .param("password", "somepassword"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }
}