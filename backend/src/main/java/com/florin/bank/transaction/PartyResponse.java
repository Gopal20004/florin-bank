package com.florin.bank.transaction;

import com.florin.bank.account.Account;
import com.florin.bank.account.AccountNumbers;
import com.florin.bank.common.Names;

/**
 * One side of a transaction as the viewer may see it. Their own accounts appear in full, by nickname;
 * anyone else's appear as a masked name and number ("Asha R.", "•••• 9012"), never by private nickname.
 */
public record PartyResponse(Long accountId, String name, String accountNumber, boolean own) {

    public static PartyResponse of(Account account, Long viewerId) {
        if (account.isOwnedBy(viewerId)) {
            return new PartyResponse(account.getId(), account.getNickname(), account.getAccountNumber(), true);
        }
        return new PartyResponse(null, Names.mask(account.getCustomer().getFullName()),
                AccountNumbers.mask(account.getAccountNumber()), false);
    }
}
