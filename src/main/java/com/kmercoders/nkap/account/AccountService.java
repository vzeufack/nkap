package com.kmercoders.nkap.account;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final AppUserService appUserService;

    public AccountService(AccountRepository accountRepository, AppUserService appUserService) {
        this.accountRepository = accountRepository;
        this.appUserService    = appUserService;
    }

    public List<AccountDTO> getAccountsForCurrentUser() {
        AppUser appUser = appUserService.getAuthenticatedUser();
        return accountRepository.findByAppUser(appUser).stream()
            .map(AccountDTO::from)
            .toList();
    }

    @Transactional
    public AccountDTO createAccount(AccountRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        Account account = new Account(request.getAccountType(), request.getName(), request.getBalance(), appUser);
        return AccountDTO.from(accountRepository.save(account));
    }
}
