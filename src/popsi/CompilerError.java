package popsi;

public record CompilerError(ErrorType type, String message, FilePosition where) {
    public static enum ErrorType {
        LEXICAL, SYNTATIC, SEMANTIC
    }

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
        switch (type) {
            case LEXICAL -> System.err.print("erro léxico: ");
            case SYNTATIC -> System.err.print("erro sintático: ");
            case SEMANTIC -> System.err.print("erro semântico: ");
        }
        System.err.println(message);

        // indicador de posição
        var lineNum = Integer.toString(where.line());
        var margin = " ".repeat(lineNum.length());
        System.err.printf("%s--> %d:%d\n", margin, where.line(), where.column());

        // linha do erro
        System.err.printf("%s |\n", margin);
        var line = where.src().lines().skip(where.line() - 1).findFirst().orElse("");
        System.err.printf("%s | %s\n", lineNum, line);

        // indicador de posição
        var pointer = " ".repeat(where.column() - 1) + "^";
        System.err.printf("%s | %s\n", margin, pointer);

        // linha vazia
        System.err.println("");
    }
}
