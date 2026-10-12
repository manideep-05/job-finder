package com.manideep.jobfinder;

import java.util.List;

public interface JobSourceAdapter {
    String sourceName();
    List<JobCandidate> fetch() throws Exception;
}
