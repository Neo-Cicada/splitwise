package com.splitwise.balance;

import com.splitwise.domain.Group;
import com.splitwise.domain.Settlement;
import com.splitwise.domain.User;
import com.splitwise.repository.BalanceView;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.repository.UserRepository;
import com.splitwise.service.BalanceService;
import com.splitwise.service.BalanceSummary;
import com.splitwise.service.ExpenseService;
import com.splitwise.service.GroupService;
import com.splitwise.service.SettlementService;
import com.splitwise.settle.SuggestedTransfer;
import com.splitwise.support.FieldValidationException;
import com.splitwise.web.form.CreateSettlementForm;
import com.splitwise.split.SplitType;
import com.splitwise.web.form.CreateExpenseForm;
import com.splitwise.web.form.CreateGroupForm;
import com.splitwise.web.form.ParticipantInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Balances run against real Postgres 16, not H2: the query leans on CTEs and
 * NUMERIC(12,2) arithmetic, and H2 behaves differently on both. Passing on H2
 * would tell us nothing about production.
 *
 * <p>Each test builds its own group, so tests stay independent without
 * needing to wipe tables between runs.
 */
@SpringBootTest
@Testcontainers
class BalanceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired GroupService groupService;
    @Autowired ExpenseService expenseService;
    @Autowired BalanceService balanceService;
    @Autowired SettlementRepository settlementRepository;
    @Autowired SettlementService settlementService;
    @Autowired UserRepository userRepository;

    private static final String DEV = "dev@splitwise.local";
    private static final String ANA = "ana@splitwise.local";
    private static final String MIGUEL = "miguel@splitwise.local";
    private static final String JOY = "joy@splitwise.local";

    @Test
    @DisplayName("net balances sum to exactly zero after mixed expenses and settlements")
    void balancesSumToZero() {
        Group group = newGroup("Zero sum");
        long dev = userId(DEV);
        long ana = addMember(group, ANA);
        long miguel = addMember(group, MIGUEL);

        // Dev pays 100.00 split three ways: 33.34 / 33.33 / 33.33
        addExpense(group, dev, "100.00", SplitType.EQUAL, List.of(dev, ana, miguel));
        // Ana pays 60.00 split two ways: 30.00 each
        addExpense(group, ana, "60.00", SplitType.EQUAL, List.of(dev, ana));
        // Miguel pays 45.55, split unevenly by exact amounts
        addExpenseExact(group, miguel, "45.55", Map.of(dev, "15.55", ana, "20.00", miguel, "10.00"));
        // And two settlements move money around
        settle(group, ana, dev, "25.00");
        settle(group, miguel, dev, "10.01");

        List<BalanceView> balances = balanceService.balancesFor(group.getId(), userId(DEV));

        BigDecimal sum = balances.stream()
                .map(BalanceView::getNetBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo("0.00");
        assertThat(balances).hasSize(3);
    }

    @Test
    @DisplayName("a payer who also participates nets out to paid minus their own share")
    void payerWhoAlsoParticipatesNetsCorrectly() {
        Group group = newGroup("Payer participates");
        long dev = userId(DEV);
        long ana = addMember(group, ANA);

        // Dev pays 100.00 and is one of two participants, so owes 50.00 of it.
        addExpense(group, dev, "100.00", SplitType.EQUAL, List.of(dev, ana));

        Map<Long, BigDecimal> byUser = balancesByUser(group.getId());

        assertThat(byUser.get(dev)).isEqualByComparingTo("50.00");   // paid 100, owed 50
        assertThat(byUser.get(ana)).isEqualByComparingTo("-50.00");  // paid 0,   owed 50
    }

    @Test
    @DisplayName("a soft-deleted expense stops affecting balances")
    void softDeletedExpenseIsExcluded() {
        Group group = newGroup("Soft delete");
        long dev = userId(DEV);
        long ana = addMember(group, ANA);

        addExpense(group, dev, "100.00", SplitType.EQUAL, List.of(dev, ana));
        Long doomed = addExpense(group, ana, "40.00", SplitType.EQUAL, List.of(dev, ana));

        Map<Long, BigDecimal> before = balancesByUser(group.getId());
        assertThat(before.get(dev)).isEqualByComparingTo("30.00");   // +50 from first, -20 from second
        assertThat(before.get(ana)).isEqualByComparingTo("-30.00");

        expenseService.softDelete(doomed, userId(DEV));

        Map<Long, BigDecimal> after = balancesByUser(group.getId());
        assertThat(after.get(dev)).isEqualByComparingTo("50.00");    // only the first expense counts
        assertThat(after.get(ana)).isEqualByComparingTo("-50.00");
        assertThat(after.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a member with no activity shows 0.00, not null")
    void memberWithNoActivityIsZeroNotNull() {
        Group group = newGroup("Quiet member");
        long dev = userId(DEV);
        long ana = addMember(group, ANA);
        long joy = addMember(group, JOY);

        // Joy takes no part in anything.
        addExpense(group, dev, "50.00", SplitType.EQUAL, List.of(dev, ana));

        Map<Long, BigDecimal> byUser = balancesByUser(group.getId());

        assertThat(byUser).containsKey(joy);
        assertThat(byUser.get(joy)).isNotNull();
        assertThat(byUser.get(joy)).isEqualByComparingTo("0.00");
        assertThat(byUser.get(joy).scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("the per-user summary reports owed, owing and settled")
    void summaryReportsPosition() {
        Group group = newGroup("Summary");
        long dev = userId(DEV);
        long ana = addMember(group, ANA);
        long joy = addMember(group, JOY);

        addExpense(group, dev, "100.00", SplitType.EQUAL, List.of(dev, ana));

        BalanceSummary devSummary = balanceService.summaryFor(group.getId(), dev);
        BalanceSummary anaSummary = balanceService.summaryFor(group.getId(), ana);
        BalanceSummary joySummary = balanceService.summaryFor(group.getId(), joy);

        assertThat(devSummary.isOwed()).isTrue();
        assertThat(devSummary.getAbsolute()).isEqualByComparingTo("50.00");

        assertThat(anaSummary.isOwing()).isTrue();
        assertThat(anaSummary.getAbsolute()).isEqualByComparingTo("50.00");

        assertThat(joySummary.isSettled()).isTrue();
    }

    @Test
    @DisplayName("settlements alone move balances and still sum to zero")
    void settlementsMoveBalances() {
        Group group = newGroup("Settle only");
        long dev = userId(DEV);
        long ana = addMember(group, ANA);

        settle(group, ana, dev, "75.00");

        Map<Long, BigDecimal> byUser = balancesByUser(group.getId());

        // Ana sent money, so she has paid down what she owed: her balance rises.
        assertThat(byUser.get(ana)).isEqualByComparingTo("75.00");
        assertThat(byUser.get(dev)).isEqualByComparingTo("-75.00");
        assertThat(byUser.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("recording every suggested transfer settles the whole group")
    void applyingSuggestionsSettlesTheGroup() {
        Group group = newGroup("Simplify");
        long dev = userId(DEV);
        long ana = addMember(group, ANA);
        long miguel = addMember(group, MIGUEL);
        long joy = addMember(group, JOY);

        addExpense(group, dev, "1200.00", SplitType.EQUAL, List.of(dev, ana, miguel, joy));
        addExpense(group, ana, "640.00", SplitType.EQUAL, List.of(ana, miguel));
        addExpenseExact(group, joy, "355.00",
                Map.of(dev, "100.00", ana, "125.00", joy, "130.00"));

        List<SuggestedTransfer> transfers =
                settlementService.suggestTransfers(group.getId(), dev);

        assertThat(transfers).isNotEmpty();
        // n members need at most n-1 payments.
        assertThat(transfers).hasSizeLessThanOrEqualTo(3);

        // Record each suggestion as a real settlement, exactly as the UI would.
        for (SuggestedTransfer transfer : transfers) {
            CreateSettlementForm form = new CreateSettlementForm();
            form.setFromUser(transfer.fromUserId());
            form.setToUser(transfer.toUserId());
            form.setAmount(transfer.amount());
            settlementService.record(group.getId(), form, dev);
        }

        Map<Long, BigDecimal> after = balancesByUser(group.getId());
        assertThat(after).hasSize(4);
        after.forEach((user, net) -> assertThat(net)
                .as("user %d should be settled", user)
                .isEqualByComparingTo("0.00"));

        // And nothing further is suggested.
        assertThat(settlementService.suggestTransfers(group.getId(), dev)).isEmpty();
    }

    @Test
    @DisplayName("a settlement is rejected when the two sides are the same person")
    void rejectsSelfSettlement() {
        Group group = newGroup("Self settle");
        long dev = userId(DEV);
        addMember(group, ANA);

        CreateSettlementForm form = new CreateSettlementForm();
        form.setFromUser(dev);
        form.setToUser(dev);
        form.setAmount(new BigDecimal("10.00"));

        assertThatThrownBy(() -> settlementService.record(group.getId(), form, dev))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("cannot settle up with themselves");
    }

    @Test
    @DisplayName("a settlement is rejected when a side is not a group member")
    void rejectsNonMemberSettlement() {
        Group group = newGroup("Outsider settle");
        long dev = userId(DEV);
        long outsider = userId(JOY);   // never added to this group

        CreateSettlementForm form = new CreateSettlementForm();
        form.setFromUser(dev);
        form.setToUser(outsider);
        form.setAmount(new BigDecimal("10.00"));

        assertThatThrownBy(() -> settlementService.record(group.getId(), form, dev))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("active member");
    }

    // --- helpers -------------------------------------------------------

    private Group newGroup(String name) {
        CreateGroupForm form = new CreateGroupForm();
        form.setName(name + " " + System.nanoTime());
        form.setCurrency("PHP");
        return groupService.create(form, userId(DEV));
    }

    private long userId(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private long addMember(Group group, String email) {
        groupService.addMember(group.getId(), email, userId(DEV));
        return userId(email);
    }

    private Long addExpense(Group group, long payer, String amount,
                            SplitType type, List<Long> participants) {
        CreateExpenseForm form = baseForm(payer, amount, type);
        participants.forEach(id -> {
            ParticipantInput p = new ParticipantInput(id);
            p.setSelected(true);
            form.getParticipants().add(p);
        });
        return expenseService.create(group.getId(), form, userId(DEV)).getId();
    }

    private Long addExpenseExact(Group group, long payer, String amount, Map<Long, String> shares) {
        CreateExpenseForm form = baseForm(payer, amount, SplitType.EXACT);
        shares.forEach((id, share) -> {
            ParticipantInput p = new ParticipantInput(id);
            p.setSelected(true);
            p.setValue(new BigDecimal(share));
            form.getParticipants().add(p);
        });
        return expenseService.create(group.getId(), form, userId(DEV)).getId();
    }

    private CreateExpenseForm baseForm(long payer, String amount, SplitType type) {
        CreateExpenseForm form = new CreateExpenseForm();
        form.setPaidBy(payer);
        form.setAmount(new BigDecimal(amount));
        form.setSpentAt(LocalDate.now());
        form.setSplitType(type);
        form.setDescription("test expense");
        return form;
    }

    private void settle(Group group, long from, long to, String amount) {
        User fromUser = userRepository.findById(from).orElseThrow();
        User toUser = userRepository.findById(to).orElseThrow();
        settlementRepository.save(Settlement.builder()
                .group(group)
                .fromUser(fromUser)
                .toUser(toUser)
                .amount(new BigDecimal(amount))
                .build());
    }

    private Map<Long, BigDecimal> balancesByUser(Long groupId) {
        return balanceService.balancesFor(groupId, userId(DEV)).stream()
                .collect(Collectors.toMap(BalanceView::getUserId,
                        BalanceView::getNetBalance,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new));
    }
}
