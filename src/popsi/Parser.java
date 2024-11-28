package popsi;

import java.util.*;

import popsi.ast.*;
import static popsi.ast.Ast.*;
import static popsi.ast.Statement.*;
import static popsi.ast.Expression.*;
import popsi.ast.Expression.*;
import popsi.ast.Statement.*;

public class Parser {
    private final List<Token> tokens;
    private int current;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.current = 0;
    }

    // Funções auxiliares
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
        Token name = consume(Token.TokenType.IDENTIFIER, "Esperado nome da função após 'fn'");
        consume(Token.TokenType.L_PAREN, "Esperado '(' após o nome da função");
        List<Parameter> parameters = parseParameterList();
        consume(Token.TokenType.R_PAREN, "Esperado ')' após os parâmetros");

        Token returnType = null;
        if (match(Token.TokenType.ARROW)) {
            returnType = consume(Token.TokenType.IDENTIFIER, "Esperado tipo de retorno após '->'");
        }

        Block body = bloco();
        return new Function(name, parameters, returnType, body);
    }

    private List<Parameter> parseParameterList() {
        List<Parameter> parameters = new ArrayList<>();
        if (!match(Token.TokenType.R_PAREN)) {
            do {
                Token name = consume(Token.TokenType.IDENTIFIER, "Esperado nome do parâmetro");
                consume(Token.TokenType.COLON, "Esperado ':' após o nome do parâmetro");
                Token type = consume(Token.TokenType.IDENTIFIER, "Esperado tipo do parâmetro");
                parameters.add(new Parameter(name, type));
            } while (match(Token.TokenType.COMMA));
        }
        return parameters;
    }

    private Block bloco() {
        consume(Token.TokenType.L_CURLY, "Esperado '{' no início de um bloco de código");
        List<Statement> statements = new ArrayList<>();
        while (!match(Token.TokenType.R_CURLY) && !atEoF()) {
            statements.add(parseStatement());
        }
        if (atEoF()) {
            throw new RuntimeException("Fim do arquivo encontrado enquanto esperava '}' para fechar o bloco");
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
            return new ExpressionStatement(expressao());
        }
    }

    private Declaration parseVariableDeclaration() {
        Token name = consume(Token.TokenType.IDENTIFIER, "Esperado nome da variável após 'let'");
        consume(Token.TokenType.COLON, "Esperado ':' após o nome da variável");
        Token type = consume(Token.TokenType.IDENTIFIER, "Esperado tipo da variável");
        consume(Token.TokenType.EQUAL, "Esperado '=' após ':' e tipo da variável");
        Expression value = expressao();
        consume(Token.TokenType.SEMICOLON, "Esperado ';' após a declaração da variável");
        return new Declaration(name, type, value);
    }

    private Statement parseForStatement() {
        Token variable = consume(Token.TokenType.IDENTIFIER, "Esperado nome da variável após 'for'");
        consume(Token.TokenType.COLON, "Esperado ':' após o nome da variável no loop 'for'");
        Token type = consume(Token.TokenType.IDENTIFIER, "Esperado tipo da variável no loop 'for'");
        consume(Token.TokenType.IN, "Esperado 'in' para indicar o intervalo do loop 'for'");
        Expression range = expressao();
        Block body = bloco();
        return new ForStatement(variable, type, range, body);
    }

    private Statement parseIfStatement() {
        Expression condition = expressao();
        Block thenBranch = bloco();
        Block elseBranch = null;
        if (match(Token.TokenType.ELSE)) {
            elseBranch = bloco();
        }
        return new IfStatement(condition, thenBranch, elseBranch);
    }

    private Statement parseWhileStatement() {
        Expression condition = expressao();
        Block body = bloco();
        return new WhileStatement(condition, body);
    }

    private Statement parseReturnStatement() {
        Expression value = expressao();
        consume(Token.TokenType.SEMICOLON, "Esperado ';' após 'return'");
        return new ReturnStatement(value);
    }

    private Statement parseDebugStatement() {
        Expression value = expressao();
        consume(Token.TokenType.SEMICOLON, "Esperado ';' após 'debug'");
        return new DebugStatement(value);
    }

    private Expression expressao() {
        return logic_or();
    }

    private Expression logic_or() {
        Expression left = logic_and();
        while (match(Token.TokenType.OR)) {
            Token operator = previous();
            Expression right = logic_and();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression logic_and() {
        Expression left = equality();
        while (match(Token.TokenType.AND)) {
            Token operator = previous();
            Expression right = equality();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression equality() {
        Expression left = comparison();
        while (match(Token.TokenType.EQUAL_EQUAL) || match(Token.TokenType.BANG_EQUAL)) {
            Token operator = previous();
            Expression right = comparison();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression comparison() {
        Expression left = termo();
        while (match(Token.TokenType.LESSER) || match(Token.TokenType.GREATER) ||
                match(Token.TokenType.LESSER_EQUAL) || match(Token.TokenType.GREATER_EQUAL)) {
            Token operator = previous();
            Expression right = termo();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression termo() {
        Expression left = fator();
        while (match(Token.TokenType.PLUS) || match(Token.TokenType.MINUS)) {
            Token operator = previous();
            Expression right = fator();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression fator() {
        Expression left = unary();
        while (match(Token.TokenType.STAR) || match(Token.TokenType.SLASH)) {
            Token operator = previous();
            Expression right = unary();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression unary() {
        if (match(Token.TokenType.MINUS) || match(Token.TokenType.BANG)) {
            Token operator = previous();
            Expression operand = unary();
            return new UnaryExpression(operator, operand);
        }
        return chamada();
    }

    private Expression chamada() {
        Expression expr = primary();
        while (match(Token.TokenType.L_PAREN)) {
            List<Expression> args = argumentos();
            consume(Token.TokenType.R_PAREN, "Esperado ')'");
            expr = new FunctionCall(previous(), args);
        }
        return expr;
    }

    private Expression primary() {
        if (match(Token.TokenType.INTEGER) || match(Token.TokenType.FLOAT) || match(Token.TokenType.STRING)) {
            return new Literal(previous());
        } else if (match(Token.TokenType.IDENTIFIER)) {
            return new VariableExpression(previous());
        } else if (match(Token.TokenType.L_PAREN)) {
            Expression expr = expressao();
            consume(Token.TokenType.R_PAREN, "Esperado ')'");
            return expr;
        }
        throw new RuntimeException("Expressão inválida encontrada: " + peek().lexeme());
    }

    private List<Expression> argumentos() {
        List<Expression> args = new ArrayList<>();
        if (!match(Token.TokenType.R_PAREN)) {
            do {
                args.add(expressao());
            } while (match(Token.TokenType.COMMA));
        }
        return args;
    }
}
