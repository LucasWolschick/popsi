package popsi;

public record CompilerError(ErrorType type, String message, FilePosition where) {
    public static enum ErrorType {
        LEXICAL, SYNTATIC, SEMANTIC
    }
}
