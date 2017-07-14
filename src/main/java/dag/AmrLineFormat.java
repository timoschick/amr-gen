package dag;

/**
 * Simple enum class used by {@link AmrParser} to differentiate between the format used by JAMR and the external alignment format.
 */

public enum AmrLineFormat {
    EXTERNAL, JAMR;

    public static String getWordNodeSeparator(AmrLineFormat format) {
        switch(format) {
            case EXTERNAL: return "-";
            case JAMR: return "\\|";
        }
        return "";
    }

}
