package popsi;

import java.util.*;

import popsi.ast.*;
import static popsi.ast.Ast.*;
import static popsi.ast.Statement.*;
import static popsi.ast.Expression.*;

public class Parser {
    private final List<Token> tokens;
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
        return tokens.get(Math.min(current + 1, tokens.size() - 1));
    }

    private boolean match(Token.TokenType type) {
        if (peek().type() == type) {
            next();
            return true;
        }
        return false;
    }

    private Token consume(Token.TokenType type, String errorMessage) {
        if (match(type))
            return previous();
        throw new RuntimeException(errorMessage + " em " + peek().where());
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    // Função principal
    public Program parse() {
        List<Function> functions = new ArrayList<>();
        while (!atEoF()) {
            functions.add(parseFunctionDeclaration());
        }
        return new Program(functions);
    }

    private Function parseFunctionDeclaration() {
        consume(Token.TokenType.FN, "Esperado 'fn' no início da declaração de função");
        String name = consume(Token.TokenType.IDENTIFIER, "Esperado nome da função após 'fn'").lexeme();
        consume(Token.TokenType.L_PAREN, "Esperado '(' após o nome da função");
        List<Parameter> parameters = parseParameterList();
        consume(Token.TokenType.R_PAREN, "Esperado ')' após os parâmetros");

        String returnType = null;
        if (match(Token.TokenType.ARROW)) {
            returnType = consume(Token.TokenType.IDENTIFIER, "Esperado tipo de retorno após '->'").lexeme();
        }

        Block body = parseBlock();
        return new Function(name, parameters, returnType, body);
    }

    private List<Parameter> parseParameterList() {
        List<Parameter> parameters = new ArrayList<>();
        if (!match(Token.TokenType.R_PAREN)) {
            do {
                String name = consume(Token.TokenType.IDENTIFIER, "Esperado nome do parâmetro").lexeme();
                consume(Token.TokenType.COLON, "Esperado ':' após o nome do parâmetro");
                String type = consume(Token.TokenType.IDENTIFIER, "Esperado tipo do parâmetro").lexeme();
                parameters.add(new Parameter(name, type));
            } while (match(Token.TokenType.COMMA));
        }
        return parameters;
    }

    private Block parseBlock() {
        consume(Token.TokenType.L_CURLY, "Esperado '{' no início do bloco");
        List<Statement> statements = new ArrayList<>();
        while (!match(Token.TokenType.R_CURLY) && !atEoF()) {
            statements.add(parseStatement());
        }
        return new Block(statements);
    }

    private Statement parseStatement() {
        if (match(Token.TokenType.LET)) {
            return parseVariableDeclaration();
        } else if (match(Token.TokenType.FOR)) {
            return parseForStatement();
        } else if (match(Token.TokenType.WHILE)) {
            return parseWhileStatement();
        } else if (match(Token.TokenType.IF)) {
            return parseIfStatement();
        } else if (match(Token.TokenType.RETURN)) {
            return parseReturnStatement();
        } else if (match(Token.TokenType.DEBUG)) {
            return parseDebugStatement();
        } else {
            return new ExpressionStatement(parseExpression());
        }
    }

    private Declaration parseVariableDeclaration() {
        String name = consume(Token.TokenType.IDENTIFIER, "Esperado nome da variável após 'let'").lexeme();
        consume(Token.TokenType.COLON, "Esperado ':' após o nome da variável");
        String type = consume(Token.TokenType.IDENTIFIER, "Esperado tipo da variável").lexeme();
        Expression value = null;
        if (match(Token.TokenType.EQUAL)) {
            value = parseExpression();
        }
        consume(Token.TokenType.SEMICOLON, "Esperado ';' após a declaração da variável");
        return new Declaration(name, type, value);
    }

    private Statement parseForStatement() {
        String variable = consume(Token.TokenType.IDENTIFIER, "Esperado nome da variável do loop 'for'").lexeme();
        consume(Token.TokenType.COLON, "Esperado ':' após a variável do loop");
        String type = consume(Token.TokenType.IDENTIFIER, "Esperado tipo da variável do loop").lexeme();
        consume(Token.TokenType.IN, "Esperado 'in' para o intervalo do loop");
        Expression range = parseExpression();
        Block body = parseBlock();
        return new ForStatement(variable, type, range, body);
    }

    private Statement parseIfStatement() {
        consume(Token.TokenType.L_PAREN, "Esperado '(' após 'if'");
        Expression condition = parseExpression();
        consume(Token.TokenType.R_PAREN, "Esperado ')' após a expressão do 'if'");
        Block thenBranch = parseBlock();
        Block elseBranch = null;
        if (match(Token.TokenType.ELSE)) {
            elseBranch = parseBlock();
        }
        return new IfStatement(condition, thenBranch, elseBranch);
    }

    private Statement parseWhileStatement() {
        consume(Token.TokenType.L_PAREN, "Esperado '(' após 'while'");
        Expression condition = parseExpression();
        consume(Token.TokenType.R_PAREN, "Esperado ')' após a expressão do 'while'");
        Block body = parseBlock();
        return new WhileStatement(condition, body);
    }

    private Statement parseReturnStatement() {
        Expression value = parseExpression();
        consume(Token.TokenType.SEMICOLON, "Esperado ';' após a declaração 'return'");
        return new ReturnStatement(value);
    }

    private Statement parseDebugStatement() {
        Expression value = parseExpression();
        consume(Token.TokenType.SEMICOLON, "Esperado ';' após o comando 'debug'");
        return new DebugStatement(value);
    }

    private Expression parseExpression() {
        return parseBinaryExpression(0);
    }

    // Análise de operadores binários com precedência
    private Expression parseBinaryExpression(int precedence) {
        Expression left = parsePrimary();
        while (!atEoF() && getPrecedence(peek().type()) >= precedence) {
            Token operator = next();
            Expression right = parseBinaryExpression(getPrecedence(operator.type()) + 1);
            left = new BinaryExpression(left, BinaryOperator.fromTokenType(operator.type()), right);
        }
        return left;
    }

    private Expression parsePrimary() {
        if (match(Token.TokenType.IDENTIFIER)) {
            return new VariableExpression(previous());
        } else if (match(Token.TokenType.INTEGER) || match(Token.TokenType.FLOAT) || match(Token.TokenType.STRING)) {
            return new Literal(previous().literal());
        } else if (match(Token.TokenType.L_PAREN)) {
            Expression expression = parseExpression();
            consume(Token.TokenType.R_PAREN, "Esperado ')' após a expressão");
            return expression;
        }
        throw new RuntimeException("Expressão inválida em " + peek().where());
    }

    private int getPrecedence(Token.TokenType type) {
        return switch (type) {
            case EQUAL_EQUAL, BANG_EQUAL -> 1;
            case LESSER, GREATER, LESSER_EQUAL, GREATER_EQUAL -> 2;
            case PLUS, MINUS -> 3;
            case STAR, SLASH -> 4;
            case HAT -> 5; // Exponenciação
            default -> 0;
        };
    }
}
