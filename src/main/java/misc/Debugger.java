package misc;

/**
 * This class manages the output of debugging information to the console.
 */
public class Debugger {

    public static boolean PRINT_DEBUG_INFORMATION = true;

    // non-instantiable class
    private Debugger() { }

    public static void println(String x) {
        if(PRINT_DEBUG_INFORMATION) System.out.println(x);
    }

    public static void print(String x) {
        if(PRINT_DEBUG_INFORMATION) System.out.print(x);
    }

    public static void printlnErr(String x) {
        if(PRINT_DEBUG_INFORMATION) System.err.println(x);
    }

    public static void printErr(String x) {
        if(PRINT_DEBUG_INFORMATION) System.err.print(x);
    }

}
