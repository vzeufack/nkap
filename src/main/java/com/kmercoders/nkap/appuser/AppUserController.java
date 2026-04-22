package com.kmercoders.nkap.appuser;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Controller
public class AppUserController {
    private static final String REGISTER = "register";
    private static final String ERROR = "error";
    private AppUserService userService;
    
    public AppUserController(AppUserService userService) {
        this.userService = userService;
    }

    private boolean isFullyAuthenticated(Authentication authentication) {
        return authentication != null 
            && authentication.isAuthenticated() 
            && !(authentication instanceof AnonymousAuthenticationToken);
    }
    
    @GetMapping(value = "/login")
    public String getLogin(ModelMap model, Authentication authentication) {
        if (isFullyAuthenticated(authentication)) {
            return "redirect:/";
        }
        
        AppUserDTO appUserDTO = new AppUserDTO();
        model.put("appUserDTO", appUserDTO);
        
        return "login";
    }
   
    @GetMapping(value = "/register")
    public String getRegister (ModelMap model, Authentication authentication) {
        if (isFullyAuthenticated(authentication)) {
            return "redirect:/";
        }

        AppUserDTO appUserDTO = new AppUserDTO();
        model.put("appUserDTO", appUserDTO);
        
        return REGISTER;
    }
   
    @PostMapping(value = "/register")
    public String postRegister (@Valid @ModelAttribute AppUserDTO appUserDTO, BindingResult bindingResult, ModelMap model,
                                HttpServletRequest request, HttpServletResponse response) {

        if (bindingResult.hasErrors()) {
            model.put("appUser", appUserDTO);
            return REGISTER;
        }
        
        AppUser appUser = new AppUser(appUserDTO.getEmail(), appUserDTO.getPassword());
        
        try {
            appUser = userService.saveUser(appUser);
        }
        catch (Exception e) {
            model.put(ERROR, "Username already exists");
            model.put("appUser", appUserDTO);
            return REGISTER;
        }
        
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(appUser, null, appUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        new HttpSessionSecurityContextRepository()
        .saveContext(SecurityContextHolder.getContext(), request, response);
        
        return "redirect:/";
    }
}
