package popsi.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import popsi.CompilerError;
import popsi.FilePosition;
import popsi.Result;
import popsi.CompilerError.ErrorType;
import popsi.analysis.typed_ast.TypedExpr;
import popsi.analysis.typed_ast.TypedAst;
import popsi.analysis.typed_ast.TypedStmt;
import popsi.lexer.Token;
import popsi.lexer.Token.TokenType;
import popsi.parser.ast.Expr;
import popsi.parser.ast.Ast;
import popsi.parser.ast.Stmt;
import popsi.parser.ast.TypeAst;

public class Analyser {
    public static Result<TypedAst.Program, List<CompilerError>> analyse(Ast.Program program) {
        var analyser = new Analyser();
        var typedProgram = analyser.program(program);
        if (analyser.errors.isEmpty()) {
            return new Result.Success<>(typedProgram);
        } else {
            return new Result.Error<>(analyser.errors);
        }
    }

    private List<CompilerError> errors;
    private SymbolTable table;
    private Environment environment;

    private Analyser() {
        errors = new ArrayList<>();
        table = new SymbolTable();
        environment = new Environment();
    }

    private TypedAst.Program program(Ast.Program program) {
        var functions = new ArrayList<TypedAst.Function>();
        var records = new ArrayList<TypedAst.Rec>();

        for (var function : program.functions()) {
            functions.add(function(function));
        }

        for (var record : program.records()) {
            records.add(rec(record));
        }

        return new TypedAst.Program(functions, records, table);
    }

    private TypedExpr.Block block(Expr.Block block) {
        var typedStatements = new ArrayList<TypedStmt>();
        for (var statement : block.statements()) {
            typedStatements.add(statement(statement));
        }

        var lastTypedStatement = block.lastStatement().map(this::statement);
        Type blockType = lastTypedStatement.map(TypedStmt::type).orElse(Type.UNIT);

        return new TypedExpr.Block(typedStatements, lastTypedStatement, blockType);
    }

    private TypedAst.Function function(Ast.Function function) {
        var parameters = new ArrayList<TypedAst.Parameter>();
        for (var param : function.parameters()) {
            var type = resolveType(param.type());
            parameters.add(new TypedAst.Parameter(param.name(), param.type(), type));
        }

        var returnType = function.returnType();

        environment = new Environment(environment);
        block(function.body());
        environment = environment.enclosing().orElse(null);

        return new TypedAst.Function(function.name(), parameters, returnType, function.body(),
                returnType.map(this::resolveType).orElse(Type.UNIT));
    }

    private TypedAst.Rec rec(Ast.Rec rec) {
        var fields = new ArrayList<TypedAst.RecField>();

        for (var field : rec.fields()) {
            var type = resolveType(field.type());
            fields.add(new TypedAst.RecField(field.name(), field.type(), type));
        }

        return new TypedAst.Rec(rec.name(), fields);
    }

    private TypedStmt statement(Stmt stmt) {
        switch (stmt) {
            case Stmt.Declaration(Token name, TypeAst typeAst, Optional<Expr> value): {
                var resolvedType = resolveType(typeAst);
                var typedValue = value.map(this::expression);

                if (typedValue.isPresent() && !compatibleTypes(typedValue.get().type(), resolvedType)) {
                    error(name, "Tipo incompatível para a declaração da variável");
                }

                environment.put(name.lexeme(), new Environment.EnvEntry.Local());
                return new TypedStmt.Declaration(name, typeAst, typedValue, resolvedType);
            }

            case Stmt.ExpressionStatement(Expr expression): {
                var typedExpr = expression(expression);
                return new TypedStmt.ExpressionStatement(typedExpr, typedExpr.type());
            }

            default:
                throw new IllegalStateException("Declaração inválida: " + stmt);
        }
    }

    private TypedExpr expression(Expr expr) {
        switch (expr) {
            case Expr.Literal(Token value): {
                return switch (value.type()) {
                    case TokenType.INTEGER -> new TypedExpr.Literal(value, Type.I_LITERAL);
                    case TokenType.FLOAT -> new TypedExpr.Literal(value, Type.F_LITERAL);
                    case TokenType.STRING -> new TypedExpr.Literal(value, Type.STR);
                    case TokenType.CHAR -> new TypedExpr.Literal(value, Type.CHAR);
                    default -> throw new RuntimeException("Unexpected token type: " + value.type());
                };
            }

            case Expr.VariableExpression(Token name): {
                var entry = environment.get(name.lexeme());
                if (entry.isEmpty()) {
                    error(name, "Uso de variável não declarada: '" + name.lexeme() + "'");
                    return new TypedExpr.VariableExpression(name, Type.INVALID);
                }
                return new TypedExpr.VariableExpression(name,
                        entry.get() instanceof Environment.EnvEntry.Local ? Type.UNKNOWN
                                : ((Environment.EnvEntry.Function) entry.get()).type());

            }

            case Expr.ListExpression(FilePosition position, List<Expr> elements): {
                if (elements.isEmpty()) {
                    return new TypedExpr.ListExpression(position, List.of(), Type.UNKNOWN);
                }

                var first = expression(elements.get(0));
                var elementType = first.type();
                var typedElements = new ArrayList<TypedExpr>();
                for (var element : elements) {
                    var typedElement = expression(element);
                    if (!compatibleTypes(typedElement.type(), elementType)) {
                        error(position, "Todos os elementos da lista devem possuir o mesmo tipo.");
                    }
                    typedElements.add(typedElement);
                }

                var listType = new Type.Named("[]", List.of(elementType));
                return new TypedExpr.ListExpression(position, typedElements, listType);
            }

            case Expr.BinaryExpression(Expr left, Token operator, Expr right): {
                var leftExpr = expression(left);
                var rightExpr = expression(right);

                if (!compatibleTypes(leftExpr.type(), rightExpr.type())) {
                    error(operator.where(), "Tipos incompatíveis para operação binária.");
                }

                Type resultType = switch (operator.type()) {
                    case TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH -> leftExpr.type();
                    case TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL, TokenType.LESSER, TokenType.LESSER_EQUAL,
                            TokenType.GREATER, TokenType.GREATER_EQUAL ->
                        Type.BOOLEAN;
                    default -> throw new RuntimeException("Operação não suportada: " + operator.type());
                };
                return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, resultType);
            }

            case Expr.UnaryExpression(Token operator, Expr operand): {
                var operandExpr = expression(operand);
                return switch (operator.type()) {
                    case TokenType.BANG -> new TypedExpr.UnaryExpression(operator, operandExpr, Type.BOOLEAN);
                    case TokenType.MINUS -> new TypedExpr.UnaryExpression(operator, operandExpr, operandExpr.type());
                    default -> throw new RuntimeException("Operação unária não suportada: " + operator.type());
                };
            }

            case Expr.FunctionCall(Expr target, List<Expr.Argument> arguments): {
                var targetExpr = expression(target);
                var typedArgs = new ArrayList<TypedExpr.Argument>();
                for (var arg : arguments) {
                    var typedValue = expression(arg.value());
                    typedArgs.add(new TypedExpr.Argument(arg.label(), typedValue, typedValue.type()));
                }
                return new TypedExpr.FunctionCall(targetExpr, typedArgs, targetExpr.type());
            }

            case Expr.IfExpression(Expr condition, Expr.Block thenBranch, Optional<Expr.Block> elseBranch): {
                var conditionExpr = expression(condition);
                if (!compatibleTypes(conditionExpr.type(), Type.BOOLEAN)) {
                    error(((Expr.Literal) condition).value(), "Condição do 'if' deve ser booleana.");

                }

                var thenExpr = (TypedExpr.Block) expression(thenBranch);
                var elseExpr = elseBranch.map(this::expression).map(e -> (TypedExpr.Block) e).orElse(null);

                return new TypedExpr.IfExpression(conditionExpr, thenExpr, Optional.ofNullable(elseExpr),
                        thenExpr.type());
            }

            case Expr.Block(List<Stmt> statements, Optional<Stmt> lastStatement): {
                var typedStatements = new ArrayList<TypedStmt>();
                for (var statement : statements) {
                    typedStatements.add(statement(statement));
                }

                var lastTypedStatement = lastStatement.map(this::statement);
                Type blockType = lastTypedStatement.map(TypedStmt::type).orElse(Type.UNIT);
                return new TypedExpr.Block(typedStatements, lastTypedStatement, blockType);
            }

            default:
                throw new RuntimeException("Expressão não implementada: " + expr);
        }
    }

    private Type resolveType(TypeAst typeAst) {
        if (typeAst instanceof TypeAst.Named named) {
            return new Type.Named(named.name().lexeme(), List.of());
        } else if (typeAst instanceof TypeAst.List list) {
            return new Type.Named("[]", List.of(resolveType(list.elementType())));
        }
        throw new RuntimeException("Tipo não suportado: " + typeAst);
    }

    private boolean compatibleTypes(Type type1, Type type2) {
        // Exemplo de compatibilidade avançada
        if (type1.equals(Type.I_LITERAL) && (type2.equals(Type.I_LITERAL) || type2.equals(Type.I_LITERAL))) {
            return true;
        }
        return type1.equals(type2);
    }

    private void error(Token token, String message) {
        errors.add(new CompilerError(ErrorType.SEMANTIC, message, token.where()));
    }

    private void error(FilePosition position, String message) {
        errors.add(new CompilerError(ErrorType.SEMANTIC, message, position));
    }
}
