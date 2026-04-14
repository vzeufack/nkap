package com.kmercoders.nkap.appuser;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsServiceImpl implements UserDetailsService {
   
    private AppUserRepository userRepo;
    
    public AppUserDetailsServiceImpl(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }
    
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepo.findByEmail(email);
        
        if (user == null)
            throw new UsernameNotFoundException("User does not exist!");
        
        return new SecurityUser(user);
    }

}
