package com.splitwise.web;

import com.splitwise.domain.GroupMember;
import com.splitwise.domain.Settlement;
import com.splitwise.security.SplitwiseUserDetails;
import com.splitwise.service.GroupService;
import com.splitwise.service.SettlementService;
import com.splitwise.support.FieldValidationException;
import com.splitwise.web.form.CreateSettlementForm;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class SettlementController {

    private final SettlementService settlementService;
    private final GroupService groupService;

    public SettlementController(SettlementService settlementService, GroupService groupService) {
        this.settlementService = settlementService;
        this.groupService = groupService;
    }

    /**
     * The settle-up page. The optional from/to/amount parameters are how a
     * suggestion pre-fills the form: clicking one reloads this page with the
     * values filled in, and the user still has to submit. Suggestions never
     * write anything on their own.
     */
    @GetMapping("/groups/{groupId}/settle")
    public String settleForm(@PathVariable Long groupId,
                             @RequestParam(required = false) Long from,
                             @RequestParam(required = false) Long to,
                             @RequestParam(required = false) BigDecimal amount,
                             @AuthenticationPrincipal SplitwiseUserDetails principal,
                             Model model) {
        CreateSettlementForm form = new CreateSettlementForm();
        form.setFromUser(from != null ? from : principal.getId());
        form.setToUser(to);
        form.setAmount(amount);

        model.addAttribute("form", form);
        populate(groupId, principal.getId(), model);
        return "settlements/form";
    }

    @PostMapping("/groups/{groupId}/settlements")
    public String record(@PathVariable Long groupId,
                         @Valid @ModelAttribute("form") CreateSettlementForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal SplitwiseUserDetails principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populate(groupId, principal.getId(), model);
            return "settlements/form";
        }

        Settlement settlement;
        try {
            settlement = settlementService.record(groupId, form, principal.getId());
        } catch (FieldValidationException e) {
            if (e.getField() == null) {
                bindingResult.reject("invalid", e.getMessage());
            } else {
                bindingResult.rejectValue(e.getField(), "invalid", e.getMessage());
            }
            populate(groupId, principal.getId(), model);
            return "settlements/form";
        }

        redirectAttributes.addFlashAttribute("success",
                settlementService.describe(settlement.getId()) + " "
                        + "₱" + settlement.getAmount().toPlainString() + ".");
        return "redirect:/groups/" + groupId;
    }

    private void populate(Long groupId, Long actorId, Model model) {
        List<GroupMember> members = groupService.findActiveMembers(groupId, actorId);
        model.addAttribute("group", groupService.getGroup(groupId, actorId));
        model.addAttribute("members", members);
        model.addAttribute("suggestions", settlementService.suggestTransfers(groupId, actorId));
        model.addAttribute("settlements", settlementService.findByGroup(groupId, actorId));
    }
}
