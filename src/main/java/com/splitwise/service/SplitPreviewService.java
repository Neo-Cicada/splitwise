package com.splitwise.service;

import com.splitwise.domain.GroupMember;
import com.splitwise.split.InvalidSplitException;
import com.splitwise.split.ShareDto;
import com.splitwise.split.SplitInput;
import com.splitwise.split.SplitStrategyResolver;
import com.splitwise.split.SplitType;
import com.splitwise.web.form.CreateExpenseForm;
import com.splitwise.web.form.ParticipantInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the live preview shown on the expense form.
 *
 * <p>Crucially this runs the same {@link SplitStrategyResolver} beans the real
 * submission uses. Whenever the inputs add up, the amounts displayed here are
 * produced by the identical code path that will store them — rounding
 * remainder included — so the preview cannot drift from what gets saved.
 */
@Service
@Transactional(readOnly = true)
public class SplitPreviewService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final SplitStrategyResolver splitStrategyResolver;
    private final GroupService groupService;

    public SplitPreviewService(SplitStrategyResolver splitStrategyResolver, GroupService groupService) {
        this.splitStrategyResolver = splitStrategyResolver;
        this.groupService = groupService;
    }

    public SplitPreview preview(Long groupId, CreateExpenseForm form, Long actorId) {
        SplitType type = form.getSplitType() == null ? SplitType.EQUAL : form.getSplitType();

        Map<Long, String> names = new LinkedHashMap<>();
        for (GroupMember member : groupService.findActiveMembers(groupId, actorId)) {
            names.put(member.getUser().getId(), member.getUser().getName());
        }

        List<ParticipantInput> selected = form.getParticipants().stream()
                .filter(ParticipantInput::isSelected)
                .filter(participant -> participant.getUserId() != null)
                .filter(participant -> names.containsKey(participant.getUserId()))
                .toList();

        if (selected.isEmpty()) {
            return SplitPreview.problem(type, "Select at least one participant.");
        }

        BigDecimal total = form.getAmount();
        if (total == null || total.signum() <= 0) {
            return SplitPreview.problem(type, "Enter an amount to see how it splits.");
        }

        return switch (type) {
            case EQUAL -> equalPreview(total, selected, names);
            case EXACT -> exactPreview(total, selected, names);
            case PERCENTAGE -> percentagePreview(total, selected, names);
        };
    }

    private SplitPreview equalPreview(BigDecimal total,
                                      List<ParticipantInput> selected,
                                      Map<Long, String> names) {
        return runStrategy(SplitType.EQUAL, total, selected, names,
                BigDecimal.ZERO, BigDecimal.ZERO,
                "Split evenly across " + selected.size() + " "
                        + (selected.size() == 1 ? "person" : "people") + ".");
    }

    private SplitPreview exactPreview(BigDecimal total,
                                      List<ParticipantInput> selected,
                                      Map<Long, String> names) {
        BigDecimal assigned = selected.stream()
                .map(participant -> orZero(participant.getValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = total.subtract(assigned);

        if (remaining.signum() != 0) {
            String status = remaining.signum() > 0
                    ? "₱" + remaining.abs().toPlainString() + " left to assign"
                    : "₱" + remaining.abs().toPlainString() + " over the total";
            return new SplitPreview(SplitType.EXACT, rowsFromValues(selected, names),
                    total, assigned, remaining, false, status, null);
        }

        return runStrategy(SplitType.EXACT, total, selected, names,
                assigned, remaining, "Fully assigned.");
    }

    private SplitPreview percentagePreview(BigDecimal total,
                                           List<ParticipantInput> selected,
                                           Map<Long, String> names) {
        BigDecimal assigned = selected.stream()
                .map(participant -> orZero(participant.getValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = ONE_HUNDRED.subtract(assigned);

        if (remaining.signum() != 0) {
            String status = plain(assigned) + "% assigned — " + plain(remaining.abs()) + "% "
                    + (remaining.signum() > 0 ? "remaining" : "over");
            return new SplitPreview(SplitType.PERCENTAGE, rowsFromValues(selected, names),
                    total, assigned, remaining, false, status, null);
        }

        return runStrategy(SplitType.PERCENTAGE, total, selected, names,
                assigned, remaining, "100% assigned.");
    }

    /** The one place amounts come from, for every split type. */
    private SplitPreview runStrategy(SplitType type,
                                     BigDecimal total,
                                     List<ParticipantInput> selected,
                                     Map<Long, String> names,
                                     BigDecimal assigned,
                                     BigDecimal remaining,
                                     String status) {
        List<SplitInput> inputs = selected.stream()
                .map(participant -> new SplitInput(participant.getUserId(), participant.getValue()))
                .toList();

        try {
            List<ShareDto> shares = splitStrategyResolver.split(type, total, inputs);

            List<SplitPreview.PreviewRow> rows = new ArrayList<>(shares.size());
            for (int i = 0; i < shares.size(); i++) {
                ShareDto share = shares.get(i);
                rows.add(new SplitPreview.PreviewRow(
                        share.userId(),
                        names.get(share.userId()),
                        share.amount(),
                        selected.get(i).getValue()));
            }
            return new SplitPreview(type, rows, total, assigned, remaining, true, status, null);

        } catch (InvalidSplitException e) {
            return new SplitPreview(type, rowsFromValues(selected, names),
                    total, assigned, remaining, false, null, e.getMessage());
        }
    }

    private List<SplitPreview.PreviewRow> rowsFromValues(List<ParticipantInput> selected,
                                                         Map<Long, String> names) {
        return selected.stream()
                .map(participant -> new SplitPreview.PreviewRow(
                        participant.getUserId(),
                        names.get(participant.getUserId()),
                        null,
                        participant.getValue()))
                .toList();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
