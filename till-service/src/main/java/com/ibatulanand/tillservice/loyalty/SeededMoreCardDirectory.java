package com.ibatulanand.tillservice.loyalty;

import com.ibatulanand.tillservice.web.TillException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory directory of fictional test More Cards (9826 prefix) for the demo stack and E2E tests. */
@Component
public class SeededMoreCardDirectory implements MoreCardDirectory {

    private record Account(String name, int pointsBalance) {
    }

    private final Map<String, Account> accounts = new ConcurrentHashMap<>(Map.of(
            "9826000000000001", new Account("Test Member One", 1200),
            "9826000000000002", new Account("Test Member Two", 0)));

    @Override
    public Optional<MoreCardMember> find(String cardNumber) {
        return Optional.ofNullable(cardNumber).map(accounts::get).map(a -> view(cardNumber, a));
    }

    @Override
    public MoreCardMember award(String cardNumber, int points) {
        Account account = accounts.computeIfPresent(cardNumber,
                (k, a) -> new Account(a.name(), Math.addExact(a.pointsBalance(), points)));
        if (account == null) {
            throw new TillException(HttpStatus.NOT_FOUND, "UNKNOWN_CARD", "More Card not recognised");
        }
        return view(cardNumber, account);
    }

    private static MoreCardMember view(String cardNumber, Account account) {
        return new MoreCardMember(cardNumber.substring(cardNumber.length() - 4), account.name(), account.pointsBalance());
    }
}
