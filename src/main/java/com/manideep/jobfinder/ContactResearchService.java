package com.manideep.jobfinder;

import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ContactResearchService {
    public List<ContactLink> links(JobCandidate j){
        String loc=(j.location()==null||j.location().isBlank())?"India":j.location();
        String base="\""+j.company()+"\" \""+j.title()+"\" ";
        return List.of(
          make("🎓 NIT Bhopal Alumni Referral","\""+j.company()+"\" \"NIT Bhopal\" OR \"MANIT\" software"),
          make("Recruiter / Talent Acquisition",base+"recruiter OR \"talent acquisition\" \""+loc+"\""),
          make("Relevant hiring manager",base+"\"engineering manager\" OR \"hiring manager\" \""+loc+"\""),
          make("Potential referral contact",base+"engineer OR \"software engineer\" \""+loc+"\""));
    }
    private ContactLink make(String type,String q){
        String e=URLEncoder.encode(q,StandardCharsets.UTF_8);
        return new ContactLink(type,q,"https://www.linkedin.com/search/results/people/?keywords="+e,"https://www.google.com/search?q="+e);
    }
    public record ContactLink(String type,String query,String linkedinUrl,String webUrl){}
}
