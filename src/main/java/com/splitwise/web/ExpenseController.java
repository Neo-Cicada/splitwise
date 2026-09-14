package com.splitwise.web;

import com.splitwise.domain.Group;
import com.splitwise.domain.GroupMember;
import com.splitwise.domain.User;
import com.splitwise.service.ExpenseService;
import com.splitwise.service.GroupService;
import com.splitwise.service.SplitPreviewService;
import com.splitwise.split.InvalidSplitException;
import com.splitwise.split.SplitType;
import com.splitwise.security.SplitwiseUserDetails;
import com.splitwise.support.FieldValidationException;
import com.splitwise.web.form.CreateExpenseForm;
import com.splitwise.web.form.ParticipantInput;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ExpenseController {

    private final ExpenseService expenseService;
    private final GroupService groupService;
    private final SplitPreviewService splitPreviewService;

    public ExpenseController(ExpenseService expenseService,
                             GroupService groupService,
                             SplitPreviewService splitPreviewService) {
        this.expenseService = expenseService;
        this.groupService = groupService;
        this.splitPreviewService = splitPreviewService;
    }

    /**
     * htmx fragment endpoint. Takes the form exactly as it stands — no
     * @Valid, because a half-filled form is the normal case here — and returns
     * the preview plus an out-of-band swap for the submit button.
     */
    @PostMapping("/groups/{groupId}/expenses/preview")
    public String preview(@PathVariable Long groupId,
                          @ModelAttribute("form") CreateExpenseForm form,
                          @AuthenticationPrincipal SplitwiseUserDetails principal,
                          Model model) {
        model.addAttribute("preview",
                splitPreviewService.preview(groupId, form, principal.getId()));
        return "fragments/split-preview :: preview";
    }

    @GetMapping("/groups/{groupId}/expenses/new")
    public String newExpense(@PathVariable Long groupId,
                            @AuthenticationPrincipal SplitwiseUserDetails principal,
                            Model model) {
        List<GroupMember> members = groupService.findActiveMembers(groupId, principal.getId());

        CreateExpenseForm form = new CreateExpenseForm();
        form.setPaidBy(principal.getId());
        // One row per member, in render order, with everyone opted in: the
        // common case is "split this across all of us".
        members.forEach(member -> {
            ParticipantInput participant = new ParticipantInput(member.getUser().getId());
            participant.setSelected(true);
            form.getParticipants().add(participant);
        });

        model.addAttribute("form", form);
        populateFormModel(groupId, principal.getId(), members, model);
        return "expenses/form";
    }

    @PostMapping("/groups/{groupId}/expenses")
    public String create(@PathVariable Long groupId,
                         @Valid @ModelAttribute("form") CreateExpenseForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal SplitwiseUserDetails principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return rerenderForm(groupId, principal.getId(), form, model);
        }

        try {
            expenseService.create(groupId, form, principal.getId());
        } catch (FieldValidationException e) {
            reject(bindingResult, e.getField(), e.getMessage());
            return rerenderForm(groupId, principal.getId(), form, model);
        } catch (InvalidSplitException e) {
            // Bad amounts or percentages: show it against the participant table.
            reject(bindingResult, "participants", e.getMessage());
            return rerenderForm(groupId, principal.getId(), form, model);
        }

        redirectAttributes.addFlashAttribute("success", "Expense added.");
        return "redirect:/groups/" + groupId;
    }

    @PostMapping("/expenses/{expenseId}/delete")
    public String delete(@PathVariable Long expenseId,
                         @AuthenticationPrincipal SplitwiseUserDetails principal,
                         RedirectAttributes redirectAttributes) {
        Long groupId = expenseService.softDelete(expenseId, principal.getId());
        redirectAttributes.addFlashAttribute("success", "Expense removed.");
        return "redirect:/groups/" + groupId;
    }

    private void reject(BindingResult bindingResult, String field, String message) {
        if (field == null) {
            bindingResult.reject("invalid", message);
        } else {
            bindingResult.rejectValue(field, "invalid", message);
        }
    }

    private String rerenderForm(Long groupId, Long actorId, CreateExpenseForm form, Model model) {
        List<GroupMember> members = groupService.findActiveMembers(groupId, actorId);

        // A submission that carried no participant rows (or lost them) would
        // re-render an empty table with nothing to correct. Rebuild the rows so
        // the user can always fix and resubmit.
        if (form.getParticipants().isEmpty()) {
            members.forEach(member ->
                    form.getParticipants().add(new ParticipantInput(member.getUser().getId())));
        }

        populateFormModel(groupId, actorId, members, model);
        return "expenses/form";
    }

    private void populateFormModel(Long groupId, Long actorId, List<GroupMember> members, Model model) {
        Group group = groupService.getGroup(groupId, actorId);

        // The form's participant rows carry only ids; the template looks names
        // up here rather than relying on two lists staying index-aligned.
        Map<Long, String> memberNames = new LinkedHashMap<>();
        members.forEach(member -> memberNames.put(member.getUser().getId(), member.getUser().getName()));

        model.addAttribute("group", group);
        model.addAttribute("members", members);
        model.addAttribute("memberNames", memberNames);
        model.addAttribute("splitTypes", SplitType.values());
    }
}
