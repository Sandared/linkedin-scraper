package io.qbilon.linkedin.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SpecialChars {

    private Map<String, String> replacements = new HashMap<>();

    public SpecialChars(){
        init();
    }

    public String replacementFor(String s) {
        return replacements.get(s);
    }

    public Set<String> specials() {
        return replacements.keySet();
    }

    private void init() {
        replacements.put("ä", "ae");
        replacements.put("ö", "oe");
        replacements.put("ü", "ue");
        replacements.put("ß", "ss");

        replacements.put("á", "a");
        replacements.put("à", "a");
        replacements.put("â", "a");

        replacements.put("é", "e");
        replacements.put("è", "e");
        replacements.put("ê", "e");
        
        replacements.put("í", "i");
        replacements.put("ì", "i");
        replacements.put("î", "i");

        replacements.put("ó", "o");
        replacements.put("ò", "o");
        replacements.put("ô", "o");
        replacements.put("ø", "o");

        replacements.put("ú", "u");
        replacements.put("ù", "u");
        replacements.put("û", "u");
        
        replacements.put("č", "c");
        replacements.put("ć", "c");
        replacements.put("ç", "c");

        replacements.put("ñ", "n");

    }
    
}
