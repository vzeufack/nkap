package com.kmercoders.nkap.appuser;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class AppUserController {
    private static final String REGISTER = "register";
    private static final String ERROR = "error";
    private AppUserService userService;
    
    public AppUserController(AppUserService userService) {
        this.userService = userService;
    }
    
    @GetMapping(value = "/login")
    public String getLogin(ModelMap model) {
        AppUser user = new AppUser();
        model.put("user", user);
        
        return "login";
    }
   
    @GetMapping(value = "/register")
    public String getRegister (ModelMap model) {
        AppUser user = new AppUser();
        model.put("user", user);
        
        return REGISTER;
    }
   
    @PostMapping(value = "/register")
    public String postRegister (@ModelAttribute AppUser appUser, ModelMap model,
                                HttpServletRequest request, HttpServletResponse response) {
        if(appUser.getEmail().isEmpty() || appUser.getEmail() == null) {
            model.put(ERROR, "Please provide an email");    
                return REGISTER;
        }
            
        if(appUser.getPassword().isEmpty()) {
            model.put(ERROR, "Please provide a password");
                return REGISTER;
        }
        
        try {
            appUser = userService.saveUser(appUser);
        }
        catch (Exception e) {
            model.put(ERROR, "Username already exists");
            return REGISTER;
        }
        
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(appUser, null, appUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        new HttpSessionSecurityContextRepository()
        .saveContext(SecurityContextHolder.getContext(), request, response);
        
        return "redirect:/";
    }
}
