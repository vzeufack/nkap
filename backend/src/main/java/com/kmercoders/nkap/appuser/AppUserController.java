package com.kmercoders.nkap.appuser;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppUserController {

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AppUserController(JwtService jwtService,
                             AuthenticationManager authenticationManager,
                             AppUserRepository appUserRepository,
                             PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> getToken(@RequestBody AccountCredentials credentials) {
        UsernamePasswordAuthenticationToken creds =
                new UsernamePasswordAuthenticationToken(
                        credentials.email(),
                        credentials.password()
                );
        Authentication auth = authenticationManager.authenticate(creds);
        String jwts = jwtService.getToken(auth.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwts)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Authorization")
                .build();
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AccountCredentials credentials) {
        if (credentials == null ||
            credentials.email() == null ||
            credentials.password() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("email and password required");
        }

        if (appUserRepository.findByEmail(credentials.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("email already exists");
        }

        AppUser user = new AppUser();

        user.setFullName(credentials.fullname());
        user.setEmail(credentials.email());
        user.setPassword(passwordEncoder.encode(credentials.password()));

        appUserRepository.save(user);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}