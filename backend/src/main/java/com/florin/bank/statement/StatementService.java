package com.florin.bank.statement;

import com.florin.bank.account.Account;
import com.florin.bank.account.AccountRepository;
import com.florin.bank.common.PageResponse;
import com.florin.bank.config.BankProperties;
import com.florin.bank.transaction.LedgerEntry;
import com.florin.bank.transaction.LedgerEntryRepository;
import com.florin.bank.transaction.PartyResponse;
import com.florin.bank.transaction.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StatementService {

    // A statement download covers at most a year, which is also far below this row cap for a personal account.
    static final int MAX_STATEMENT_DAYS = 366;
    static final int MAX_STATEMENT_ROWS = 10_000;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LedgerEntryRepository entries;
    private final AccountRepository accounts;
    private final BankProperties bank;

    public StatementService(LedgerEntryRepository entries, AccountRepository accounts, BankProperties bank) {
        this.entries = entries;
        this.accounts = accounts;
        this.bank = bank;
    }

    @Transactional(readOnly = true)
    public PageResponse<StatementLine> activity(Long customerId, StatementQuery query, int page, int size) {
        if (query.accountId() != null) {
            owned(customerId, query.accountId());
        }
        Page<LedgerEntry> result = search(customerId, query, PageRequest.of(page, size));
        return PageResponse.from(result, lines -> toLines(lines, customerId));
    }

    /** An account statement as CSV, oldest line first, for spreadsheets and accounting tools. */
    @Transactional(readOnly = true)
    public Csv statement(Long customerId, Long accountId, LocalDate from, LocalDate to) {
        Account account = owned(customerId, accountId);
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end date must be on or after the start date");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_STATEMENT_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Download at most one year of statements at a time");
        }
        List<LedgerEntry> rows = new ArrayList<>(search(customerId,
                new StatementQuery(accountId, from, to, null, null), PageRequest.of(0, MAX_STATEMENT_ROWS)).getContent());
        Collections.reverse(rows);

        ZoneId zone = bank.zone();
        StringBuilder csv = new StringBuilder("﻿"); // BOM: Excel then reads the file as UTF-8 (₹, names).
        csv.append("Date,Reference,Description,Type,Counterparty,Debit,Credit,Balance\r\n");
        for (StatementLine line : toLines(rows, customerId)) {
            boolean debit = line.direction() == LedgerEntry.Direction.DEBIT;
            csv.append(CSV_TIME.format(line.createdAt().atZone(zone))).append(',')
                    .append(line.reference()).append(',')
                    .append(Csv.cell(line.description())).append(',')
                    .append(line.type().name()).append(',')
                    .append(Csv.cell(counterpartyLabel(line))).append(',')
                    .append(debit ? line.amount().toPlainString() : "").append(',')
                    .append(debit ? "" : line.amount().toPlainString()).append(',')
                    .append(line.balanceAfter().toPlainString()).append("\r\n");
        }
        String last4 = account.getAccountNumber().substring(8);
        return new Csv("florin-statement-" + last4 + "-" + from + "-to-" + to + ".csv", csv.toString());
    }

    private Page<LedgerEntry> search(Long customerId, StatementQuery query, Pageable pageable) {
        ZoneId zone = bank.zone();
        Instant from = query.from() == null ? null : query.from().atStartOfDay(zone).toInstant();
        Instant to = query.to() == null ? null : query.to().plusDays(1).atStartOfDay(zone).toInstant();
        String text = query.text() == null ? "" : query.text().trim();
        String pattern = text.isEmpty() ? null : "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        String reference = text.isEmpty() ? null : text.toUpperCase(Locale.ROOT);
        return entries.search(customerId, query.accountId(), from, to, query.direction(), pattern, reference, pageable);
    }

    /** Maps a page of entries to lines, naming the other side of each transfer with one extra query. */
    private List<StatementLine> toLines(List<LedgerEntry> page, Long customerId) {
        Set<Long> transferIds = page.stream()
                .filter(entry -> entry.getTransaction().getType() == Transaction.Type.TRANSFER)
                .map(entry -> entry.getTransaction().getId())
                .collect(Collectors.toSet());
        Map<Long, List<LedgerEntry>> legs = transferIds.isEmpty() ? Map.of()
                : entries.findLegs(transferIds).stream().collect(Collectors.groupingBy(entry -> entry.getTransaction().getId()));
        return page.stream().map(entry -> {
            Transaction transaction = entry.getTransaction();
            Account account = entry.getAccount();
            PartyResponse counterparty = legs.getOrDefault(transaction.getId(), List.of()).stream()
                    .filter(leg -> !leg.getId().equals(entry.getId()))
                    .findFirst()
                    .map(leg -> PartyResponse.of(leg.getAccount(), customerId))
                    .orElse(null);
            return new StatementLine(entry.getId(), transaction.getId(), transaction.getReference(), transaction.getType(),
                    entry.getDirection(), entry.getAmount(), entry.getBalanceAfter(), transaction.getDescription(),
                    new StatementLine.AccountRef(account.getId(), account.getNickname(), account.getAccountNumber()),
                    counterparty, entry.getCreatedAt());
        }).toList();
    }

    private Account owned(Long customerId, Long accountId) {
        return accounts.findByIdAndCustomerId(accountId, customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private static String counterpartyLabel(StatementLine line) {
        if (line.counterparty() != null) {
            return line.counterparty().name() + " " + line.counterparty().accountNumber();
        }
        return line.type() == Transaction.Type.DEPOSIT ? "Cash deposit" : "Cash withdrawal";
    }

    // '!' is the LIKE escape character declared in LedgerEntryRepository#search.
    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** A rendered statement file. */
    public record Csv(String filename, String content) {

        /**
         * Quotes a free-text cell, and defuses spreadsheet formulas: a note such as "=HYPERLINK(...)" would
         * otherwise run when the statement is opened in Excel or Sheets.
         */
        static String cell(String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            String safe = "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
    }
}
