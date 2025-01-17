package popsi;

public record CompilerError(ErrorType type, String message, FilePosition where) {
    public static enum ErrorType {
        LEXICAL, SYNTATIC, SEMANTIC
    }

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_BOLD = "\u001B[1m";

    public void printError() {
        /*
         * erro <tipo>: <message>
         * --> <line>:<column>
         * |
         * <line> | <source>
         * | ^ Aqui
         * |
         * 
         * erro: não foi possível compilar o programa devido ao(s) erro(s) acima.
         */

        // cabeçalho
        System.err.print(ANSI_RED + ANSI_BOLD);
        switch (type) {
            case LEXICAL -> System.err.print("erro léxico");
            case SYNTATIC -> System.err.print("erro sintático");
            case SEMANTIC -> System.err.print("erro semântico");
        }
        System.err.print(ANSI_RESET + ANSI_BOLD);
        System.err.println(": " + message);
        System.err.print(ANSI_RESET);

        // indicador de posição
        var lineNum = Integer.toString(where.line());
        var margin = " ".repeat(lineNum.length());
        System.err.printf(ANSI_BLUE + "%s-->" + ANSI_RESET + " %d:%d\n", margin, where.line(), where.column());

        // linha do erro
        System.err.printf(ANSI_BLUE + "%s |\n" + ANSI_RESET, margin);
        var line = where.src().lines().skip(where.line() - 1).findFirst().orElse("");
        System.err.printf(ANSI_BLUE + "%s |" + ANSI_RESET + " %s\n", lineNum, line);

        // indicador de posição
        var pointer = " ".repeat(where.column() - 1) + "^";
        System.err.printf(ANSI_BLUE + "%s |" + ANSI_RED + " %s\n" + ANSI_RESET, margin, pointer);

        // linha vazia
        System.err.println(ANSI_RESET);
    }
}
