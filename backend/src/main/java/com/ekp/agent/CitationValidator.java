package com.ekp.agent;
import java.util.regex.Pattern;
/** Structural check only: valid references do not prove factual support. */
public final class CitationValidator {
    private static final Pattern CITATION=Pattern.compile("\\[(\\d+)\\]");
    private CitationValidator() {}
    public static void validate(String answer,int sources) {
        var matcher=CITATION.matcher(answer); boolean found=false;
        while(matcher.find()) {
            found=true;
            try {
                int id=Integer.parseInt(matcher.group(1));
                if(id<1 || id>sources) throw new IllegalStateException("Answer cited an unavailable source");
            } catch(NumberFormatException e) { throw new IllegalStateException("Invalid citation",e); }
        }
        if(!found) throw new IllegalStateException("Answer contained no source citations");
    }
}
