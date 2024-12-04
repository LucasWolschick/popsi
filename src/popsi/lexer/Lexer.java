package popsi.lexer;

import java.util.ArrayList;
import java.util.List;

import popsi.CompilerError;
import popsi.FilePosition;
import popsi.Result;
import popsi.CompilerError.ErrorType;
import popsi.Result.Error;
import popsi.Result.Success;
import popsi.lexer.Token.TokenType;

public class Lexer {
    public static Result<List<Token>, List<CompilerError>> lex(String src) {
        var lexer = new Lexer(src);

        while (!lexer.atEof()) {
            lexer.scan();
        }
        lexer.token(TokenType.EOF);

        if (lexer.errors.isEmpty()) {
            return new Result.Success<>(lexer.tokens);
        } else {
            return new Result.Error<>(lexer.errors);
        }
    }

    /// Conteúdos do arquivo sendo analisado
    private String src;

    /// Tokens reconhecidos durante a análise
    private List<Token> tokens;

    /// Erros encontrados durante a análise
    private List<CompilerError> errors;

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
        this.tokens = new ArrayList<>();
        this.errors = new ArrayList<>();
        this.pos = new FilePosition(1, 1, src);
        this.beginPos = this.pos;
        this.current = 0;
        this.begin = 0;
    }

    private void scan() {
        begin = current;
        beginPos = pos;
        if (atEof()) {
            return;
        }
        var ch = next();

        switch (ch) {
            // um caractere
            case "(":
                token(TokenType.L_PAREN);
                return;
            case ")":
                token(TokenType.R_PAREN);
                return;
            case "[":
                token(TokenType.L_BRACKET);
                return;
            case "]":
                token(TokenType.R_BRACKET);
                return;
            case "{":
                token(TokenType.L_CURLY);
                return;
            case "}":
                token(TokenType.R_CURLY);
                return;
            case ":":
                token(TokenType.COLON);
                return;
            case ";":
                token(TokenType.SEMICOLON);
                return;
            case "#":
                token(TokenType.HASH);
                return;
            case ",":
                token(TokenType.COMMA);
                return;

            // dois caracteres
            case "=":
                token(match("=") ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
                return;
            case "%":
                token(match("=") ? TokenType.PERCENT_EQUAL : TokenType.PERCENT);
                return;
            case "!":
                token(match("=") ? TokenType.BANG_EQUAL : TokenType.BANG);
                return;
            case "+":
                token(match("=") ? TokenType.PLUS_EQUAL : TokenType.PLUS);
                return;
            case "-":
                token(match("=") ? TokenType.MINUS_EQUAL : match(">") ? TokenType.ARROW : TokenType.MINUS);
                return;
            case "/":
                if (match("/")) {
                    // comentário
                    while (!atEof() && !peek().equals("\n")) {
                        next();
                    }
                    begin = current;
                } else {
                    token(match("=") ? TokenType.SLASH_EQUAL : TokenType.SLASH);
                }
                return;
            case "*":
                token(match("=") ? TokenType.STAR_EQUAL : TokenType.STAR);
                return;
            case "^":
                token(match("=") ? TokenType.HAT_EQUAL : TokenType.HAT);
                return;
            case "<":
                token(match("=") ? TokenType.LESSER_EQUAL : TokenType.LESSER);
                return;
            case ">":
                token(match("=") ? TokenType.GREATER_EQUAL : TokenType.GREATER);
                return;
            case ".":
                token(match(".") ? TokenType.DOT_DOT : TokenType.DOT);
                return;
            case "&":
                if (peek().equals("&")) {
                    next();
                    token(TokenType.AND);
                } else {
                    error("Símbolo não reconhecido (encontrado '&', você não quis dizer '&&'?)");
                }
                return;
            case "|":
                if (peek().equals("|")) {
                    next();
                    token(TokenType.OR);
                } else {
                    error("Símbolo não reconhecido (encontrado '|', você não quis dizer '||'?)");
                }
                return;
            // espaço em branco
            case " ":
            case "\t":
            case "\r":
            case "\n":
                while (isWhitespace(peek())) {
                    next();
                }
                return;

            case "\"":
                string();
                return;

            // demais
            default: {
                if (isDigit(ch)) {
                    number(ch);
                } else if (isIdentifierBegin(ch)) {
                    identifier();
                } else {
                    error("Símbolo não reconhecido");
                }
                return;
            }

        }
    }

    private String next() {
        var ch = src.codePointAt(current);
        current += Character.charCount(ch);
        if (new String(Character.toChars(ch)).equals("\n")) {
            pos = pos.nextLine();
        } else {
            // assumindo que os caracteres tem tamanho 1
            pos = pos.nextColumn();
        }
        return new String(Character.toChars(ch));
    }

    private boolean match(String expected) {
        if (atEof()) {
            return false;
        }
        if (!peek().equals(expected)) {
            return false;
        }
        next();
        return true;
    }

    private String peek() {
        if (atEof()) {
            return "";
        }
        var ch = src.codePointAt(current);
        return new String(Character.toChars(ch));
    }

    private String peekNext() {
        if (atEof(current + 1)) {
            return "";
        }
        var ch = src.codePointAt(current);
        var ch2 = src.codePointAt(current + Character.charCount(ch));
        return new String(Character.toChars(ch2));
    }

    private void identifier() {
        // já consumimos o primeiro caractere
        String ch;
        while (!(ch = peek()).equals("") && isIdentifierContinuation(ch)) {
            next();
        }

        var lexeme = src.substring(begin, current);
        switch (lexeme) {
            case "fn":
                token(TokenType.FN);
                break;
            case "rec":
                token(TokenType.REC);
                break;
            case "let":
                token(TokenType.LET);
                break;
            case "for":
                token(TokenType.FOR);
                break;
            case "while":
                token(TokenType.WHILE);
                break;
            case "return":
                token(TokenType.RETURN);
                break;
            case "if":
                token(TokenType.IF);
                break;
            case "debug":
                token(TokenType.DEBUG);
                break;
            case "else":
                token(TokenType.ELSE);
                break;
            case "in":
                token(TokenType.IN);
                break;
            case "true":
                token(TokenType.TRUE);
                break;
            case "false":
                token(TokenType.FALSE);
                break;
            default:
                token(TokenType.IDENTIFIER);
                break;
        }
    }

    private void number(String firstDigit) {
        var zero = firstDigit.equals("0");
        if (zero && match("x")) {
            hexNumber();
        } else if (zero && match("b")) {
            binaryNumber();
        } else if (zero && match("o")) {
            octalNumber();
        } else {
            decimalNumber();
        }
    }

    private void hexNumber() {
        while (peek().matches("[0-9a-fA-F]")) {
            next();
        }
        var literal = Long.parseLong(src.substring(begin + 2, current), 16);
        token(TokenType.INTEGER, literal);
    }

    private void binaryNumber() {
        while (peek().matches("[01]")) {
            next();
        }
        var literal = Long.parseLong(src.substring(begin + 2, current), 2);
        token(TokenType.INTEGER, literal);
    }

    private void octalNumber() {
        while (peek().matches("[0-7]")) {
            next();
        }
        var literal = Long.parseLong(src.substring(begin + 2, current), 8);
        token(TokenType.INTEGER, literal);
    }

    private void decimalNumber() {
        while (isDigit(peek())) {
            next();
        }
        if (peek().equals(".") && isDigit(peekNext())) {
            next();
            while (isDigit(peek())) {
                next();
            }
            var literal = Double.parseDouble(src.substring(begin, current));
            token(TokenType.FLOAT, literal);
        } else {
            var literal = Long.parseLong(src.substring(begin, current));
            token(TokenType.INTEGER, literal);
        }
    }

    private void string() {
        while (!atEof() && !peek().equals("\"")) {
            if (peek().equals("\n")) {
                error("String não fechada");
                return;
            }
            stringContent();
        }

        if (atEof()) {
            error("String não fechada");
            return;
        }

        // fecha a string
        next();

        var literal = src.substring(begin + 1, current - 1);
        literal = literal.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        token(TokenType.STRING, literal);
    }

    private void stringContent() {
        if (peek().equals("\\")) {
            stringEscape();
        } else {
            next();
        }
    }

    private void stringEscape() {
        next();
        switch (peek()) {
            case "\"":
            case "\\":
            case "n":
            case "r":
            case "t":
                next();
                break;
            default:
                error("Escape inválido");
                next();
                break;
        }
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
        return atEof(current);
    }

    private boolean atEof(int where) {
        return where >= src.length();
    }

    private void token(TokenType type) {
        token(type, null);
    }

    private void token(TokenType type, Object literal) {
        var lexeme = src.substring(begin, current);
        tokens.add(new Token(lexeme, type, pos, literal));
    }

    private void error(String message) {
        errors.add(new CompilerError(ErrorType.LEXICAL, message, beginPos));
    }
}
