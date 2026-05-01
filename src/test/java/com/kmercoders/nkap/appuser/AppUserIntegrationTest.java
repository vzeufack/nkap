package com.kmercoders.nkap.appuser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppUserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_blankEmail_returnsRegisterViewWithFieldError() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "")
                .param("password", "somepassword"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("appUserDTO", "email"));
    }

    @Test
    void register_invalidEmail_returnsRegisterViewWithFieldError() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "abc")
                .param("password", "somepassword"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("appUserDTO", "email"));
    }

    @Test
    void register_blankPassword_returnsRegisterViewWithFieldError() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "test@test.com")
                .param("password", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("appUserDTO", "password"));
    }

    @Test
    void register_invalidPassword_returnsRegisterViewWithFieldError() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "test@test.com")
                .param("password", "12345"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("appUserDTO", "password"));
    }

    @Test
    void register_blankEmailAndPassword_returnsRegisterViewWithFieldError() throws Exception {
        mockMvc.perform(post("/register")
                .param("email", "")
                .param("password", ""))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeHasFieldErrors("appUserDTO", "email", "password"));
    }

    @Test
    void register_existingEmail_returnsRegisterViewWithError() throws Exception {
        AppUser existingUser = new AppUser("existing@example.com", passwordEncoder.encode("somepassword"));
        userRepository.save(existingUser);

        mockMvc.perform(post("/register")
                .param("email", "existing@example.com")
                .param("password", "somepassword"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("error"));
    }

    private String budgetRedirectUrl() {
        LocalDate now = LocalDate.now();
        return "/budgets/" + now.getMonth() + "/" + now.getYear();
    }

    @Test
    @WithMockUser
    void register_authenticatedUser_redirectsToHome() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(budgetRedirectUrl()));
    }

    @Test
    void register_unauthenticatedUser_returnsRegisterView() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"));
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
            .andExpect(redirectedUrl(budgetRedirectUrl()));
    }

    @Test
    void login_invalidCredentials_returnsLoginViewWithError() throws Exception {
        mockMvc.perform(post("/login")
                .param("email", "user@example.com")
                .param("password", "wrongpassword"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void login_validCredentials_redirectsToHome() throws Exception {
        AppUser user = new AppUser("user@example.com", passwordEncoder.encode("somepassword"));
        userRepository.save(user);

        mockMvc.perform(post("/login")
                .param("email", "user@example.com")
                .param("password", "somepassword"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(budgetRedirectUrl()));
    }

    @Test
    @WithMockUser
    void login_authenticatedUser_redirectsToHome() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(budgetRedirectUrl()));
    }

    @Test
    void login_unauthenticatedUser_returnsLoginView() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"));
    }
}