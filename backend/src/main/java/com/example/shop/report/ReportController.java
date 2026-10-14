package com.example.shop.report;

import com.example.shop.report.ReportService.Report;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Sales reports; requires the REPORTS admin section (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** @param days 7, 30, 90 or 365 (the last N days including today) */
    @GetMapping
    public Report report(@RequestParam(defaultValue = "30") int days) {
        return reports.report(days);
    }
}
