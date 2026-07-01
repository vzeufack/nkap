package com.kmercoders.nkap.account;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
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
            .sorted(Comparator.comparing(dto -> dto.getName().toLowerCase()))
            .toList();
    }

    @Transactional
    public AccountDTO createAccount(AccountRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        Account account = new Account(request.getAccountType(), request.getName(), request.getBalance(), appUser);
        return AccountDTO.from(accountRepository.save(account));
    }

    @Transactional
    public AccountDTO updateAccount(Long id, AccountRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        Account account = accountRepository.findByIdAndAppUser(id, appUser)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        account.setName(request.getName());
        account.setType(request.getAccountType());
        account.setBalance(request.getBalance());
        return AccountDTO.from(account);
    }
}
