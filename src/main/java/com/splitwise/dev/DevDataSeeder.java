package com.splitwise.dev;

import com.splitwise.domain.Group;
import com.splitwise.domain.Settlement;
import com.splitwise.domain.User;
import com.splitwise.repository.BalanceView;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.repository.UserRepository;
import com.splitwise.service.BalanceService;
import com.splitwise.service.ExpenseService;
import com.splitwise.service.GroupService;
import com.splitwise.web.form.CreateExpenseForm;
import com.splitwise.web.form.CreateGroupForm;
import com.splitwise.web.form.ParticipantInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Populates a realistic group on startup under the {@code dev} profile.
 *
 * <p>Seeding goes through the real services rather than raw SQL, so the data
 * is produced by the same validation and split arithmetic the UI uses — if a
 * strategy breaks, the seeder fails loudly instead of writing numbers the
 * application itself would reject.
 *
 * <p>Idempotent: it keys off the seed group's name, so restarting never
 * duplicates anything, and a database already holding other groups is left
 * untouched.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final SettlementRepository settlementRepository;
    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final BalanceService balanceService;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(GroupRepository groupRepository,
                         UserRepository userRepository,
                         SettlementRepository settlementRepository,
                         GroupService groupService,
                         ExpenseService expenseService,
                         BalanceService balanceService,
                         PasswordEncoder passwordEncoder) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.settlementRepository = settlementRepository;
        this.groupService = groupService;
        this.expenseService = expenseService;
        this.balanceService = balanceService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        List<User> people = ensurePeople();
        ensureUsablePasswords(people);
        logCredentials();

        Group existing = findSeedGroup();
        if (existing != null) {
            log.info("[dev-seed] Already seeded; skipping. Group '{}' is id {}.",
                    SeedData.GROUP_NAME, existing.getId());
            logBalances(existing.getId(), people.get(0).getId());
            return;
        }

        Group group = createGroup(people);

        SeedData.EXPENSES.forEach(expense -> addExpense(group, people, expense));
        SeedData.SETTLEMENTS.forEach(settlement -> addSettlement(group, people, settlement));

        log.info("[dev-seed] Seeded group '{}' (id {}) with {} expenses and {} settlements.",
                group.getName(), group.getId(),
                SeedData.EXPENSES.size(), SeedData.SETTLEMENTS.size());
        logBalances(group.getId(), people.get(0).getId());
    }

    /**
     * Flyway seeds users with a placeholder password that is not a BCrypt
     * hash, so nobody could sign in as them. Give them a real encoded
     * password, but only once: re-encoding on every boot would churn rows and
     * invalidate any password changed by hand.
     */
    private void ensureUsablePasswords(List<User> people) {
        people.stream()
                .filter(user -> user.getPassword() == null || !user.getPassword().startsWith("$2"))
                .forEach(user -> {
                    user.setPassword(passwordEncoder.encode(SeedData.DEV_PASSWORD));
                    userRepository.save(user);
                    log.info("[dev-seed] Set a usable password for {}.", user.getEmail());
                });
    }

    private void logCredentials() {
        log.info("[dev-seed] Dev sign-in credentials (password is the same for all):");
        SeedData.PEOPLE.forEach(person ->
                log.info("[dev-seed]   {} / {}  ({})",
                        person.email(), SeedData.DEV_PASSWORD, person.name()));
    }

    private Group findSeedGroup() {
        return groupRepository.findAll().stream()
                .filter(group -> SeedData.GROUP_NAME.equals(group.getName()))
                .findFirst()
                .orElse(null);
    }

    /** Reuses the users Flyway seeds, creating any that are missing. */
    private List<User> ensurePeople() {
        List<User> people = new ArrayList<>();
        for (SeedData.Person person : SeedData.PEOPLE) {
            User user = userRepository.findByEmailIgnoreCase(person.email())
                    .orElseGet(() -> userRepository.save(User.builder()
                            .name(person.name())
                            .email(person.email())
                            .password("not-a-real-password")
                            .build()));
            people.add(user);
        }
        return people;
    }

    private Group createGroup(List<User> people) {
        CreateGroupForm form = new CreateGroupForm();
        form.setName(SeedData.GROUP_NAME);
        form.setCurrency(SeedData.CURRENCY);

        // The first person creates the group and so joins as ADMIN; the rest
        // are added as members.
        Group group = groupService.create(form, people.get(0).getId());
        people.stream().skip(1).forEach(person ->
                groupService.addMember(group.getId(), person.getEmail(), people.get(0).getId()));
        return group;
    }

    private void addExpense(Group group, List<User> people, SeedData.SeedExpense seed) {
        CreateExpenseForm form = new CreateExpenseForm();
        form.setPaidBy(people.get(seed.payer()).getId());
        form.setAmount(new BigDecimal(seed.amount()));
        form.setDescription(seed.description());
        form.setSpentAt(LocalDate.now().minusDays(seed.daysAgo()));
        form.setSplitType(seed.splitType());

        for (int i = 0; i < seed.participants().size(); i++) {
            ParticipantInput participant = new ParticipantInput(
                    people.get(seed.participants().get(i)).getId());
            participant.setSelected(true);
            if (seed.values() != null) {
                participant.setValue(new BigDecimal(seed.values().get(i)));
            }
            form.getParticipants().add(participant);
        }

        expenseService.create(group.getId(), form, people.get(0).getId());
    }

    private void addSettlement(Group group, List<User> people, SeedData.SeedSettlement seed) {
        settlementRepository.save(Settlement.builder()
                .group(group)
                .fromUser(people.get(seed.from()))
                .toUser(people.get(seed.to()))
                .amount(new BigDecimal(seed.amount()))
                .settledAt(OffsetDateTime.now().minusDays(seed.daysAgo()))
                .build());
    }

    private void logBalances(Long groupId, Long actorId) {
        log.info("[dev-seed] Open http://localhost:8080/groups/{}", groupId);

        List<BalanceView> balances = balanceService.balancesFor(groupId, actorId);
        BigDecimal sum = BigDecimal.ZERO;
        for (BalanceView balance : balances) {
            BigDecimal net = balance.getNetBalance();
            sum = sum.add(net);
            log.info("[dev-seed]   {} {} {}",
                    String.format("%-14s", balance.getName()),
                    net.signum() >= 0 ? "is owed" : "owes   ",
                    String.format("%10s", net.abs().toPlainString()));
        }
        log.info("[dev-seed]   sum of net balances = {} (must be 0.00)", sum.toPlainString());
    }
}
