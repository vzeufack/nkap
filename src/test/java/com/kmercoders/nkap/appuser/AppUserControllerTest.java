package com.kmercoders.nkap.appuser;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AppUserController.class)
@Import(SecurityConfig.class)
class AppUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppUserService userService;

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
        when(userService.saveUser(any(AppUser.class)))
            .thenThrow(new RuntimeException("Username already exists"));

        mockMvc.perform(post("/register")
                .param("email", "existing@example.com")
                .param("password", "somepassword"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("error"));
    }

    @Test
    @WithMockUser
    void register_authenticatedUser_redirectsToHome() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }

    @Test
    void register_unauthenticatedUser_returnsRegisterView() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"));
    }
}