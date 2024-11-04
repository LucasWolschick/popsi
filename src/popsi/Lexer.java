package popsi;

import java.util.ArrayList;
import java.util.List;

import popsi.CompilerError.ErrorType;
import popsi.Token.TokenType;

public class Lexer {
    public static LexerResult lex(String src) {
        try {
            var lexer = new Lexer(src);
            var tokens = new ArrayList<Token>();
            while (true) {
                var token = lexer.scan();
                tokens.add(token);
                if (token.type() == TokenType.EOF) {
                    break;
                }
            }
            return new LexerResult.Success(tokens);
        } catch (LexerException e) {
            return new LexerResult.Error(e.err);
        }
    }

    private sealed interface LexerResult {
        record Success(List<Token> tokens) implements LexerResult {
        }

        record Error(CompilerError error) implements LexerResult {
        }
    }

    private class LexerException extends Exception {
        public CompilerError err;

        public LexerException(String message) {
            this.err = new CompilerError(ErrorType.LEXICAL, message, pos);
        }
    }

    /// Conteúdos do arquivo sendo analisado
    private String src;

    /// Posição atual no arquivo
    private FilePosition pos;

    // Índice do primeiro caractere do lexema atual
    private int begin;

    /// Índice do próximo caractere a ser lido
    private int current;

    private Lexer(String src) {
        this.src = src;
        this.pos = new FilePosition(1, 1);
        this.current = 0;
        this.begin = 0;
    }

    private Token scan() throws LexerException {
        if (atEof()) {
            return token(TokenType.EOF);
        }

        begin = current;
        var ch = next();

        switch (ch) {
            // um caractere
            case "(":
                return token(TokenType.L_PAREN);
            case ")":
                return token(TokenType.R_PAREN);
            case "[":
                return token(TokenType.L_BRACKET);
            case "]":
                return token(TokenType.R_BRACKET);
            case "{":
                return token(TokenType.L_CURLY);
            case "}":
                return token(TokenType.R_CURLY);
            case ":":
                return token(TokenType.COLON);
            case ";":
                return token(TokenType.SEMICOLON);
            case "#":
                return token(TokenType.HASH);
            case ",":
                return token(TokenType.COMMA);

            // dois caracteres
            case "=":
                switch (peek()) {
                    case "=":
                        next();
                        return token(TokenType.EQUAL_EQUAL);
                    default:
                        return token(TokenType.EQUAL);
                }
            case "%":
                switch (peek()) {
                    case "=":
                        next();
                        return token(TokenType.PERCENT_EQUAL);
                    default:
                        return token(TokenType.PERCENT);
                }
            case "!":
                switch (peek()) {
                    case "=":
                        next();
                        return token(TokenType.BANG_EQUAL);
                    default:
                        return token(TokenType.BANG);
                }
            case "+":
                switch (peek()) {
                    case "=":
                        next();
                        return token(TokenType.PLUS_EQUAL);
                    default:
                        return token(TokenType.PLUS);
                }
            case "-":
                switch (peek()) {
                    case "=":
                        next();
                        return token(TokenType.MINUS_EQUAL);
                    default:
                        return token(TokenType.MINUS);
                }
            case "/":
                switch (peek()) {
                    case "=":
                        next();
                        return token(TokenType.SLASH_EQUAL);
                    case "/":
                        // comment
                        while (!atEof() && !peek().equals("\n")) {
                            next();
                        }
                        return scan();
                    default:
                        return token(TokenType.SLASH);
                }
            case "*":
                switch (peek()) {
                    case "=":
                        next();
                        return token(TokenType.STAR_EQUAL);
                    default:
                        return token(TokenType.STAR);
                }
            case "^":
                switch (peek()) {
                    case "=":
                        next();
                        return token(TokenType.HAT_EQUAL);
                    default:
                        return token(TokenType.HAT);
                }
            case "<":
                switch (peek()) {
                    case "=":
                        next();
                        return token(TokenType.LESSER_EQUAL);
                    default:
                        return token(TokenType.LESSER);
                }
            case ">":
                switch (peek()) {
                    case "=":
                        next();
                        return token(TokenType.GREATER_EQUAL);
                    default:
                        return token(TokenType.GREATER);
                }
            case ".":
                switch (peek()) {
                    case ".":
                        next();
                        return token(TokenType.DOT_DOT);
                    default:
                        return token(TokenType.DOT);
                }
                // dois caracteres
            case "&": {
                if (peek().equals("&")) {
                    next();
                    return token(TokenType.AND);
                } else {
                    throw new LexerException("Token inválido");
                }
            }
            case "|": {
                if (peek().equals("|")) {
                    next();
                    return token(TokenType.OR);
                } else {
                    throw new LexerException("Token inválido");
                }
            }
            // espaço em branco
            case " ":
            case "\t":
            case "\r":
            case "\n":
                // TODO: stack overflow
                return scan();

            // strings TODO
            case "\"":
                return string();

            // demais
            default: {
                if (isIdentifierBegin(ch)) {
                    return identifier();
                } else if (isDigit(ch)) {
                    return number();
                } else {
                    throw new LexerException("Token inválido");
                }
            }

        }
    }

    private String next() {
        var ch = src.codePointAt(current);
        current += Character.charCount(ch);
        return new String(Character.toChars(ch));
    }

    private String peek() {
        if (atEof()) {
            return "";
        }
        var ch = src.codePointAt(current);
        return new String(Character.toChars(ch));
    }

    private Token identifier() {
        // já consumimos o primeiro caractere
        String ch;
        while ((ch = peek()) != "" && isIdentifierContinuation(ch)) {
            next();
        }

        var lexeme = src.substring(begin, current);
        switch (lexeme) {
            case "fn":
                return token(TokenType.FN);
            case "let":
                return token(TokenType.LET);
            case "for":
                return token(TokenType.FOR);
            case "while":
                return token(TokenType.WHILE);
            case "return":
                return token(TokenType.RETURN);
            case "if":
                return token(TokenType.IF);
            case "debug":
                return token(TokenType.DEBUG);
            default:
                return token(TokenType.IDENTIFIER);
        }
    }

    private Token number() throws LexerException {
        // TODO: já faz o parse do number pro valor java
        // já consumimos o primeiro caractere
        String ch;
        while ((ch = peek()) != "" && isDigit(ch)) {
            next();
        }
        if (ch == ".") {
            next();
            if (!isDigit(peek())) {
                throw new LexerException("Esperava número após '.'");
            }
            while ((ch = peek()) != "" && isDigit(ch)) {
                next();
            }
            return token(TokenType.FLOAT);
        }
        return token(TokenType.INTEGER);
    }

    private Token string() throws LexerException {
        // TODO: faz o parse da string pro valor java
        // consumimos a abertura, agora vamos até o fechamento
        String ch;
        boolean escaping = false;
        while ((ch = peek()) != "" && (ch != "\"" || escaping)) {
            if (ch == "\\") {
                escaping = true;
            } else {
                escaping = false;
            }
            next();
        }
        if (ch == "") {
            throw new LexerException("String não fechada");
        }
        // senão, chegamos ao "
        next();
        return token(TokenType.STRING);
    }

    private boolean isDigit(String ch) {
        return ch.matches("[0-9]");
    }

    private boolean isIdentifierBegin(String ch) {
        var cp = ch.codePointAt(0);
        return Character.isUnicodeIdentifierStart(cp) || Character.isEmoji(cp);
    }

    private boolean isIdentifierContinuation(String ch) {
        var cp = ch.codePointAt(0);
        return Character.isUnicodeIdentifierPart(cp) || Character.isEmoji(cp);
    }

    private boolean atEof() {
        return current >= src.length();
    }

    private Token token(TokenType type) {
        return token(type, null);
    }

    private Token token(TokenType type, Object literal) {
        var lexeme = src.substring(begin, current);
        return new Token(lexeme, type, pos, literal);
    }
}
