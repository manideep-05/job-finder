package com.manideep.jobfinder;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebController {
    private final JobScanService scan;
    public WebController(JobScanService scan){this.scan=scan;}

    @GetMapping("/")
    public String dashboard(@RequestParam(value = "tab", required = false, defaultValue = "PENDING") String tab, Model model){
        List<JobPosting> jobs = scan.eligibleJobs();
        Map<String, Long> counts = scan.getCategoryCounts();

        String activeTab = "PENDING";
        if (tab != null) {
            String norm = tab.trim().toUpperCase().replace("-", "_").replace(" ", "_");
            if (norm.equals("APPLIED")) activeTab = "APPLIED";
            else if (norm.equals("NOT_RELEVANT") || norm.equals("NOT_RELAVANT") || norm.equals("NOTRELEVANT")) activeTab = "NOT_RELEVANT";
        }

        model.addAttribute("status", scan.status());
        model.addAttribute("jobs", jobs);
        model.addAttribute("allCount", scan.allJobs().size());
        model.addAttribute("counts", counts);
        model.addAttribute("pendingCount", counts.getOrDefault("PENDING", 0L));
        model.addAttribute("appliedCount", counts.getOrDefault("APPLIED", 0L));
        model.addAttribute("notRelevantCount", counts.getOrDefault("NOT_RELEVANT", 0L));
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("diagnostics", scan.tracker().getAllRecords());
        model.addAttribute("errorLogs", scan.tracker().getRecentErrorLogs());
        return "dashboard";
    }

    @PostMapping("/scan")
    public String scan(@RequestParam(defaultValue="true") boolean sendEmail,
                       @RequestParam(value = "tab", required = false, defaultValue = "PENDING") String tab){
        scan.runScan(sendEmail);
        return "redirect:/?tab=" + tab.toLowerCase();
    }

    @GetMapping("/scan") public String scanGet(){return "redirect:/";}

    @PostMapping("/api/jobs/{id}/category")
    @ResponseBody
    public ResponseEntity<?> updateCategoryApi(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, String> body,
                                               @RequestParam(required = false) String category) {
        String newCategory = body != null && body.containsKey("category") ? body.get("category") : category;
        if (newCategory == null || newCategory.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Category is required"));
        }
        var updated = scan.updateCategory(id, newCategory);
        if (updated.isPresent()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("id", id);
            resp.put("category", updated.get().getCategory());
            resp.put("counts", scan.getCategoryCounts());
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/jobs/{id}/category")
    public String updateCategoryForm(@PathVariable Long id,
                                     @RequestParam String category,
                                     @RequestParam(value = "tab", required = false, defaultValue = "PENDING") String tab) {
        scan.updateCategory(id, category);
        return "redirect:/?tab=" + tab.toLowerCase();
    }

    @GetMapping("/api/status") @ResponseBody public JobScanService.ScanStatus status(){return scan.status();}
    @GetMapping("/api/category-counts") @ResponseBody public Map<String, Long> categoryCounts(){return scan.getCategoryCounts();}
    @GetMapping("/api/health") @ResponseBody public java.util.List<SourceHealthTracker.HealthRecord> health(){return scan.tracker().getAllRecords();}
    @GetMapping("/api/errors") @ResponseBody public java.util.List<SourceErrorLog> errorLogs(){return scan.tracker().getRecentErrorLogs();}
}
