package io.qbilon.linkedin.util;

import java.util.HashSet;
import java.util.Set;

public class LinkShortener {

    private Set<String> shortener = new HashSet<>();

    public LinkShortener() {
        init();
    }

    public boolean contains(String domain) {
        return shortener.contains(domain);
    }

    private void init() {
        shortener.add("bit.ly");
        shortener.add("fcld.ly");
        shortener.add("linktr.ee");
        shortener.add("cutt.ly");
    }
    
}
