package com.ibatulanand.tillservice.loyalty;

import java.util.Optional;

/** More Card member lookups and points ledger. {@link SeededMoreCardDirectory} today; a loyalty-platform adapter later. */
public interface MoreCardDirectory {

    Optional<MoreCardMember> find(String cardNumber);

    /** Credits points to the card and returns the member with the new balance. */
    MoreCardMember award(String cardNumber, int points);
}
