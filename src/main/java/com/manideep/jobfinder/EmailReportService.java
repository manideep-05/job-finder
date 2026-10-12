package com.manideep.jobfinder;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.time.Instant;
import java.util.List;

@Service
public class EmailReportService {
    private final JavaMailSender sender;
    @Value("${jobfinder.email.to}") private String to;
    @Value("${jobfinder.email.from:}") private String from;
    public EmailReportService(JavaMailSender sender){this.sender=sender;}
    public void send(List<JobScanService.ReportJob> jobs,List<String> errors,Instant scanTime) throws Exception {
        MimeMessage msg=sender.createMimeMessage(); MimeMessageHelper h=new MimeMessageHelper(msg,true,"UTF-8");
        h.setTo(to); if(from!=null&&!from.isBlank())h.setFrom(from);
        h.setSubject("[Job Finder] " + jobs.size() + " new matching job(s) · " + scanTime);
        h.setText(buildHtml(jobs,errors,scanTime),true);
        try {
            sender.send(msg);
        } catch (Exception ex) {
            String msgText = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            if (msgText.contains("timed out") || msgText.contains("ConnectException") || msgText.contains("MailConnectException")) {
                throw new Exception("SMTP connection timed out. Outbound port may be blocked by your ISP or firewall.", ex);
            }
            throw ex;
        }
    }
    private String esc(String s){return HtmlUtils.htmlEscape(s==null?"":s);}
    private String buildHtml(List<JobScanService.ReportJob> jobs,List<String> errors,Instant time){
        StringBuilder b=new StringBuilder("<html><body style='font-family:Arial,sans-serif;color:#172033'><h1>Job Finder Report</h1><p>Scan: "+time+"</p><p>Showing newly discovered qualifying openings from this scan run, ordered by match score (highest first).</p>");
        if(jobs.isEmpty())b.append("<p>No new qualifying jobs were discovered in this scan run. All detected jobs are already tracked in your dashboard.</p>");
        for(var r:jobs){
            var j=r.candidate();var s=r.result();
            b.append("<hr><h2><a href='").append(esc(j.jobUrl())).append("'>").append(esc(j.title())).append("</a></h2><p>")
             .append(esc(j.company())).append(" · ").append(esc(j.location())).append(" · ").append(esc(j.source())).append("</p><p><b>Score:</b> ")
             .append(s.score()).append("/100 · <b>Compensation:</b> ").append(esc(s.compensationStatus())).append(" — ").append(esc(j.compensationText())).append("</p><p><a href='")
             .append(esc(j.jobUrl())).append("'>Official application page</a></p><h3>Why it matched</h3><ul>");
            for(String reason:s.reasons())b.append("<li>").append(esc(reason)).append("</li>");
            b.append("</ul><h3>Contact research</h3><ul>");
            for(var c:r.contacts())b.append("<li><b>").append(esc(c.type())).append(":</b> <a href='").append(esc(c.linkedinUrl())).append("'>LinkedIn people search</a> · <a href='").append(esc(c.webUrl())).append("'>Web search</a></li>");
            b.append("</ul><p><small>Contact links are search leads, not verified identities or proof of ownership of this requisition.</small></p>");
        }
        if(!errors.isEmpty()){b.append("<hr><h2>Source errors</h2><ul>");for(String e:errors)b.append("<li>").append(esc(e)).append("</li>");b.append("</ul>");}
        return b.append("</body></html>").toString();
    }
}
