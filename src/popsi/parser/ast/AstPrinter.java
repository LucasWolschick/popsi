package popsi.parser.ast;

import java.util.List;
import java.util.Optional;

import popsi.lexer.Token;
import popsi.parser.ast.Ast.Function;
import popsi.parser.ast.Ast.Parameter;
import popsi.parser.ast.Ast.Program;
import popsi.parser.ast.Ast.Rec;
import popsi.parser.ast.Ast.Rec_field;

public class AstPrinter {
    private AstPrinter() {
    }

    public static String print(Object object) {
        var str = new AstPrinter().visit(object);
        return str.replaceAll("\\s+", " ").trim();
    }

    private String visit(Object object) {
        return switch (object) {
            case Ast ast -> visitAst(ast);
            case Expression expression -> visitExpression(expression);
            case Statement statement -> visitStatement(statement);
            case Type type -> visitType(type);
            default -> throw new IllegalArgumentException("Unexpected object: " + object);
        };
    }

    private String visitAst(Ast ast) {
        return switch (ast) {
            case Program program -> parens("program", program.records(), program.functions());
            case Rec rec -> parens("rec", parens(rec.name().lexeme(), rec.fields()));
            case Function function -> {
                yield parens("fn", parens(function.name().lexeme(), function.parameters()), "->", function.returnType(),
                        function.body());
            }
            case Parameter parameter -> parens(parameter.name().lexeme(), ":", parameter.type());
            case Rec_field field -> parens("let", parens(field.name().lexeme(), field.type()));
        };
    }

    private String visitExpression(Expression expression) {
        return switch (expression) {
            case Expression.Literal literal -> literal.value().lexeme();
            case Expression.VariableExpression variable -> variable.name().lexeme();
            case Expression.BinaryExpression binary ->
                parens(binary.operator().lexeme(), binary.left(), binary.right());
            case Expression.UnaryExpression unary -> parens(unary.operator().lexeme(), unary.operand());
            case Expression.FunctionCall call -> parens(visit(call.target()), call.arguments());
            case Expression.ListAccess access -> parens("[]", access.target(), access.place());
            case Expression.RangeExpression range -> visit(range.start()) + ".." + visit(range.end());
            case Expression.ForExpression loop ->
                parens("for", loop.variable(), loop.type(), loop.range(), loop.body());
            case Expression.IfExpression ifExpr ->
                parens("if", ifExpr.condition(), ifExpr.thenBranch(), ifExpr.elseBranch());
            case Expression.WhileExpression whileExpr -> parens("while", whileExpr.condition(), whileExpr.body());
            case Expression.ReturnExpression returnExpr -> parens("return", returnExpr.value());
            case Expression.DebugExpression debugExpr -> parens("debug", debugExpr.value());
            case Expression.Block block -> parens("block", block.statements(), block.lastStatement());
            case Expression.ListExpression list -> parens("list", list.elements());
            case Expression.Argument arg ->
                arg.label().isPresent()
                        ? arg.label().get().lexeme() + ": " + print(arg.value())
                        : print(arg.value());
            case Expression.RecAccess rec -> parens(".", rec.target(), rec.place());
        };
    }

    private String visitStatement(Statement statement) {
        return switch (statement) {
            case Statement.ExpressionStatement expr -> visitExpression(expr.expression());
            case Statement.Declaration let ->
                parens("let", parens(parens(let.name().lexeme(), let.type())), let.value());
        };
    }

    private String visitType(Type type) {
        return switch (type) {
            case Type.Named named -> named.name().lexeme();
            case Type.List list -> brackets(visitType(list.elementType()));
        };
    }

    // Helper
    private String parens(String name, Object... rest) {
        var builder = new StringBuilder();
        builder.append("(").append(name);
        stringify(builder, rest);
        builder.append(")");
        return builder.toString();
    }

    private String brackets(String name, Object... rest) {
        var builder = new StringBuilder();
        builder.append("[").append(name);
        stringify(builder, rest);
        builder.append("]");
        return builder.toString();
    }

    private String parens(Object... rest) {
        var builder = new StringBuilder();
        builder.append("(");
        stringify(builder, rest);
        builder.append(")");
        return builder.toString();
    }

    private void stringify(StringBuilder builder, Object... rest) {
        for (var obj : rest) {
            builder.append(" ");
            switch (obj) {
                case List<?> list -> stringify(builder, list.toArray());
                case Optional<?> optional -> optional.ifPresent(
                        value -> stringify(builder, value));
                case String string -> builder.append(string);
                case Token token -> builder.append(token.lexeme());
                default -> builder.append(visit(obj));
            }
        }
    }
}
