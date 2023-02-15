package io.qbilon.linkedin.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JobDescriptors {

    private List<String> descriptors = new ArrayList<>();

    public JobDescriptors() {
        init();
    }

    public List<String> getJobDescriptors(String companyName) {
        return descriptors.stream().map( d -> d + companyName).collect(Collectors.toList());
    }

    private void init() {
        descriptors.add(" at ");
        descriptors.add(" @ ");
        descriptors.add(" @");
        descriptors.add(" | ");
        descriptors.add("| ");
        descriptors.add(" |");
        descriptors.add("|");
        descriptors.add(" bei ");
        descriptors.add(" ");
        descriptors.add("");
    }
    
}
