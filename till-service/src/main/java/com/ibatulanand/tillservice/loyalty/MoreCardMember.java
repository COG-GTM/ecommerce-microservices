package com.ibatulanand.tillservice.loyalty;

/** A More Card holder as the till shows them. Only the last four digits of the card leave the till service. */
public record MoreCardMember(String cardLast4, String name, int pointsBalance) {
}
