package popsi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import popsi.CompilerError.ErrorType;
import popsi.Token.TokenType;

public class Lexer {
    public static LexerResult lex(String src) {
        var lexer = new Lexer(src);
        var tokens = new ArrayList<Token>();
        var errors = new ArrayList<CompilerError>();
        while (true) {
            try {
                var token = lexer.scan();
                tokens.add(token);
                if (token.type() == TokenType.EOF) {
                    break;
                }
            } catch (LexerException e) {
                errors.add(e.err);
            }
        }
        if (errors.isEmpty()) {
            return new LexerResult.Success(tokens);
        } else {
            return new LexerResult.Error(errors);
        }
    }

    public static sealed interface LexerResult permits LexerResult.Success, LexerResult.Error {
        final record Success(List<Token> tokens) implements LexerResult {
        }

        final record Error(List<CompilerError> error) implements LexerResult {
        }
    }

    private class LexerException extends Exception {
        public CompilerError err;

        public LexerException(String message) {
            this(message, beginPos);
        }

        public LexerException(String message, FilePosition where) {
            this.err = new CompilerError(ErrorType.LEXICAL, message, where);
        }
    }

    /// Conteúdos do arquivo sendo analisado
    private String src;

    /// Posição do início do lexema atual
    private FilePosition beginPos;

    /// Posição atual no arquivo
    private FilePosition pos;

    // Índice do primeiro caractere do lexema atual
    private int begin;

    /// Índice do próximo caractere a ser lido
    private int current;

    private Lexer(String src) {
        this.src = src;
        this.pos = new FilePosition(1, 1, src);
        this.beginPos = this.pos;
        this.current = 0;
        this.begin = 0;
    }

    private Token scan() throws LexerException {
        begin = current;
        beginPos = pos;
        if (atEof()) {
            return token(TokenType.EOF);
        }
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
                    throw new LexerException("Símbolo não reconhecido (esperava '&&', encontrou '&')");
                }
            }
            case "|": {
                if (peek().equals("|")) {
                    next();
                    return token(TokenType.OR);
                } else {
                    throw new LexerException("Símbolo não reconhecido (esperava '||', encontrou '|')");
                }
            }
            // espaço em branco
            case " ":
            case "\t":
            case "\r":
            case "\n":
                while (isWhitespace(peek())) {
                    next();
                }
                return scan();

            case "\"":
                return string();

            // demais
            default: {
                if (isDigit(ch)) {
                    return number(ch);
                } else if (isIdentifierBegin(ch)) {
                    return identifier();
                } else {
                    throw new LexerException("Símbolo não reconhecido");
                }
            }

        }
    }

    private String next() {
        var ch = src.codePointAt(current);
        current += Character.charCount(ch);
        if (ch == Character.LINE_SEPARATOR) {
            pos = pos.nextLine();
        } else {
            // assumindo que os caracteres tem tamanho 1
            pos = pos.nextColumn();
        }
        return new String(Character.toChars(ch));
    }

    private String match(String expected) throws LexerException {
        if (atEof()) {
            throw new LexerException(String.format("Esperava %s, encontrado fim do arquivo", expected),
                    pos.previousColumn());
        }
        var ch = next();
        if (ch.equals(expected)) {
            return ch;
        }
        throw new LexerException(String.format("Esperava %s, encontrado \"%s\"", expected, ch), pos.previousColumn());
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
        while (!(ch = peek()).equals("") && isIdentifierContinuation(ch)) {
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

    private Token number(String firstDigit) throws LexerException {
        // já consumimos o primeiro caractere
        if (firstDigit.equals("0")) {
            if (peek().equals("x")) {
                next();
                while (!peek().isEmpty() && peek().matches("[0-9a-fA-F]")) {
                    next();
                }
                if (!peek().isEmpty() && !isWhitespace(peek())) {
                    throw new LexerException("Literal numérico inválido");
                }
                var literal = Long.parseLong(src.substring(begin + 2, current), 16);
                return token(TokenType.INTEGER, literal);
            } else if (peek().equals("b")) {
                next();
                while (!peek().isEmpty() && peek().matches("[01]")) {
                    next();
                }
                if (!peek().isEmpty() && !isWhitespace(peek())) {
                    throw new LexerException("Literal numérico inválido");
                }
                var literal = Long.parseLong(src.substring(begin + 2, current), 2);
                return token(TokenType.INTEGER, literal);
            } else if (peek().equals("o")) {
                next();
                while (!peek().isEmpty() && peek().matches("[0-7]")) {
                    next();
                }
                if (!peek().isEmpty() && !isWhitespace(peek())) {
                    throw new LexerException("Literal numérico inválido");
                }
                var literal = Long.parseLong(src.substring(begin + 2, current), 8);
                return token(TokenType.INTEGER, literal);
            } else {
                while (!peek().isEmpty() && isDigit(peek())) {
                    next();
                }
                if (peek().equals(".")) {
                    next();
                    if (!isDigit(peek())) {
                        throw new LexerException("Literal numérico inválido: esperava dígito após o ponto");
                    }
                    while (!peek().isEmpty() && isDigit(peek())) {
                        next();
                    }
                    var literal = Double.parseDouble(src.substring(begin, current));
                    return token(TokenType.FLOAT, literal);
                }
                var literal = Long.parseLong(src.substring(begin, current));
                return token(TokenType.INTEGER, literal);
            }
        }

        while (!peek().isEmpty() && isDigit(peek())) {
            next();
        }
        if (peek().equals(".")) {
            next();
            if (!isDigit(peek())) {
                throw new LexerException("Literal numérico inválido: esperava dígito após o ponto");
            }
            while (!peek().isEmpty() && isDigit(peek())) {
                next();
            }
            var literal = Double.parseDouble(src.substring(begin, current));
            return token(TokenType.FLOAT, literal);
        }
        var literal = Long.parseLong(src.substring(begin, current));
        return token(TokenType.INTEGER, literal);
    }

    private Token string() throws LexerException {
        // consumimos a abertura, agora vamos até o fechamento
        String ch;
        boolean escaping = false;

        HashMap<String, String> escapes = new HashMap<>();
        escapes.put("n", "\n");
        escapes.put("r", "\r");
        escapes.put("t", "\t");
        escapes.put("\"", "\"");
        escapes.put("\\", "\\");

        while (!(ch = peek()).isEmpty() && (!ch.equals("\"") || escaping)) {
            if (escaping) {
                if (!escapes.containsKey(ch)) {
                    var where = pos.previousColumn();
                    // consome até o final da string
                    while (!(ch = peek()).isEmpty() && (!ch.equals("\"") || escaping)) {
                        if (escaping) {
                            escaping = false;
                        } else if (ch.equals("\"")) {
                            escaping = true;
                        }
                        next();
                    }
                    match("\"");
                    throw new LexerException("Escape inválido", where);
                }
                escaping = false;
            } else if (ch.equals("\\")) {
                escaping = true;
            }
            next();
        }
        match("\"");
        var literal = src.substring(begin + 1, current - 1);
        // convert escapes
        literal = literal.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"")
                .replace("\\\\", "\\");
        return token(TokenType.STRING, literal);
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

    private boolean isWhitespace(String ch) {
        return ch.matches("[ \t\r\n]");
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
