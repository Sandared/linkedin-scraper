package io.qbilon.linkedin.util;

import java.util.HashSet;
import java.util.Set;

public class RemovableNameSegments {
    private Set<String> segments = new HashSet<>();

    public RemovableNameSegments() {
        init();
    }

    public Set<String> removableSegments() {
        return segments;
    }

    private void init() {
        segments.add("prof.");
        segments.add("prof");
        segments.add("dr.");
        segments.add("dr");
    }
    
}
