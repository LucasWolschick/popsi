package popsi.parser;

import java.util.*;
import popsi.CompilerError;
import popsi.CompilerError.ErrorType;
import popsi.FilePosition;
import popsi.Result;
import popsi.lexer.Token;
import popsi.lexer.Token.TokenType;
import popsi.parser.ast.*;
import popsi.parser.ast.Ast.Function;
import popsi.parser.ast.Ast.Parameter;
import popsi.parser.ast.Ast.Program;
import popsi.parser.ast.Ast.Rec;
import popsi.parser.ast.Ast.Rec_field;
import popsi.parser.ast.Expr.Argument;
import popsi.parser.ast.Expr.BinaryExpression;
import popsi.parser.ast.Expr.Block;
import popsi.parser.ast.Expr.DebugExpression;
import popsi.parser.ast.Expr.ForExpression;
import popsi.parser.ast.Expr.FunctionCall;
import popsi.parser.ast.Expr.IfExpression;
import popsi.parser.ast.Expr.ListAccess;
import popsi.parser.ast.Expr.ListExpression;
import popsi.parser.ast.Expr.Literal;
import popsi.parser.ast.Expr.RecAccess;
import popsi.parser.ast.Expr.ReturnExpression;
import popsi.parser.ast.Expr.UnaryExpression;
import popsi.parser.ast.Expr.VariableExpression;
import popsi.parser.ast.Expr.WhileExpression;
import popsi.parser.ast.Stmt.Declaration;
import popsi.parser.ast.Stmt.ExpressionStatement;

public class Parser {
    // Função principal
    public static Result<Program, List<CompilerError>> parse(List<Token> tokens) {
        var parser = new Parser(tokens);

        List<Function> functions = new ArrayList<>();
        List<Rec> records = new ArrayList<>();
        while (!parser.atEoF()) {
            try {
                if (parser.match(TokenType.REC)) {
                    var rec = parser.rec();
                    records.add(rec);
                } else {
                    var func = parser.function();
                    functions.add(func);
                }
            } catch (ParseError e) {
                parser.recover_function();
            }
        }

        if (!parser.errors.isEmpty()) {
            return new Result.Error<>(parser.errors);
        } else {
            return new Result.Success<>(new Program(functions, records));
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

    private Token peekNext() {
        if (current + 1 >= tokens.size())
            return tokens.get(tokens.size() - 1); // eof
        return tokens.get(current + 1);
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
        throw error(errorMessage + " (encontrado: " + peek().lexeme() + ")");
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

    private void recover_function() {
        next();
        while (!atEoF()) {
            if (peek().type() == TokenType.FN) {
                break;
            }

            next();
        }
    }

    private void recover_stmt() {
        next();
        while (!atEoF()) {
            if (previous().type() == TokenType.SEMICOLON) {
                break;
            }

            switch (peek().type()) {
                case TokenType.LET, TokenType.IF, TokenType.WHILE, TokenType.FOR, TokenType.RETURN,
                        TokenType.DEBUG:
                    return;
                default:
                    next();
            }
        }
    }

    // Gramática
    private Function function() {
        consume(TokenType.FN, "Esperado 'fn' no início da declaração de função");
        Token name = consume(TokenType.IDENTIFIER, "Esperado nome da função após 'fn'");
        consume(TokenType.L_PAREN, "Esperado '(' após o nome da função");
        List<Parameter> parameters = parameters();
        consume(TokenType.R_PAREN, "Esperado ')' após os parâmetros");

        Optional<TypeAst> returnType = Optional.empty();
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

    private Rec rec() {
        Token name = consume(TokenType.IDENTIFIER, "Esperado nome do record após 'rec'");
        consume(TokenType.L_CURLY, "Esperado '{' após o nome do record");
        List<Rec_field> fields = new ArrayList<>();

        while (peek().type() != TokenType.R_CURLY) {
            fields.add(rec_field());
        }
        consume(TokenType.R_CURLY, "Esperado '}' para fechar o record");

        return new Rec(name, fields);
    }

    // variável de controle para possibilitar o último ';' opcional
    // atualizada apenas nos statements
    // lida apenas no block
    // indica se o último statement consumiu um ';'
    private boolean ateSemi = false;

    private Block block() {
        var open = consume(TokenType.L_CURLY, "Esperado '{' no início de um bloco de código");
        List<Stmt> stmts = new ArrayList<>();
        while (peek().type() != TokenType.R_CURLY) {
            try {
                stmts.add(statement());
            } catch (Exception e) {
                recover_stmt();
                System.out.println("Recuperado, em: " + peek().lexeme());
            }
        }
        consume(TokenType.R_CURLY, "Esperado '}' para fechar o bloco de código aberto na linha " + open.where().line());

        if (ateSemi || stmts.isEmpty()) {
            return new Block(open.where(), stmts, Optional.empty());
        } else {
            return new Block(open.where(), stmts, Optional.of(stmts.removeLast()));
        }
    }

    private Rec_field rec_field() {
        consume(TokenType.LET, "Esperado 'let' para declarar um campo do record");
        Token name = consume(TokenType.IDENTIFIER, "Esperado nome do campo do record");
        consume(TokenType.COLON, "Esperado ':' após o nome do campo do record");
        var type = type();
        consume(TokenType.SEMICOLON, "Esperado ';' após a declaração do campo do record");
        return new Rec_field(name, type);
    }

    private Stmt statement() {
        ateSemi = false;
        if (match(TokenType.LET)) {
            return declaration();
        } else {
            return exprStmt();
        }
    }

    private Declaration declaration() {
        Token name = consume(TokenType.IDENTIFIER, "Esperado nome da variável após 'let'");
        consume(TokenType.COLON, "Esperado ':' após o nome da variável");
        var type = type();
        if (match(TokenType.EQUAL)) {
            Expr value = expression();
            consume(TokenType.SEMICOLON, "Esperado ';' após a declaração da variável");
            ateSemi = true;
            return new Declaration(name, type, Optional.of(value));
        } else {
            consume(TokenType.SEMICOLON, "Esperado ';' após a declaração da variável");
            ateSemi = true;
            return new Declaration(name, type, Optional.empty());
        }
    }

    private Stmt exprStmt() {
        return switch (peek().type()) {
            case TokenType.IF, TokenType.WHILE, TokenType.FOR, TokenType.L_CURLY -> {
                var block = blockExpression();
                ateSemi = match(TokenType.SEMICOLON);
                yield new ExpressionStatement(block);
            }
            default -> {
                var expr = blocklessExpression();
                if (peek().type() != TokenType.R_CURLY) {
                    // ; é obrigatório
                    consume(TokenType.SEMICOLON, "Esperado ';' após a expressão");
                    ateSemi = true;
                } else {
                    // ; é opcional
                    ateSemi = match(TokenType.SEMICOLON);
                }
                yield new ExpressionStatement(expr);
            }
        };
    }

    private Expr expression() {
        return switch (peek().type()) {
            case TokenType.IF, TokenType.WHILE, TokenType.FOR, TokenType.L_CURLY -> blockExpression();
            default -> blocklessExpression();
        };
    }

    private Expr blockExpression() {
        if (match(TokenType.IF)) {
            return ifExpression();
        } else if (peek().type() == TokenType.WHILE || peek().type() == TokenType.FOR) {
            return loop();
        } else {
            return block();
        }
    }

    private Expr ifExpression() {
        Expr condition = expression();
        Block thenBranch = block();
        Optional<Expr> elseBranch = Optional.empty();
        if (match(TokenType.ELSE)) {
            if (match(TokenType.IF))
                elseBranch = Optional.of(ifExpression());
            else
                elseBranch = Optional.of(block());

        }
        return new IfExpression(condition, thenBranch, elseBranch);
    }

    private Expr loop() {
        if (match(TokenType.WHILE)) {
            return whileExpression();
        } else if (match(TokenType.FOR)) {
            return forExpression();
        } else {
            // unreachable
            throw new IllegalArgumentException();
        }
    }

    private Expr forExpression() {
        Token variable = consume(TokenType.IDENTIFIER, "Esperado nome da variável após 'for'");
        consume(TokenType.COLON, "Esperado ':' após o nome da variável no loop 'for'");
        var type = type();
        consume(TokenType.IN, "Esperado 'in' para indicar o intervalo do loop 'for'");
        Expr range = expression();
        Block body = block();
        return new ForExpression(variable, type, range, body);
    }

    private Expr whileExpression() {
        Expr condition = expression();
        Block body = block();
        return new WhileExpression(condition, body);
    }

    private Expr blocklessExpression() {
        if (match(TokenType.RETURN)) {
            return new ReturnExpression(expression());
        } else if (match(TokenType.DEBUG)) {
            return new DebugExpression(expression());
        } else {
            return attribution();
        }
    }

    private Expr attribution() {
        var target = range();
        if (match(TokenType.EQUAL, TokenType.PERCENT_EQUAL, TokenType.PLUS_EQUAL, TokenType.MINUS_EQUAL,
                TokenType.SLASH_EQUAL, TokenType.STAR_EQUAL, TokenType.HAT_EQUAL)) {
            Token operator = previous();
            Expr value = attribution();
            return new BinaryExpression(target, operator, value);
        } else {
            return target;
        }
    }

    private Expr range() {
        Expr start = logicOr();
        if (match(TokenType.DOT_DOT)) {
            Expr end = logicOr();
            return new BinaryExpression(start, previous(), end);
        } else {
            return start;
        }
    }

    private Expr logicOr() {
        Expr left = logicAnd();
        while (match(TokenType.OR)) {
            Token operator = previous();
            Expr right = logicAnd();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expr logicAnd() {
        Expr left = equality();
        while (match(TokenType.AND)) {
            Token operator = previous();
            Expr right = equality();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expr equality() {
        Expr left = comparison();
        while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expr comparison() {
        Expr left = term();
        while (match(TokenType.LESSER, TokenType.GREATER, TokenType.LESSER_EQUAL, TokenType.GREATER_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expr term() {
        Expr left = factor();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            Expr right = factor();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expr factor() {
        Expr left = exponent();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token operator = previous();
            Expr right = exponent();
            left = new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expr exponent() {
        Expr left = unary();
        if (match(TokenType.HAT)) {
            Token operator = previous();
            Expr right = exponent();
            return new BinaryExpression(left, operator, right);
        }
        return left;
    }

    private Expr unary() {
        if (match(TokenType.MINUS, TokenType.BANG, TokenType.HASH)) {
            Token operator = previous();
            Expr operand = unary();
            return new UnaryExpression(operator, operand);
        }
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        while (match(TokenType.L_PAREN, TokenType.L_BRACKET, TokenType.DOT)) {
            if (previous().type() == TokenType.L_BRACKET) {
                Expr place = expression();
                consume(TokenType.R_BRACKET, "Esperado ']' após o índice");
                expr = new ListAccess(expr, place);
            } else if (previous().type() == TokenType.L_PAREN) {
                List<Argument> args = argList();
                consume(TokenType.R_PAREN, "Esperado ')'");
                expr = new FunctionCall(expr, args);
            } else if (previous().type() == TokenType.DOT) {
                var place = consume(TokenType.IDENTIFIER, "Esperado nome do campo");
                expr = new RecAccess(expr, place);
            }
        }
        return expr;
    }

    private Expr primary() {
        if (match(TokenType.INTEGER, TokenType.FLOAT, TokenType.TRUE, TokenType.FALSE, TokenType.STRING)) {
            return new Literal(previous());
        } else if (match(TokenType.IDENTIFIER)) {
            return new VariableExpression(previous());
        } else if (match(TokenType.L_BRACKET)) {
            FilePosition position = previous().where();
            List<Expr> elements = listItems();
            consume(TokenType.R_BRACKET, "Esperado ']' após a lista");
            return new ListExpression(position, elements);
        } else if (match(TokenType.L_PAREN)) {
            Expr expr = expression();
            consume(TokenType.R_PAREN, "Esperado ')'");
            return expr;
        }
        throw error("Esperada expressão, encontrado: " + peek().lexeme());
    }

    private List<Argument> argList() {
        List<Argument> args = new ArrayList<>();
        if (peek().type() != TokenType.R_PAREN) {
            do {
                args.add(argument());
            } while (match(TokenType.COMMA));
        }
        return args;
    }

    private Argument argument() {
        if (peek().type() == TokenType.IDENTIFIER && peekNext().type() == TokenType.COLON) {
            // labeled argument
            var label = consume(TokenType.IDENTIFIER, "Esperado identificador no rótulo");
            consume(TokenType.COLON, "Esperado : após o rótulo");
            // expression
            var expr = expression();
            return new Argument(Optional.of(label), expr);
        } else {
            var expr = expression();
            return new Argument(Optional.empty(), expr);
        }
    }

    private List<Expr> listItems() {
        List<Expr> args = new ArrayList<>();
        if (peek().type() != TokenType.R_BRACKET) {
            do {
                args.add(expression());
            } while (match(TokenType.COMMA));
        }
        return args;
    }

    private TypeAst type() {
        if (match(TokenType.IDENTIFIER)) {
            return new TypeAst.Named(previous());
        } else if (match(TokenType.L_BRACKET)) {
            TypeAst elementType = type();
            consume(TokenType.R_BRACKET, "Esperado ']' após o tipo da lista");
            return new TypeAst.List(elementType);
        } else {
            throw error("Esperado tipo");
        }
    }
}
