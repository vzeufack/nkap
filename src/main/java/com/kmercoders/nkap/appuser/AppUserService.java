package com.kmercoders.nkap.appuser;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AppUserService {
   private final AppUserRepository userRepo;
   private final BCryptPasswordEncoder passwordEncoder;
   
    public AppUserService(AppUserRepository userRepo) {
      this.userRepo = userRepo;
      this.passwordEncoder = new BCryptPasswordEncoder();
    }
    
    public AppUser saveUser(AppUser user) {
      Authority authority = new Authority();
      authority.setAuthority("ROLE_USER");
      authority.setAppUser(user);
      
      Set<Authority> authorities = new HashSet<>();
      authorities.add(authority);
      
      final String encryptedPassword = passwordEncoder.encode(user.getPassword());
      user.setPassword(encryptedPassword);    
      user.setAuthorities(authorities);
      user = userRepo.save(user); 
      
        return user;
    }

    public AppUser getAuthenticatedUser() {
      return (AppUser) SecurityContextHolder.getContext()
              .getAuthentication()
              .getPrincipal();
    }
}
