package popsi;

public record Token(String lexeme, TokenType type, FilePosition where, Object literal) {
    public static enum TokenType {
        // tokens de um caractere
        L_PAREN, R_PAREN, L_BRACKET, R_BRACKET, L_CURLY, R_CURLY,
        COLON, SEMICOLON, HASH, COMMA,

        // tokens de um caractere que podem ser dois
        EQUAL, PERCENT, BANG, PLUS, MINUS, SLASH, STAR, HAT, LESSER, GREATER, DOT,

        // tokens de dois caracteres que podem ser um
        EQUAL_EQUAL, PERCENT_EQUAL, BANG_EQUAL, PLUS_EQUAL, MINUS_EQUAL, SLASH_EQUAL,
        STAR_EQUAL, HAT_EQUAL, LESSER_EQUAL, GREATER_EQUAL, DOT_DOT,

        // tokens de dois caracteres
        OR, AND,

        // palavras reservadas
        FN, LET, FOR, WHILE, RETURN, IF, DEBUG,

        // literais
        IDENTIFIER, INTEGER, FLOAT, STRING, CHAR,

        // EOF
        EOF,

        // erro
        INVALID
    }

    @Override
    public String toString() {
        return String.format("(%s, %s)", type, lexeme);
    }
}
