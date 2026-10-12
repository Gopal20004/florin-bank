package com.florin.bank.demo;

import com.florin.bank.account.Account;
import com.florin.bank.account.AccountService;
import com.florin.bank.config.BankProperties;
import com.florin.bank.customer.Customer;
import com.florin.bank.customer.CustomerRepository;
import com.florin.bank.payee.Payee;
import com.florin.bank.payee.PayeeRepository;
import com.florin.bank.transaction.Ledger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Seeds a demo customer with four months of believable history, so the app can be tried without
 * typing in data. Every movement goes through {@link Ledger} with a back-dated instant, so balances,
 * running balances and limits obey exactly the same rules as live traffic. Runs once: if the demo
 * customer exists, nothing happens.
 */
@Component
@ConditionalOnProperty(name = "florin.demo.seed", havingValue = "true")
public class DemoData implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoData.class);
    private static final int HISTORY_DAYS = 125;

    private final CustomerRepository customers;
    private final AccountService accounts;
    private final PayeeRepository payees;
    private final Ledger ledger;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final BankProperties bank;
    private final Clock clock;

    public DemoData(CustomerRepository customers, AccountService accounts, PayeeRepository payees, Ledger ledger,
                    PasswordEncoder passwordEncoder, TransactionTemplate transactionTemplate, BankProperties bank,
                    Clock clock) {
        this.customers = customers;
        this.accounts = accounts;
        this.payees = payees;
        this.ledger = ledger;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.bank = bank;
        this.clock = clock;
    }

    private record Person(Customer customer, Account everyday) {
    }

    private record Cast(Person aarav, Account rainyDay, Account studio, Person diya, Person kabir, Person meera,
                        Person rohan) {
    }

    private record Event(Instant at, Runnable action) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (customers.existsByEmail(bank.demo().email())) {
            LOG.info("Demo data already present for {}", bank.demo().email());
            return;
        }
        ZoneId zone = bank.zone();
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate start = today.minusDays(HISTORY_DAYS);

        Cast cast = transactionTemplate.execute(status -> createCast(start, zone));
        List<Event> events = timeline(cast, start, today, zone);
        events.sort(Comparator.comparing(Event::at));
        // The first month starts on the 1st; nothing may happen before the opening deposits on day one.
        Instant firstMoment = at(start, 10, 0, zone);
        int posted = 0;
        int skipped = 0;
        for (Event event : events) {
            if (event.at().isBefore(firstMoment) || !event.at().isBefore(now)) {
                continue;
            }
            try {
                event.action().run();
                posted++;
            } catch (RuntimeException rejected) {
                // The ledger refused it (funds, limits): skip it, as a real bank would. Demo data must never block startup.
                skipped++;
                LOG.debug("Skipped demo transaction at {}: {}", event.at(), rejected.getMessage());
            }
        }
        LOG.info("Seeded demo customer {} (password {}) with {} transactions ({} skipped)", bank.demo().email(),
                bank.demo().password(), posted, skipped);
    }

    private Cast createCast(LocalDate start, ZoneId zone) {
        Instant opened = start.minusDays(40).atTime(11, 0).atZone(zone).toInstant();
        Person aarav = person("Aarav Mehta", bank.demo().email(), bank.demo().password(), opened);
        Account rainyDay = accounts.openAccount(aarav.customer(), Account.Type.SAVINGS, "Rainy Day Fund",
                start.atTime(9, 0).atZone(zone).toInstant());
        Account studio = accounts.openAccount(aarav.customer(), Account.Type.CURRENT, "Studio",
                start.atTime(9, 5).atZone(zone).toInstant());
        Person diya = person("Diya Kapoor", "diya@example.com", "florin-demo", opened);
        Person kabir = person("Kabir Rao", "kabir@example.com", "florin-demo", opened);
        Person meera = person("Meera Mehta", "meera@example.com", "florin-demo", opened);
        Person rohan = person("Rohan Das", "rohan@example.com", "florin-demo", opened);

        payee(aarav, kabir, "Kabir · Rent", opened);
        payee(aarav, diya, "Diya", opened);
        payee(aarav, meera, "Meera (sis)", opened);
        payee(aarav, rohan, "Rohan", opened);
        return new Cast(aarav, rainyDay, studio, diya, kabir, meera, rohan);
    }

    private Person person(String name, String email, String password, Instant joined) {
        Customer customer = customers.save(new Customer(name, email, passwordEncoder.encode(password), joined));
        Account everyday = accounts.openAccount(customer, Account.Type.SAVINGS, "Everyday Savings", joined);
        return new Person(customer, everyday);
    }

    private void payee(Person owner, Person target, String nickname, Instant at) {
        payees.save(new Payee(owner.customer(), target.everyday(), nickname, at));
    }

    private List<Event> timeline(Cast cast, LocalDate start, LocalDate today, ZoneId zone) {
        // A fixed seed: every demo deployment shows the same believable history.
        Random random = new Random(20_261_012L);
        List<Event> events = new ArrayList<>();
        Person aarav = cast.aarav();
        Account everyday = aarav.everyday();

        events.add(deposit(at(start, 10, 5, zone), aarav, everyday, 120_000, "Opening deposit"));
        events.add(deposit(at(start, 10, 20, zone), cast.rohan(), cast.rohan().everyday(), 150_000, "Opening deposit"));
        events.add(deposit(at(start, 10, 25, zone), cast.diya(), cast.diya().everyday(), 90_000, "Opening deposit"));

        String[] clients = {"Pixelfern", "Monsoon Labs", "Brightwater Co.", "Saffron & Slate", "Juniper Ink"};
        String[] everydayNotes = {"Groceries", "Weekend market", "Fuel", "Pharmacy", "Books", "Hardware store", "Coffee beans"};
        String[] bigNotes = {"Flights to Kochi", "New headphones", "Weekend in Coorg", "Bookshelf", "Running shoes"};
        String[] diyaNotes = {"Dinner, my share", "Concert tickets", "Cab split", "Birthday gift pool", "Brunch"};
        String[] rohanNotes = {"Goa trip, settling up", "Badminton court split", "Movie night", "Groceries split"};

        int invoice = 14;
        for (LocalDate month = start.withDayOfMonth(1); !month.isAfter(today); month = month.plusMonths(1)) {
            events.add(deposit(at(month.withDayOfMonth(1), 9, 30, zone), aarav, everyday, 112_000,
                    "Salary · Quillon Systems"));
            events.add(transfer(at(month.withDayOfMonth(2), 19, 10, zone), aarav, everyday, cast.kabir().everyday(),
                    32_000, "Rent · Flat 4B"));
            events.add(transfer(at(month.withDayOfMonth(3), 8, 0, zone), aarav, everyday, cast.rainyDay(),
                    15_000, "Monthly saving"));
            events.add(transfer(at(month.withDayOfMonth(5), 20, 40, zone), aarav, everyday, cast.meera().everyday(),
                    5_000, "Tuition top-up"));
            events.add(withdraw(at(month.withDayOfMonth(7), 18, 15, zone), aarav, everyday,
                    1_800 + random.nextInt(14) * 100, "Electricity bill"));
            events.add(withdraw(at(month.withDayOfMonth(9), 12, 0, zone), aarav, everyday, 999, "Broadband bill"));
            events.add(withdraw(at(month.withDayOfMonth(10), 10, 0, zone), aarav, everyday, 8_200, "Laptop EMI"));
            events.add(withdraw(at(month.withDayOfMonth(14), 9, 45, zone), aarav, everyday, 599, "Phone plan"));

            for (int day : new int[]{11, 25}) {
                events.add(deposit(at(month.withDayOfMonth(day), 15, 20, zone), aarav, cast.studio(),
                        28_000 + random.nextInt(38) * 1_000,
                        "Invoice #0" + invoice++ + " · " + clients[random.nextInt(clients.length)]));
            }
            events.add(transfer(at(month.withDayOfMonth(16), 11, 0, zone), aarav, cast.studio(), everyday,
                    45_000, "Owner's draw"));

            for (int day = 2; day <= 28; day += 3 + random.nextInt(2)) {
                events.add(withdraw(at(month.withDayOfMonth(day), 17 + random.nextInt(4), random.nextInt(60), zone),
                        aarav, everyday, 1_200 + random.nextInt(47) * 100, everydayNotes[random.nextInt(everydayNotes.length)]));
            }
            events.add(withdraw(at(month.withDayOfMonth(18 + random.nextInt(8)), 21, 5, zone), aarav, everyday,
                    9_000 + random.nextInt(14) * 1_000, bigNotes[random.nextInt(bigNotes.length)]));

            for (int i = 0; i < 2 + random.nextInt(2); i++) {
                events.add(transfer(at(month.withDayOfMonth(4 + random.nextInt(24)), 22, random.nextInt(50), zone),
                        aarav, everyday, cast.diya().everyday(), 600 + random.nextInt(33) * 100,
                        diyaNotes[random.nextInt(diyaNotes.length)]));
            }
            events.add(transfer(at(month.withDayOfMonth(6 + random.nextInt(20)), 13, 30, zone),
                    cast.rohan(), cast.rohan().everyday(), everyday, 1_500 + random.nextInt(51) * 100,
                    rohanNotes[random.nextInt(rohanNotes.length)]));
            if (random.nextBoolean()) {
                events.add(transfer(at(month.withDayOfMonth(12 + random.nextInt(14)), 21, 0, zone),
                        cast.diya(), cast.diya().everyday(), everyday, 450 + random.nextInt(20) * 50, "Movie tickets"));
            }
        }
        return events;
    }

    private static Instant at(LocalDate day, int hour, int minute, ZoneId zone) {
        return day.atTime(hour, minute).atZone(zone).toInstant();
    }

    private Event deposit(Instant at, Person by, Account account, long rupees, String note) {
        return new Event(at, () -> ledger.deposit(by.customer().getId(), account.getId(), BigDecimal.valueOf(rupees, 0)
                .setScale(2), note, null, null, at));
    }

    private Event withdraw(Instant at, Person by, Account account, long rupees, String note) {
        return new Event(at, () -> ledger.withdraw(by.customer().getId(), account.getId(), BigDecimal.valueOf(rupees, 0)
                .setScale(2), note, null, null, at));
    }

    private Event transfer(Instant at, Person by, Account from, Account to, long rupees, String note) {
        return new Event(at, () -> ledger.transfer(by.customer().getId(), from.getId(), to.getAccountNumber(),
                BigDecimal.valueOf(rupees, 0).setScale(2), note, null, null, at));
    }
}
