package ru.eltex.producthub.search.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ru.eltex.producthub.search.service.SearchIndexer;
import ru.eltex.producthub.search.service.SearchLogService;
import ru.eltex.producthub.search.service.SearchService;
import ru.eltex.producthub.security.HubUserDetails;
import ru.eltex.producthub.sku.service.SkuPermissions;

/** Поиск: выпадающий список в шапке, страница "все результаты", учет кликов, переиндексация для ADMIN. */
@Controller
public class SearchController {

    private final SearchService search;
    private final SearchIndexer indexer;
    private final SearchLogService searchLog;
    private final SkuPermissions permissions;

    public SearchController(SearchService search, SearchIndexer indexer, SearchLogService searchLog,
                            SkuPermissions permissions) {
        this.search = search;
        this.indexer = indexer;
        this.searchLog = searchLog;
        this.permissions = permissions;
    }

    /** Фрагмент для выпадающего списка. Короче двух символов не ищем и не пишем в журнал. */
    @GetMapping("/search/suggest")
    public String suggest(@RequestParam(defaultValue = "") String q, @AuthenticationPrincipal HubUserDetails user, Model ui) {
        SearchService.Result result = search.acceptable(q)
                ? search.suggest(q.strip(), permissions.isInternal(user))
                : new SearchService.Result(q, List.of(), 0);
        if (search.acceptable(q)) {
            searchLog.record("SUGGEST", user.getUsername(), q.strip(), result.total(), result.millis(), null, null);
        }
        ui.addAttribute("result", result);
        ui.addAttribute("q", q.strip());
        ui.addAttribute("show", search.acceptable(q));
        return "search/suggest :: results";
    }

    /** Страница результатов: без типа показывает все группы, с типом постранично один тип. */
    @GetMapping("/search")
    public String page(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) String type,
                       @RequestParam(defaultValue = "0") int page, @AuthenticationPrincipal HubUserDetails user, Model ui) {
        boolean internal = permissions.isInternal(user);
        String query = q.strip();
        ui.addAttribute("q", query);
        ui.addAttribute("acceptable", search.acceptable(query));
        ui.addAttribute("types", SearchIndexer.TYPES);
        ui.addAttribute("activeMenu", "");
        long started = System.nanoTime();
        if (search.acceptable(query)) {
            SearchService.Result all = search.suggest(query, internal);
            ui.addAttribute("result", all);
            if (type != null && SearchIndexer.TYPES.contains(type)) {
                SearchService.Group group = search.page(query, type, page, internal);
                ui.addAttribute("type", type);
                ui.addAttribute("group", group);
                ui.addAttribute("pageNo", Math.max(page, 0));
                ui.addAttribute("hasNext", (long) (Math.max(page, 0) + 1) * 20 < group.total());
            }
            long millis = (System.nanoTime() - started) / 1_000_000;
            searchLog.record("PAGE", user.getUsername(), query, all.total(), millis, null, null);
        }
        return "search/results";
    }

    /** Клик по результату: пишем в журнал и переходим. Адрес перехода только внутри приложения. */
    @GetMapping("/search/go")
    public String go(@RequestParam String to, @RequestParam(defaultValue = "") String q,
                     @RequestParam(required = false) String type, @RequestParam(required = false) Long id,
                     @AuthenticationPrincipal HubUserDetails user) {
        String target = safe(to);
        searchLog.record("CLICK", user.getUsername(), q, null, 0, type, id);
        return "redirect:" + target;
    }

    static String safe(String to) {
        boolean local = to != null && to.startsWith("/") && !to.startsWith("//") && !to.contains("\\")
                && !to.contains("\r") && !to.contains("\n");
        return local ? to : "/";
    }

    @PostMapping("/directories/reindex")
    public String reindex(RedirectAttributes redirect) {
        redirect.addFlashAttribute("reindexed", indexer.reindexAll());
        return "redirect:/directories";
    }
}
