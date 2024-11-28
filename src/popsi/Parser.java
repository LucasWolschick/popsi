package popsi;

import java.util.*;

import popsi.ast.*;

import static popsi.ast.Ast.*;
import static popsi.ast.Statement.*;
import static popsi.ast.Expression.*;
import popsi.CompilerError.ErrorType;
import popsi.Token.TokenType;

public class Parser {
    // Função principal
    public static Result<Program, List<CompilerError>> parse(List<Token> tokens) {
        var parser = new Parser(tokens);

        List<Function> functions = new ArrayList<>();
        while (!parser.atEoF()) {
            try {
                var func = parser.function();
                functions.add(func);
            } catch (ParseError e) {
                parser.sincronizar();
            }
        }

        if (!parser.errors.isEmpty()) {
            return new Result.Error<>(parser.errors);
        } else {
            return new Result.Success<>(new Program(functions));
        }
    }

    // Membros privados
    private List<Token> tokens;
    private int current;

    private List<CompilerError> errors;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.current = 0;
        this.errors = new ArrayList<>();
    }

    // Funções auxiliares
    private boolean atEoF() {
        return peek().type() == TokenType.EOF;
    }

    private Token next() {
        if (!atEoF())
            current++;
        return previous();
    }

    private Token peek() {
        return tokens.get(current);
    }

    private boolean match(TokenType... type) {
        for (var t : type) {
            if (peek().type() == t) {
                next();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String errorMessage) {
        if (match(type))
            return previous();
        throw error(errorMessage);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private static class ParseError extends RuntimeException {
    }

    private ParseError error(String message) {
        errors.add(new CompilerError(ErrorType.SYNTATIC, message, peek().where()));
        return new ParseError();
    }

    private void sincronizar() {
        next();
        while (!atEoF()) {
            if (previous().type() == TokenType.SEMICOLON)
                return;

            // TODO: adicionar mais pontos de sincronização
            next();
        }
    }

    // Gramática
    private Function function() {
        consume(TokenType.FN, "Esperado 'fn' no início da declaração de função");
        Token name = consume(TokenType.IDENTIFIER, "Esperado nome da função após 'fn'");
        consume(TokenType.L_PAREN, "Esperado '(' após o nome da função");
        List<Parameter> parameters = parameters();
        consume(TokenType.R_PAREN, "Esperado ')' após os parâmetros");

        Optional<Type> returnType = Optional.empty();
        if (match(TokenType.ARROW)) {
            returnType = Optional.of(type());
        }

        Block body = block();
        return new Function(name, parameters, returnType, body);
    }

    private List<Parameter> parameters() {
        List<Parameter> parameters = new ArrayList<>();
        if (peek().type() != TokenType.R_PAREN) {
            do {
                Token name = consume(TokenType.IDENTIFIER, "Esperado nome do parâmetro");
                consume(TokenType.COLON, "Esperado ':' após o nome do parâmetro");
                var type = type();
                parameters.add(new Parameter(name, type));
            } while (match(TokenType.COMMA));
        }
        return parameters;
    }

    private Block block() {
        consume(TokenType.L_CURLY, "Esperado '{' no início de um bloco de código");

        var readSemicolon = false;
        var statements = new ArrayList<Statement>();
        while (peek().type() != TokenType.R_CURLY) {
            readSemicolon = false;
            statements.add(statement());
            if (peek().type() == TokenType.SEMICOLON) {
                readSemicolon = true;
                next();
            }
        }
        consume(TokenType.R_CURLY, "Esperado '}' no final de um bloco de código");

        if (!readSemicolon && !statements.isEmpty()) {
            var last = statements.removeLast();
            return new Block(statements, Optional.of(last));
        } else {
            return new Block(statements, Optional.empty());
        }

    }

    private Statement statement() {
        if (match(TokenType.LET)) {
            return declaration();
        } else {
            return new ExpressionStatement(expression());
        }
    }

    private Declaration declaration() {
        Token name = consume(TokenType.IDENTIFIER, "Esperado nome da variável após 'let'");
        consume(TokenType.COLON, "Esperado ':' após o nome da variável");
        var type = type();
        consume(TokenType.EQUAL, "Esperado '=' após ':' e tipo da variável");
        Expression value = expression();
        consume(TokenType.SEMICOLON, "Esperado ';' após a declaração da variável");
        return new Declaration(name, type, value);
    }

    private Expression expression() {
        return switch (peek().type()) {
            case TokenType.IF, TokenType.WHILE, TokenType.L_CURLY -> blockExpression();
            default -> blocklessExpression();
        };
    }

    private Expression blockExpression() {
        if (match(TokenType.IF)) {
            return ifExpression();
        } else if (peek().type() == TokenType.WHILE || peek().type() == TokenType.FOR) {
            return loop();
        } else {
            return block();
        }
    }

    private Expression ifExpression() {
        Expression condition = expression();
        Block thenBranch = block();
        Optional<Block> elseBranch = Optional.empty();
        if (match(TokenType.ELSE)) {
            elseBranch = Optional.of(block());
        }
        return new IfExpression(condition, thenBranch, elseBranch);
    }

    private Expression loop() {
        if (match(TokenType.WHILE)) {
            return whileExpression();
        } else if (match(TokenType.FOR)) {
            return forExpression();
        } else {
            // unreachable
            throw new IllegalArgumentException();
        }
    }

    private Expression forExpression() {
        Token variable = consume(TokenType.IDENTIFIER, "Esperado nome da variável após 'for'");
        consume(TokenType.COLON, "Esperado ':' após o nome da variável no loop 'for'");
        var type = type();
        consume(TokenType.IN, "Esperado 'in' para indicar o intervalo do loop 'for'");
        Expression range = expression();
        Block body = block();
        return new ForExpression(variable, type, range, body);
    }

    private Expression whileExpression() {
        Expression condition = expression();
        Block body = block();
        return new WhileExpression(condition, body);
    }

    private Expression blocklessExpression() {
        if (match(TokenType.RETURN)) {
            return new ReturnExpression(expression());
        } else if (match(TokenType.DEBUG)) {
            return new DebugExpression(expression());
        } else {
            return attribution();
        }
    }

    private Expression attribution() {
        var target = range();
        if (match(TokenType.EQUAL, TokenType.PERCENT_EQUAL, TokenType.PLUS_EQUAL, TokenType.MINUS_EQUAL,
                TokenType.SLASH_EQUAL, TokenType.STAR_EQUAL, TokenType.HAT_EQUAL)) {
            Token operator = previous();
            Expression value = attribution();
            return new BinaryExpression(target, operator, value);
        } else {
            return target;
        }
    }

    private Expression range() {
        Expression start = logicOr();
        if (match(TokenType.DOT_DOT)) {
            Expression end = logicOr();
            return new RangeExpression(start, end);
        } else {
            return start;
        }
    }

    private Expression logicOr() {
        Expression left = logicAnd();
        while (match(TokenType.OR)) {
            Token operator = previous();
            Expression right = logicAnd();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression logicAnd() {
        Expression left = equality();
        while (match(TokenType.AND)) {
            Token operator = previous();
            Expression right = equality();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression equality() {
        Expression left = comparison();
        while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
            Token operator = previous();
            Expression right = comparison();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression comparison() {
        Expression left = term();
        while (match(TokenType.LESSER, TokenType.GREATER, TokenType.LESSER_EQUAL, TokenType.GREATER_EQUAL)) {
            Token operator = previous();
            Expression right = term();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression term() {
        Expression left = factor();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            Expression right = factor();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression factor() {
        Expression left = exponent();
        while (match(TokenType.STAR, TokenType.SLASH)) {
            Token operator = previous();
            Expression right = exponent();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression exponent() {
        Expression left = unary();
        if (match(TokenType.HAT)) {
            Token operator = previous();
            Expression right = exponent();
            return new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression unary() {
        if (match(TokenType.MINUS, TokenType.BANG, TokenType.HASH)) {
            Token operator = previous();
            Expression operand = unary();
            return new UnaryExpression(operator, operand);
        }
        return call();
    }

    private Expression call() {
        Expression expr = primary();
        while (match(TokenType.L_PAREN)) {
            List<Expression> args = argList();
            consume(TokenType.R_PAREN, "Esperado ')'");
            expr = new FunctionCall(expr, args);
        }
        return expr;
    }

    private Expression primary() {
        if (match(TokenType.INTEGER, TokenType.FLOAT, TokenType.TRUE, TokenType.FALSE, TokenType.STRING)) {
            return new Literal(previous());
        } else if (match(TokenType.IDENTIFIER)) {
            return new VariableExpression(previous());
        } else if (match(TokenType.L_PAREN)) {
            Expression expr = expression();
            consume(TokenType.R_PAREN, "Esperado ')'");
            return expr;
        }
        throw error("Expressão inválida encontrada");
    }

    private List<Expression> argList() {
        List<Expression> args = new ArrayList<>();
        if (peek().type() != TokenType.R_PAREN) {
            do {
                args.add(expression());
            } while (match(TokenType.COMMA));
        }
        return args;
    }

    private Type type() {
        if (match(TokenType.IDENTIFIER)) {
            return new Type.Named(previous());
        } else if (match(TokenType.L_BRACKET)) {
            Type elementType = type();
            consume(TokenType.R_BRACKET, "Esperado ']' após o tipo da lista");
            return new Type.List(elementType);
        } else {
            throw error("Esperado tipo");
        }
    }
}
