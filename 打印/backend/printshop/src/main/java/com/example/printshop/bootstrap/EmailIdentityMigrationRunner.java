package com.example.printshop.bootstrap;

import com.example.printshop.entity.Account;
import com.example.printshop.mapper.AccountMapper;
import com.example.printshop.security.FieldCryptoService;
import com.example.printshop.security.QqEmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class EmailIdentityMigrationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(EmailIdentityMigrationRunner.class);
    private final AccountMapper accountMapper;
    private final FieldCryptoService cryptoService;

    public EmailIdentityMigrationRunner(AccountMapper accountMapper, FieldCryptoService cryptoService) {
        this.accountMapper = accountMapper;
        this.cryptoService = cryptoService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Account account : accountMapper.selectMissingEmailHashes()) {
            String email = cryptoService.decryptNullable(account.getEmail());
            if (!QqEmailAddress.isQqEmail(email)) {
                log.warn("account id={} type={} has no valid QQ email and cannot use email login until bound", account.getId(), account.getAccountType());
                continue;
            }
            String normalized = QqEmailAddress.normalize(email);
            accountMapper.updateEmailIdentity(account.getId(), cryptoService.encryptNullable(normalized), cryptoService.blindIndex(normalized));
            log.info("QQ email login index initialized for account id={} type={}", account.getId(), account.getAccountType());
        }
    }
}
