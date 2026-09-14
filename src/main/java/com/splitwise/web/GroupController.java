package com.splitwise.web;

import com.splitwise.domain.Group;
import com.splitwise.security.SplitwiseUserDetails;
import com.splitwise.service.AccessGuard;
import com.splitwise.service.BalanceService;
import com.splitwise.service.ExpenseService;
import com.splitwise.service.GroupService;
import com.splitwise.web.form.AddMemberForm;
import com.splitwise.web.form.CreateGroupForm;
import com.splitwise.support.BusinessRuleException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/groups")
public class GroupController {

    private static final List<String> CURRENCIES = List.of("PHP", "USD", "EUR", "SGD", "JPY");

    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final BalanceService balanceService;
    private final AccessGuard accessGuard;

    public GroupController(GroupService groupService,
                           ExpenseService expenseService,
                           BalanceService balanceService,
                           AccessGuard accessGuard) {
        this.groupService = groupService;
        this.expenseService = expenseService;
        this.balanceService = balanceService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal SplitwiseUserDetails principal, Model model) {
        model.addAttribute("groups", groupService.findGroupsFor(principal.getId()));
        return "groups/list";
    }

    @GetMapping("/new")
    public String newGroup(Model model) {
        model.addAttribute("form", new CreateGroupForm());
        model.addAttribute("currencies", CURRENCIES);
        return "groups/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CreateGroupForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal SplitwiseUserDetails principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // Re-render the form rather than redirect: a redirect would drop
            // both the binding errors and what the user typed.
            model.addAttribute("currencies", CURRENCIES);
            return "groups/form";
        }

        Group group = groupService.create(form, principal.getId());
        redirectAttributes.addFlashAttribute("success", "Created “" + group.getName() + "”.");
        return "redirect:/groups/" + group.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal SplitwiseUserDetails principal,
                         Model model) {
        populateDetail(id, principal.getId(), model);
        if (!model.containsAttribute("memberForm")) {
            model.addAttribute("memberForm", new AddMemberForm());
        }
        return "groups/detail";
    }

    @PostMapping("/{id}/members")
    public String addMember(@PathVariable Long id,
                            @Valid @ModelAttribute("memberForm") AddMemberForm memberForm,
                            BindingResult bindingResult,
                            @AuthenticationPrincipal SplitwiseUserDetails principal,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateDetail(id, principal.getId(), model);
            return "groups/detail";
        }

        try {
            String added = groupService.addMember(id, memberForm.getEmail(), principal.getId());
            redirectAttributes.addFlashAttribute("success", added + " joined the group.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id;
    }

    @PostMapping("/{id}/members/{uid}/remove")
    public String removeMember(@PathVariable Long id,
                               @PathVariable Long uid,
                               @AuthenticationPrincipal SplitwiseUserDetails principal,
                               RedirectAttributes redirectAttributes) {
        try {
            String removed = groupService.removeMember(id, uid, principal.getId());
            redirectAttributes.addFlashAttribute("success", removed + " left the group.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/groups/" + id;
    }

    private void populateDetail(Long groupId, Long userId, Model model) {
        var expenses = expenseService.findFeed(groupId, userId);

        // The same guard that enforces deletion decides whether to draw the
        // button, so the UI can never offer an action the service will refuse.
        Set<Long> deletable = expenses.stream()
                .map(expense -> expense.getId())
                .filter(expenseId -> accessGuard.canDeleteExpense(expenseId, userId))
                .collect(Collectors.toSet());

        model.addAttribute("group", groupService.getGroup(groupId, userId));
        model.addAttribute("members", groupService.findActiveMembers(groupId, userId));
        model.addAttribute("expenses", expenses);
        model.addAttribute("myShares", expenseService.findSharesFor(groupId, userId));
        model.addAttribute("balances", balanceService.balancesFor(groupId, userId));
        model.addAttribute("myBalance", balanceService.summaryFor(groupId, userId));
        model.addAttribute("deletableExpenses", deletable);
        model.addAttribute("currentUserId", userId);
    }
}
