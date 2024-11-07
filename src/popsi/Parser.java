package popsi;

import java.util.*;

public class Parser {
    private List<Token> tokens;
    private int current;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.current = 0;
    }

    // Funções Auxiliares
    private boolean atEoF() {
        return peek().type() == Token.TokenType.EOF;
    }

    private Token next() {
        if (!atEoF())
            current++;
        return previous();
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peekNext() {
        return tokens.get(current + 1);
    }

    private boolean match(Token.TokenType type) {
        if (peek().type() == type) {
            next();
            return true;
        }

        return false;
    }

    private Token consume(Token.TokenType type, String errorMessage) {
        if (match(type)) {
            return previous();
        }

        throw new RuntimeException(errorMessage + " em " + peek().where());
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    // Funções de análise da gramática
    public void parse() {
        while (!atEoF()) {
            parseDeclaration();
        }
    }

    private void parseDeclaration() {
        if (match(Token.TokenType.FN)) {
            parseDeclaration();
        } else {
            parseStatement();
        }
    }

    private void parseFunctionDeclaration() {
        consume(Token.TokenType.IDENTIFIER, "Esperado nome da função após 'fn'");
        consume(Token.TokenType.L_PAREN, "Esperado '(' após o nome da função");
        parseParameterList();
        consume(Token.TokenType.R_PAREN, "Esperado ')' após os parâmetros");
        consume(Token.TokenType.L_CURLY, "Esperado '{' no início do corpo da função");
        parseBlock();
        consume(Token.TokenType.R_CURLY, "Esperado '}' no final do corpo da função");
    }

    private void parseParameterList() {
        if (!match(Token.TokenType.R_PAREN)) {
            do {
                consume(Token.TokenType.IDENTIFIER, "Esperado nome do parâmetro");
                consume(Token.TokenType.COLON, "Esperado ':' após o nome do parâmetro");
                consume(Token.TokenType.IDENTIFIER, "Esperado tipo do parâmetro");
            } while (match(Token.TokenType.COMMA));
        }
    }

    private void parseBlock() {
        while (!match(Token.TokenType.R_CURLY) && !atEoF()) {
            parseStatement();
        }
    }

    private void parseStatement() {
        if (match(Token.TokenType.LET)) {
            parseVariableDeclaration();
        } else if (match(Token.TokenType.FOR)) {
            parseForStatement();
        } else if (match(Token.TokenType.IF)) {
            parseIfStatement();
        } else if (match(Token.TokenType.RETURN)) {
            parseReturnStatement();
        } else if (match(Token.TokenType.WHILE)) {
            parseWhileStatement();
        } else {
            parseExpression();
        }
    }

    private void parseVariableDeclaration() {
        consume(Token.TokenType.IDENTIFIER, "Esperado nome da variável após 'let'");
        consume(Token.TokenType.COLON, "Esperado ':' após o nome da variável");
        consume(Token.TokenType.IDENTIFIER, "Esperado tipo da variável");
        if (match(Token.TokenType.EQUAL)) {
            parseExpression();
        }
        consume(Token.TokenType.SEMICOLON, "Esperado ';' após a declaração da variável");
    }

    private void parseForStatement() {
        // parse pro for
    }

    private void parseIfStatement() {
        // Parse pro if
    }

    private void parseWhileStatement() {
        // Parse pro while
    }

    private void parseReturnStatement() {
        parseExpression();
        consume(Token.TokenType.SEMICOLON, "Esperado ';' após a declaração return");
    }

    private void parseExpression() {
        // Implementar a análise de expressões, incluindo operadores e literais
    }
}
