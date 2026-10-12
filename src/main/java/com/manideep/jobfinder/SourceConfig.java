package com.manideep.jobfinder;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "jobfinder")
public class SourceConfig {
    private List<Board> greenhouse = new ArrayList<>();
    private List<Board> lever = new ArrayList<>();
    private List<Board> ashby = new ArrayList<>();
    private List<Board> smartrecruiters = new ArrayList<>();
    private List<Board> workday = new ArrayList<>();
    private List<CareerPage> careerPages = new ArrayList<>();
    private int targetCtcLpa = 33;
    private int maxAgeDays = 30;
    private int minScore = 59;

    public List<Board> getSmartrecruiters() {
        return smartrecruiters;
    }

    public void setSmartrecruiters(List<Board> v) {
        smartrecruiters = v;
    }

    public List<Board> getGreenhouse() {
        return greenhouse;
    }

    public void setGreenhouse(List<Board> v) {
        greenhouse = v;
    }

    public List<Board> getLever() {
        return lever;
    }

    public void setLever(List<Board> v) {
        lever = v;
    }

    public List<Board> getAshby() {
        return ashby;
    }

    public List<Board> getWorkday() {
        return workday;
    }

    public void setWorkday(List<Board> v) {
        workday = v;
    }

    public List<CareerPage> getCareerPages() {
        return careerPages;
    }

    public void setCareerPages(List<CareerPage> v) {
        careerPages = v;
    }

    public void setAshby(List<Board> v) {
        ashby = v;
    }

    public int getTargetCtcLpa() {
        return targetCtcLpa;
    }

    public void setTargetCtcLpa(int v) {
        targetCtcLpa = v;
    }

    public int getMaxAgeDays() {
        return maxAgeDays;
    }

    public void setMaxAgeDays(int v) {
        maxAgeDays = v;
    }

    public int getMinScore() {
        return minScore;
    }

    public void setMinScore(int v) {
        minScore = v;
    }

    public static class Board {
        private String company;
        private String token;
        private String site;
        private String name;
        private String identifier;
        private String careersUrl;

        public String getIdentifier() {
            return identifier != null ? identifier : (token != null ? token : name);
        }

        public void setIdentifier(String v) {
            identifier = v;
        }

        public String getCompany() {
            return company;
        }

        public void setCompany(String v) {
            company = v;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String v) {
            token = v;
        }

        public String getSite() {
            return site;
        }

        public void setSite(String v) {
            site = v;
        }

        public String getName() {
            return name;
        }

        public void setName(String v) {
            name = v;
        }

        public String getCareersUrl() {
            return careersUrl;
        }

        public void setCareersUrl(String v) {
            careersUrl = v;
        }
    }

    public static class CareerPage {
        private String company;
        private String url;

        public String getCompany() {
            return company;
        }

        public void setCompany(String v) {
            company = v;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String v) {
            url = v;
        }
    }
}
