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
        // if (match(Token.TokenType.ARROW)) {
        // consume(Token.TokenType.IDENTIFIER, "Esperado tipo de retorno após '->'");
        // }
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
        } else if (match(Token.TokenType.WHILE)) {
            parseWhileStatement();
        } else if (match(Token.TokenType.IF)) {
            parseIfStatement();
        } else if (match(Token.TokenType.RETURN)) {
            parseReturnStatement();
        } else if (match(Token.TokenType.DEBUG)) {
            parseDebugStatement();
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
        consume(Token.TokenType.IDENTIFIER, "Esperado nome da variável do loop 'for'");
        consume(Token.TokenType.COLON, "Esperado ':' após a variável do loop");
        consume(Token.TokenType.IDENTIFIER, "Esperado tipo da variável do loop");

        // consume(Token.TokenType.IN, "Esperado 'in' para o intervalo do loop");
        parseRangeExpression();
        parseBlock();
    }

    private void parseIfStatement() {
        consume(Token.TokenType.L_PAREN, "Esperado '(' após 'if'");
        parseExpression();
        consume(Token.TokenType.R_PAREN, "Esperado ')' após a expressão do 'if'");
        parseBlock();

        // if (match(Token.TokenType.ELSE)) {
        // if (match(Token.TokenType.IF)) {
        // parseIfStatement(); // "else if"
        // } else {
        // parseBlock(); // "else" block
        // }
        // }
    }

    private void parseWhileStatement() {
        consume(Token.TokenType.L_PAREN, "Esperado '(' após 'while'");
        parseExpression();
        consume(Token.TokenType.R_PAREN, "Esperado ')' após a expressão do 'while'");
        parseBlock();
    }

    private void parseReturnStatement() {
        parseExpression();
        consume(Token.TokenType.SEMICOLON, "Esperado ';' após a declaração 'return'");
    }

    private void parseDebugStatement() {
        parseExpression();
        consume(Token.TokenType.SEMICOLON, "Esperado ';' após o comando 'debug'");
    }

    private void parseRangeExpression() {
        consume(Token.TokenType.DOT_DOT, "Esperado '..' no intervalo");
        consume(Token.TokenType.HASH, "Esperado '#' para denotar comprimento do vetor");
        consume(Token.TokenType.IDENTIFIER, "Esperado nome do vetor após '#'");
    }

    private void parseExpression() {
        // Implementação da análise de expressões, incluindo operadores e literais
    }
}
