package popsi;

import java.util.*;

import popsi.ast.*;
import popsi.ast.Ast.Block;
import popsi.ast.Ast.Function;
import popsi.ast.Ast.Parameter;
import popsi.ast.Ast.Program;

import static popsi.ast.Ast.*;
import static popsi.ast.Statement.*;
import static popsi.ast.Expression.*;
import popsi.ast.Expression.*;
import popsi.ast.Statement.*;
import popsi.CompilerError.ErrorType;
import popsi.Token.TokenType;

public class Parser {
    // Função principal
    public static Result<Program, List<CompilerError>> parse(List<Token> tokens) {
        var parser = new Parser(tokens);

        List<Function> functions = new ArrayList<>();
        while (!parser.atEoF()) {
            try {
                var func = parser.functionDeclaration();
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

    private boolean match(TokenType type) {
        if (peek().type() == type) {
            next();
            return true;
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
    private Function functionDeclaration() {
        consume(TokenType.FN, "Esperado 'fn' no início da declaração de função");
        Token name = consume(TokenType.IDENTIFIER, "Esperado nome da função após 'fn'");
        consume(TokenType.L_PAREN, "Esperado '(' após o nome da função");
        List<Parameter> parameters = parameterList();
        consume(TokenType.R_PAREN, "Esperado ')' após os parâmetros");

        Token returnType = null;
        if (match(TokenType.ARROW)) {
            returnType = consume(TokenType.IDENTIFIER, "Esperado tipo de retorno após '->'");
        }

        Block body = bloco();
        return new Function(name, parameters, returnType, body);
    }

    private List<Parameter> parameterList() {
        List<Parameter> parameters = new ArrayList<>();
        if (peek().type() != TokenType.R_PAREN) {
            do {
                Token name = consume(TokenType.IDENTIFIER, "Esperado nome do parâmetro");
                consume(TokenType.COLON, "Esperado ':' após o nome do parâmetro");
                Token type = consume(TokenType.IDENTIFIER, "Esperado tipo do parâmetro");
                parameters.add(new Parameter(name, type));
            } while (match(TokenType.COMMA));
        }
        return parameters;
    }

    private Block bloco() {
        consume(TokenType.L_CURLY, "Esperado '{' no início de um bloco de código");
        List<Statement> statements = new ArrayList<>();
        while (!match(TokenType.R_CURLY) && !atEoF()) {
            statements.add(stmt());
        }
        if (previous().type() != TokenType.R_CURLY) {
            throw error("Fim do arquivo encontrado enquanto esperava '}' para fechar o bloco");
        }
        return new Block(statements);
    }

    private Statement stmt() {
        if (match(TokenType.LET)) {
            return varDeclaration();
        } else if (match(TokenType.FOR)) {
            return forStmt();
        } else if (match(TokenType.WHILE)) {
            return whileStmt();
        } else if (match(TokenType.IF)) {
            return ifStmt();
        } else if (match(TokenType.RETURN)) {
            return returnStmt();
        } else if (match(TokenType.DEBUG)) {
            return debug();
        } else {
            return new ExpressionStatement(expressao());
        }
    }

    private Declaration varDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "Esperado nome da variável após 'let'");
        consume(TokenType.COLON, "Esperado ':' após o nome da variável");
        Token type = consume(TokenType.IDENTIFIER, "Esperado tipo da variável");
        consume(TokenType.EQUAL, "Esperado '=' após ':' e tipo da variável");
        Expression value = expressao();
        consume(TokenType.SEMICOLON, "Esperado ';' após a declaração da variável");
        return new Declaration(name, type, value);
    }

    private Statement forStmt() {
        Token variable = consume(TokenType.IDENTIFIER, "Esperado nome da variável após 'for'");
        consume(TokenType.COLON, "Esperado ':' após o nome da variável no loop 'for'");
        Token type = consume(TokenType.IDENTIFIER, "Esperado tipo da variável no loop 'for'");
        consume(TokenType.IN, "Esperado 'in' para indicar o intervalo do loop 'for'");
        Expression range = expressao();
        Block body = bloco();
        return new ForStatement(variable, type, range, body);
    }

    private Statement ifStmt() {
        Expression condition = expressao();
        Block thenBranch = bloco();
        Block elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = bloco();
        }
        return new IfStatement(condition, thenBranch, elseBranch);
    }

    private Statement whileStmt() {
        Expression condition = expressao();
        Block body = bloco();
        return new WhileStatement(condition, body);
    }

    private Statement returnStmt() {
        Expression value = expressao();
        consume(TokenType.SEMICOLON, "Esperado ';' após 'return'");
        return new ReturnStatement(value);
    }

    private Statement debug() {
        Expression value = expressao();
        consume(TokenType.SEMICOLON, "Esperado ';' após 'debug'");
        return new DebugStatement(value);
    }

    private Expression expressao() {
        return logicOr();
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
        while (match(TokenType.EQUAL_EQUAL) || match(TokenType.BANG_EQUAL)) {
            Token operator = previous();
            Expression right = comparison();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression comparison() {
        Expression left = termo();
        while (match(TokenType.LESSER) || match(TokenType.GREATER) ||
                match(TokenType.LESSER_EQUAL) || match(TokenType.GREATER_EQUAL)) {
            Token operator = previous();
            Expression right = termo();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression termo() {
        Expression left = fator();
        while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
            Token operator = previous();
            Expression right = fator();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression fator() {
        Expression left = unary();
        while (match(TokenType.STAR) || match(TokenType.SLASH)) {
            Token operator = previous();
            Expression right = unary();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expression unary() {
        if (match(TokenType.MINUS) || match(TokenType.BANG)) {
            Token operator = previous();
            Expression operand = unary();
            return new UnaryExpression(operator, operand);
        }
        return chamada();
    }

    private Expression chamada() {
        Expression expr = primary();
        while (match(TokenType.L_PAREN)) {
            List<Expression> args = argumentos();
            consume(TokenType.R_PAREN, "Esperado ')'");
            expr = new FunctionCall(previous(), args);
        }
        return expr;
    }

    private Expression primary() {
        if (match(TokenType.INTEGER) || match(TokenType.FLOAT) || match(TokenType.STRING)) {
            return new Literal(previous());
        } else if (match(TokenType.IDENTIFIER)) {
            return new VariableExpression(previous());
        } else if (match(TokenType.L_PAREN)) {
            Expression expr = expressao();
            consume(TokenType.R_PAREN, "Esperado ')'");
            return expr;
        }
        throw error("Expressão inválida encontrada");
    }

    private List<Expression> argumentos() {
        List<Expression> args = new ArrayList<>();
        if (!match(TokenType.R_PAREN)) {
            do {
                args.add(expressao());
            } while (match(TokenType.COMMA));
        }
        return args;
    }
}
