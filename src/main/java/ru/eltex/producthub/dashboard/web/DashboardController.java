package ru.eltex.producthub.dashboard.web;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ru.eltex.producthub.dashboard.service.DashboardService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.service.ReferenceDataService;

/** Дашборд: PRODUCT по своим SKU, HEAD и ADMIN по всем или по выбранному продакту. */
@Controller
public class DashboardController {

    private final DashboardService service;
    private final ReferenceDataService reference;

    public DashboardController(DashboardService service, ReferenceDataService reference) {
        this.service = service;
        this.reference = reference;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) Long owner, @AuthenticationPrincipal HubUserDetails user, Model ui) {
        if (!service.canView(user)) {
            throw new AccessDeniedException("Дашборд доступен продактам и руководителям");
        }
        Long scope = service.scopeFor(user, owner);
        ui.addAttribute("d", service.build(scope));
        ui.addAttribute("owners", reference.owners());
        ui.addAttribute("canPick", user.getRoles().stream().anyMatch(r -> r.name().equals("HEAD") || r.name().equals("ADMIN")));
        ui.addAttribute("activeMenu", "dashboard");
        return "dashboard/index";
    }
}
