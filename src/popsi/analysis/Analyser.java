package popsi.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import popsi.CompilerError;
import popsi.FilePosition;
import popsi.Result;
import popsi.CompilerError.ErrorType;
import popsi.analysis.typed_ast.TypedExpr;
import popsi.analysis.SymbolTable.Id;
import popsi.analysis.SymbolTable.TypeInfo;
import popsi.analysis.Type.TypeAlgebra;
import popsi.analysis.SymbolTable.LocalInfo;
import popsi.analysis.SymbolTable.RecordInfo;
import popsi.analysis.Cfa.CfaResult;
import popsi.analysis.Environment.EnvEntry;
import popsi.analysis.Environment.TypeEnvEntry;
import popsi.analysis.SymbolTable.FunctionInfo;
import popsi.analysis.typed_ast.TypedAst;
import popsi.analysis.typed_ast.TypedStmt;
import popsi.lexer.Token;
import popsi.lexer.Token.TokenType;
import popsi.parser.ast.Expr;
import popsi.parser.ast.Ast;
import popsi.parser.ast.AstPrinter;
import popsi.parser.ast.Stmt;
import popsi.parser.ast.TypeAst;

public class Analyser {
    public static Result<TypedAst.Program, List<CompilerError>> analyse(Ast.Program program) {
        var analyser = new Analyser();
        var typedProgram = analyser.program(program);

        if (analyser.errors.isEmpty()) {
            analyser.table.printSymbolTable();
            return new Result.Success<>(typedProgram);
        } else {
            return new Result.Error<>(analyser.errors);
        }
    }

    private List<CompilerError> errors;
    private SymbolTable table;
    private Environment environment;
    private Optional<Id<FunctionInfo>> currentFunction;

    private Analyser() {
        errors = new ArrayList<>();
        table = new SymbolTable();
        environment = new Environment();
        currentFunction = Optional.empty();

        // Registrar tipos básicos
        registerPrelude();
    }

    private void registerPrelude() {
        // tipos que são inseridos no environment
        for (var type : List.of(
                Type.U8, Type.U16, Type.U32, Type.U64,
                Type.I8, Type.I16, Type.I32, Type.I64,
                Type.F32, Type.F64,
                Type.STR,
                Type.CHAR,
                Type.UNIT,
                Type.BOOLEAN)) {
            var id = table.types().insert(new TypeInfo(type));
            environment.putType(type.name(), new TypeEnvEntry.Type(id));
        }

        // tipos que não são inseridos no environment
        for (var type : List.of(Type.ANY, Type.INVALID, Type.I_LITERAL, Type.F_LITERAL, Type.NUMERIC, Type.NOTHING)) {
            table.types().insert(new TypeInfo(type));
        }

        // registra conversões numéricas
        for (var type : List.of(
                Type.U8, Type.U16, Type.U32, Type.U64,
                Type.I8, Type.I16, Type.I32, Type.I64,
                Type.F32, Type.F64)) {
            var constructorType = new Type.Function(List.of(Type.NUMERIC), type);
            var constructorTypeId = table.typeId(constructorType);
            var constructorInfo = new FunctionInfo(type.name(), constructorTypeId);
            environment.put(type.name(), new EnvEntry.Function(table.functions().insert(constructorInfo)));
        }
    }

    private TypedAst.Program program(Ast.Program program) {
        var functions = new ArrayList<TypedAst.Function>();
        var records = new ArrayList<TypedAst.Rec>();

        for (var record : program.records()) {
            records.add(rec(record));
        }

        for (var function : program.functions()) {
            functions.add(function(function));
        }

        // table.printSymbolTable();

        return new TypedAst.Program(functions, records, table);
    }

    private Id<TypeInfo> statementType(TypedStmt stmt) {
        switch (stmt) {
            case TypedStmt.ExpressionStatement(TypedExpr _, Id<TypeInfo> type):
                return type;
            case TypedStmt.Declaration decl:
                return table.locals().get(decl.local()).get().type();
        }
    }

    private TypedExpr.Block block(Expr.Block block) {

        // Um bloco abre um novo escopo
        environment = new Environment(environment);

        // Lista para armazenar as instruções tipadas
        var typedStatements = new ArrayList<TypedStmt>();

        // Analisar cada instrução
        for (var statement : block.statements()) {
            typedStatements.add(statement(statement));
        }

        // Analisar a última instrução, se existir
        var lastTypedStatement = block.lastStatement().map(this::statement);

        // Determinar o tipo do bloco
        var blockType = lastTypedStatement.map(s -> statementType(s)).orElse(table.typeId(Type.UNIT));

        if (typedStatements.isEmpty() && lastTypedStatement.isEmpty()) {
            // TODO: queremos isso msm?
            error(block.start(),
                    "Bloco vazio detectado. Certifique-se de que seu código contém pelo menos uma instrução.");
        }

        // Fecha environment
        environment = environment.enclosing().get();

        // Retornar o bloco tipado
        return new TypedExpr.Block(block.start(), typedStatements, lastTypedStatement, blockType);
    }

    private TypedAst.Function function(Ast.Function function) {
        var parameters = new ArrayList<TypedAst.Parameter>();

        // Resolver os tipos dos parâmetros
        for (var param : function.parameters()) {
            var type = typeAst(param.type());
            parameters.add(new TypedAst.Parameter(param.name(), param.type(), type));
        }

        // Resolver o tipo de retorno
        var returnType = function.returnType().map(this::typeAst).orElse(table.typeId(Type.UNIT));

        // Registrar a função na tabela de símbolos
        var functionType = new Type.Function(
                parameters.stream().map(x -> table.typeDefinition(x.type())).toList(),
                table.typeDefinition(returnType));
        var functionTypeId = table.typeId(functionType);
        var functionInfo = new FunctionInfo(function.name().lexeme(), functionTypeId);
        var functionId = table.functions().insert(functionInfo);

        // Registrar a função no escopo externo
        environment.put(functionInfo.name(), new EnvEntry.Function(functionId));

        // Criar escopo para a função
        environment = new Environment(environment);

        // Seta função como ativa
        currentFunction = Optional.of(functionId);

        // Cria uma variável local para cada parâmetro
        for (var param : parameters) {
            var localInfo = new LocalInfo(param.name().lexeme(), param.type());
            var localId = table.locals().insert(localInfo);
            environment.put(param.name().lexeme(), new EnvEntry.Local(localId));
        }

        // Analisar o corpo da função
        var bodyExpr = block(function.body());

        // Remove função
        currentFunction = Optional.empty();

        // Restaurar o escopo anterior
        environment = environment.enclosing().orElse(null);

        // Verificar se a função tem um retorno
        var branchReturns = new Cfa(table).ensureAllPathsReturnType(bodyExpr, table.typeDefinition(returnType));

        if (branchReturns != CfaResult.RETURNED_TYPE
                && !table.typeDefinition(returnType).equals(table.typeDefinition(bodyExpr.type()))) {
            error(function.name(), "O tipo do corpo da função não é compatível com o tipo de retorno declarado. "
                    + "Esperado: " + table.typeDefinition(returnType) + ", recebido: "
                    + table.typeDefinition(bodyExpr.type()));
        }

        return new TypedAst.Function(function.name(), parameters, function.returnType(), bodyExpr, functionId);
    }

    private TypedAst.Rec rec(Ast.Rec rec) {
        var fields = new ArrayList<TypedAst.RecField>();
        var parameters = new ArrayList<TypedAst.Parameter>();

        for (var field : rec.fields()) {
            var type = typeAst(field.type());
            fields.add(new TypedAst.RecField(field.name(), field.type(), type));
            parameters.add(new TypedAst.Parameter(field.name(), field.type(), type));
        }

        // insere na tabela de símbolos
        var recType = new Type.Record(rec.name().lexeme(), fields.stream().map(f -> f.name().lexeme()).toList(),
                fields.stream().map(f -> table.typeDefinition(f.type())).toList());
        var recTypeId = table.typeId(recType);
        var recInfo = new RecordInfo(rec.name().lexeme(), recTypeId);
        var recInfoId = table.records().insert(recInfo);

        // insere o tipo no environment
        environment.putType(recInfo.name(), new TypeEnvEntry.Record(recInfoId));

        // registra construtores
        var constructorType = new Type.Function(
                parameters.stream().map(p -> table.typeDefinition(p.type())).toList(),
                table.typeDefinition(recTypeId));

        var constructorTypeId = table.typeId(constructorType);
        var constructorInfo = new FunctionInfo(rec.name().lexeme(), constructorTypeId);
        table.functions().insert(constructorInfo);

        environment.put(rec.name().lexeme(), new EnvEntry.Function(table.functions().insert(constructorInfo)));

        return new TypedAst.Rec(rec.name(), fields, recInfoId);
    }

    private TypedStmt statement(Stmt stmt) {

        // 1.1. O tipo de uma declaração é o tipo declarado após ela.
        // 1.1.1. O tipo da declaração e o tipo da expressão nela contida devem ser
        // compatíveis.
        // 1.2. O efeito de uma declaração é adicionar uma variável ao ambiente.
        // 1.2.1. A declaração sai de escopo ao final do bloco em que ela foi declarada.
        // 1.2.2. Caso já exista uma variável com o mesmo nome no ambiente, a existente
        // é ocultada, até que essa variável saia de escopo.
        // 1.3. O valor de uma declaração é o valor da expressão que ela contém, ou o
        // valor Unit, caso a expressão seja vazia.
        switch (stmt) {
            case Stmt.Declaration(Token name, TypeAst typeAst, Optional<Expr> value): {
                var resolvedType = typeAst(typeAst);

                // Analisar a expressão inicial, se houver
                var typedValue = value.map(this::expression);

                // Validar compatibilidade de tipos
                if (typedValue.isPresent()) {
                    if (!compatibleTypes(typedValue.get().type(), resolvedType)) {
                        error(name,
                                "Tipo incompatível para a variável '" + name.lexeme() + "'. Esperado: "
                                        + table.typeDefinition(resolvedType)
                                        + ", recebido: " + table.typeDefinition(typedValue.get().type()));
                    }

                    // Verificar se o valor do literal cabe no tipo da variável
                    if (typedValue.get() instanceof TypedExpr.Literal literal) {
                        if (literal.value().type() == TokenType.INTEGER || literal.value().type() == TokenType.FLOAT) {
                            var literalValue = Long.parseLong(literal.value().lexeme()); // Obter o valor do literal
                            if (!fitsInIntegerType(literalValue, table.typeDefinition(resolvedType))) {
                                error(name,
                                        "Valor do literal '" + literalValue + "' não cabe no tipo '" +
                                                table.typeDefinition(resolvedType) + "'.");
                            }
                        }
                    }
                }

                // Adiciona variável local à tabela
                var localInfo = new LocalInfo(name.lexeme(), resolvedType);
                var localId = table.locals().insert(localInfo);

                // Adiciona variável local ao escopo
                if (environment.get(name.lexeme()).isPresent()) {
                    error(name, "Variável '" + name.lexeme() + "' já foi declarada.");
                } else {
                    environment.put(name.lexeme(), new EnvEntry.Local(localId));
                }

                return new TypedStmt.Declaration(name, typeAst, typedValue, localId);
            }

            // Outros casos permanecem iguais
            case Stmt.ExpressionStatement(Expr expression): {
                var typedExpr = expression(expression);
                return new TypedStmt.ExpressionStatement(typedExpr, typedExpr.type());
            }
        }
    }

    private boolean canConvert(Id<TypeInfo> from, Id<TypeInfo> to) {
        var fromType = table.typeDefinition(from);
        var toType = table.typeDefinition(to);

        if ((TypeAlgebra.isIntegerType(fromType) || TypeAlgebra.isFloatType(fromType)) &&
                (TypeAlgebra.isIntegerType(toType) || TypeAlgebra.isFloatType(toType))) {
            return true; // Qualquer numérico pode ser convertido para outro numérico
        }

        if (toType.equals(Type.STR)) {
            return false;
        }

        return false;
    }

    // private boolean canConvert(Id<TypeInfo> from, Id<TypeInfo> to) {
    // var fromType = table.typeDefinition(from);
    // var toType = table.typeDefinition(to);

    // // Conversão entre tipos inteiros
    // if (isIntegerType(fromType) && isIntegerType(toType)) {
    // if (fromType.equals(Type.I_LITERAL)) {
    // // Literais inteiros podem ser convertidos se couberem
    // return fitsInIntegerType(Long.MAX_VALUE, toType) &&
    // fitsInIntegerType(Long.MIN_VALUE, toType);
    // } else if (fromType.equals(Type.I64) || fromType.equals(Type.U64)) {
    // // Verificar se valores grandes cabem no tipo de destino
    // return fitsInIntegerType(Long.MAX_VALUE, toType) &&
    // fitsInIntegerType(Long.MIN_VALUE, toType);
    // }
    // return true; // Outros inteiros podem ser convertidos
    // }

    // // Conversão de inteiro para float
    // if (isIntegerType(fromType) && isFloatType(toType)) {
    // return true; // Sempre válido
    // }

    // // Conversão de float para inteiro
    // if (isFloatType(fromType) && isIntegerType(toType)) {
    // return fitsInIntegerType(Double.MAX_VALUE, toType) &&
    // fitsInIntegerType(Double.MIN_VALUE, toType);
    // }

    // // Conversão entre floats
    // if (isFloatType(fromType) && isFloatType(toType)) {
    // return true;
    // }

    // // Conversão para string
    // if (toType.equals(Type.STR)) {
    // return false; // Conversão explícita para string não é permitida
    // }

    // return false;
    // }

    // private boolean fitsInIntegerType(double number, Type toType) {
    // if (toType.equals(Type.I8)) {
    // return number >= Byte.MIN_VALUE && number <= Byte.MAX_VALUE && number ==
    // (int) number;
    // }
    // if (toType.equals(Type.U8)) {
    // return number >= 0 && number <= 255 && number == (int) number;
    // }
    // if (toType.equals(Type.I16)) {
    // return number >= Short.MIN_VALUE && number <= Short.MAX_VALUE && number ==
    // (int) number;
    // }
    // if (toType.equals(Type.U16)) {
    // return number >= 0 && number <= 65535 && number == (int) number;
    // }
    // if (toType.equals(Type.I32)) {
    // return number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE && number
    // == (int) number;
    // }
    // if (toType.equals(Type.U32)) {
    // return number >= 0 && number <= 4294967295L && number == (long) number;
    // }
    // if (toType.equals(Type.I64)) {
    // return number >= Long.MIN_VALUE && number <= Long.MAX_VALUE && number ==
    // (long) number;
    // }
    // if (toType.equals(Type.U64)) {
    // return number >= 0 && number <= Double.MAX_VALUE && number == (long) number;
    // }
    // return false;
    // }

    private boolean fitsInIntegerType(long value, Type toType) {
        if (toType.equals(Type.I8)) {
            return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
        }
        if (toType.equals(Type.U8)) {
            return value >= 0 && value <= 255;
        }
        if (toType.equals(Type.I16)) {
            return value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
        }
        if (toType.equals(Type.U16)) {
            return value >= 0 && value <= 65535;
        }
        if (toType.equals(Type.I32)) {
            return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
        }
        if (toType.equals(Type.U32)) {
            return value >= 0 && value <= 4294967295L;
        }
        if (toType.equals(Type.I64)) {
            return true; // Qualquer long cabe em i64
        }
        if (toType.equals(Type.U64)) {
            return value >= 0; // Apenas valores positivos cabem em u64
        }
        return false;
    }

    private TypedExpr expression(Expr expr) {
        switch (expr) {
            // 1.1. O tipo de um literal inteiro é '{integer}'.
            // 1.2. O tipo de um literal float é '{float}'.
            // 1.3. O tipo de um literal string é 'str'.
            // 1.4. O tipo de um literal char é 'char'.
            // 1.5. O valor de um literal é ele mesmo.
            case Expr.Literal(Token value): {
                return switch (value.type()) {
                    case TokenType.INTEGER -> new TypedExpr.Literal(value, table.typeId(Type.I_LITERAL));
                    case TokenType.FLOAT -> new TypedExpr.Literal(value, table.typeId(Type.F_LITERAL));
                    case TokenType.STRING -> new TypedExpr.Literal(value, table.typeId(Type.STR));
                    case TokenType.CHAR -> new TypedExpr.Literal(value, table.typeId(Type.CHAR));
                    case TokenType.TRUE -> new TypedExpr.Literal(value, table.typeId(Type.BOOLEAN));
                    case TokenType.FALSE -> new TypedExpr.Literal(value, table.typeId(Type.BOOLEAN));
                    default -> throw new RuntimeException("Unexpected token type: " + value.type());
                };
            }

            // 2.1. Uma variável precisa estar em escopo para ser usada.
            // 2.2. O tipo de uma variável é o tipo que foi atribuído a ela.
            // 2.3. O valor de uma variável é o valor que foi atribuído a ela no instante em
            // que ele é avaliado.
            case Expr.VariableExpression(Token name): {
                var local = environment.get(name.lexeme());

                if (local.isEmpty()) {
                    error(name, "Uso de variável não declarada: '" + name.lexeme() + "'.");
                    return new TypedExpr.VariableExpression(name, table.typeId(Type.INVALID));
                }

                switch (local.get()) {
                    case EnvEntry.Local(Id<LocalInfo> localId): {
                        return new TypedExpr.VariableExpression(name, table.locals().get(localId).get().type());
                    }
                    case EnvEntry.Function(Id<FunctionInfo> function): {
                        return new TypedExpr.VariableExpression(name, table.functions().get(function).get().type());
                    }
                }
            }

            // 3.1. O tipo de uma lista é o tipo dos elementos que ela contém.
            // 3.2. Todos os elementos de uma lista precisam ter tipos compatíveis.
            // 3.3. O valor de uma lista é a própria lista.
            case Expr.ListExpression(FilePosition position, List<Expr> elements): {
                if (elements.isEmpty()) {
                    return new TypedExpr.ListExpression(position, List.of(),
                            table.typeId(new Type.Named("[]", List.of(Type.ANY))));
                }

                // tente descobrir o tipo da lista.
                // para todo tipo T, T <= ANY.
                Type elementType = Type.ANY;
                var typedElements = new ArrayList<TypedExpr>();
                for (var element : elements) {
                    var typedElement = expression(element);
                    var common = TypeAlgebra.glb(elementType, table.typeDefinition(typedElement.type()));
                    // TODO: sinalizar qual elemento é incompatível.
                    typedElements.add(typedElement);
                    elementType = common;
                }

                if (elementType.equals(Type.NOTHING)) {
                    error(position, "Os elementos de uma lista devem ter tipos compatíveis.");
                    elementType = Type.INVALID;
                }

                // lub e glb podem criar novos tipos que devem ser registrados; garantimos isso
                // aqui.
                table.typeId(elementType);

                var listType = new Type.Named("[]", List.of(elementType));
                return new TypedExpr.ListExpression(position, typedElements, table.typeId(listType));
            }

            // TODO: definir o que é um lugar atribuível.
            // TODO: definir o que é um tipo compatível.
            // TODO: definir igualdade de valores.

            // 4.1. O tipo de uma operação binária é o tipo do resultado da operação.

            // 4.2. Os tipos dos operandos precisam ser compatíveis com os requeridos pela
            // operação.

            // 4.3. As operações são as seguintes:

            // 4.3.1. Atribuição: =
            // 4.3.1.1. O lado esquerdo de uma atribuição precisa ser um lugar atribuível.
            // 4.3.1.2. O tipo do lado direito precisa ser compatível com o tipo do lado
            // esquerdo.
            // 4.3.1.3. O efeito da atribuição é que o lugar atribuível passa a ter o valor
            // do lado direito.
            // 4.3.1.4. O valor da atribuição é o valor do lado direito.

            // 4.3.2. Módulo com atribuição: %=
            // 4.3.2.1. A operação a %= b é válida se e somente se a = a % b for válida.
            // 4.3.2.3. O tipo da operação é o tipo dos operandos.
            // 4.3.2.4. O valor da operação é a % b.
            // 4.3.2.5. O efeito da operação é que a passa a ter o valor a % b.

            // 4.3.3. Soma com atribuição: +=
            // 4.3.3.1. A operação a += b é válida se e somente se a = a + b for válida.
            // 4.3.3.3. O tipo da operação é o tipo dos operandos.
            // 4.3.3.4. O valor da operação é a % b.
            // 4.3.3.5. O efeito da operação é que a passa a ter o valor a % b.

            // 4.3.4. Subtração com atribuição: -=
            // 4.3.4.1. A operação a -= b é válida se e somente se a = a - b for válida.
            // 4.3.4.3. O tipo da operação é o tipo dos operandos.
            // 4.3.4.4. O valor da operação é a - b.
            // 4.3.4.5. O efeito da operação é que a passa a ter o valor a - b.

            // 4.3.5. Multiplicação com atribuição: *=
            // 4.3.5.1. A operação a *= b é válida se e somente se a = a * b for válida.
            // 4.3.5.3. O tipo da operação é o tipo dos operandos.
            // 4.3.5.4. O valor da operação é a * b.
            // 4.3.5.5. O efeito da operação é que a passa a ter o valor a * b.

            // 4.3.6. Divisão com atribuição: /=
            // 4.3.6.1. A operação a /= b é válida se e somente se a = a / b for válida.
            // 4.3.6.3. O tipo da operação é o tipo dos operandos.
            // 4.3.6.4. O valor da operação é a / b.
            // 4.3.6.5. O efeito da operação é que a passa a ter o valor a / b.

            // 4.3.7. Exponenciação com atribuição: ^=
            // 4.3.7.1. A operação a ^= b é válida se e somente se a = a ^ b for válida.
            // 4.3.7.3. O tipo da operação é o tipo dos operandos.
            // 4.3.7.4. O valor da operação é a ^ b.
            // 4.3.7.5. O efeito da operação é que a passa a ter o valor a ^ b.

            // 4.3.8. Intervalo: ..
            // 4.3.8.1. O tipo de um intervalo é o tipo dos elementos que ele contém.
            // 4.3.8.2. O tipo do lado esquerdo deve ser compatível com o tipo do lado
            // direito.
            // 4.3.8.3. O valor de um intervalo é o próprio intervalo.

            // 4.3.9. Ou: ||
            // 4.3.9.1. O tipo do operador é booleano.
            // 4.3.9.2. Os dois operandos precisam ser booleanos.
            // 4.3.9.3. O valor da operação é verdadeiro sse pelo menos um dos operandos for
            // verdadeiro.
            // 4.3.9.4. O operador possui curto-circuito. O operando à direita somente é
            // avaliado se o da esquerda for falso.

            // 4.3.10. E: &&
            // 4.3.10.1. O tipo do operador é booleano.
            // 4.3.10.2. Os dois operandos precisam ser booleanos.
            // 4.3.10.3. O valor da operação é verdadeiro sse ambos os operandos forem
            // verdadeiros.
            // 4.3.10.4. O operador possui curto-circuito. O operando à direita somente é
            // avaliado se o da esquerda for verdadeiro.

            // 4.3.11. Igual: ==
            // 4.3.11.1. O tipo do operador é booleano.
            // 4.3.11.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.11.3. O valor da operação é verdadeiro sse os valores dos operandos forem
            // iguais.

            // 4.3.12. Não igual: !=
            // 4.3.12.1. O tipo do operador é booleano.
            // 4.3.12.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.12.3. O valor da operação é verdadeiro sse os valores dos operandos não
            // forem iguais.

            // 4.3.13. Menor que: <
            // 4.3.13.1. O tipo do operador é booleano.
            // 4.3.13.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.13.3. Caso os operandos sejam numéricos:
            // 4.3.13.3.1. O valor da operação é verdadeiro sse o operando da esquerda for
            // menor do que o da direita.
            // 4.3.13.4. Caso os operandos sejam strings:
            // 4.3.13.4.1. O valor da operação é verdadeiro sse o operando da esquerda vier
            // antes do que o da direita em ordem lexicográfica (alfabética). A ordem
            // lexicográfica utilizada é os valores dos caracteres da string.
            // 4.3.13.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.14. Menor ou igual: <=
            // 4.3.14.1. O tipo do operador é booleano.
            // 4.3.14.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.14.3. Caso os operandos sejam numéricos:
            // 4.3.14.3.1. O valor da operação é verdadeiro sse o operando da esquerda for
            // menor ou igual do que o da direita.
            // 4.3.14.4. Caso os operandos sejam strings:
            // 4.3.14.4.1. O valor da operação é verdadeiro sse o operando da esquerda vier
            // antes do que o da direita em ordem lexicográfica (alfabética) ou for igual. A
            // ordem lexicográfica utilizada é os valores dos caracteres da string.
            // 4.3.14.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.15. Maior que: >
            // 4.3.15.1. O tipo do operador é booleano.
            // 4.3.15.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.15.3. Caso os operandos sejam numéricos:
            // 4.3.15.3.1. O valor da operação é verdadeiro sse o operando da esquerda for
            // maior do que o da direita.
            // 4.3.15.4. Caso os operandos sejam strings:
            // 4.3.15.4.1. O valor da operação é verdadeiro sse o operando da esquerda vier
            // antes do que o da direita em ordem lexicográfica (alfabética). A ordem
            // lexicográfica utilizada é os valores dos caracteres da string.
            // 4.3.15.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.16. Maior ou igual: >=
            // 4.3.16.1. O tipo do operador é booleano.
            // 4.3.16.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.16.3. Caso os operandos sejam numéricos:
            // 4.3.16.3.1. O valor da operação é verdadeiro sse o operando da esquerda for
            // maior ou igual do que o da direita.
            // 4.3.16.4. Caso os operandos sejam strings:
            // 4.3.16.4.1. O valor da operação é verdadeiro sse o operando da esquerda vier
            // depois do que o da direita em ordem lexicográfica (alfabética) ou for igual.
            // A ordem lexicográfica utilizada é os valores dos caracteres da string.
            // 4.3.16.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.17. Soma: +
            // 4.3.17.1. O tipo da operação é o tipo dos operandos.
            // 4.3.17.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.17.3. Caso os operandos sejam inteiros:
            // 4.3.17.3.1. O valor da operação é a soma dos valores dos operandos.
            // 4.3.17.3.2. Caso a soma não caiba no tipo dos operandos, a operação é
            // inválida.
            // 4.3.17.4. Caso os operandos sejam floats:
            // 4.3.17.4.1. O valor da operação é a soma dos valores dos operandos.
            // 4.3.17.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.18. Subtração: -
            // 4.3.18.1. O tipo da operação é o tipo dos operandos.
            // 4.3.18.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.18.3. Caso os operandos sejam inteiros:
            // 4.3.18.3.1. O valor da operação é a subtração do lado esquerdo pelo lado
            // direito dos valores dos operandos.
            // 4.3.18.3.2. Caso a subtração não caiba no tipo dos operandos, a operação é
            // inválida.
            // 4.3.18.4. Caso os operandos sejam floats:
            // 4.3.18.4.1. O valor da operação é a subtração do lado esquerdo pelo lado
            // direito dos valores dos operandos.
            // 4.3.18.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.19. Multiplicação: *
            // 4.3.19.1. O tipo da operação é o tipo dos operandos.
            // 4.3.19.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.19.3. Caso os operandos sejam inteiros:
            // 4.3.19.3.1. O valor da operação é a multiplicação dos valores dos operandos.
            // 4.3.19.3.2. Caso a multiplicação não caiba no tipo dos operandos, a operação
            // é inválida.
            // 4.3.19.4. Caso os operandos sejam floats:
            // 4.3.19.4.1. O valor da operação é a multiplicação dos valores dos operandos.
            // 4.3.19.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.20. Divisão: /
            // 4.3.20.1. O tipo da operação é o tipo dos operandos.
            // 4.3.20.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.20.3. Caso os operandos sejam inteiros:
            // 4.3.20.3.1. O valor da operação é o quociente truncado dos valores dos
            // operandos.
            // 4.3.20.3.2. Caso o divisor seja zero, a operação é inválida.
            // 4.3.20.4. Caso os operandos sejam floats:
            // 4.3.20.4.1. O valor da operação é o quociente dos valores dos operandos.
            // 4.3.20.4.2. As regras de divisão por zero de floats do padrão IEEE-754 se
            // aplicam.
            // 4.3.20.5. Para qualquer outro tipo, a operação é inválida.

            // 4.3.21. Exponenciação: ^
            // 4.3.21.1. O tipo da operação é o tipo dos operandos.
            // 4.3.21.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.21.3. Caso os operandos sejam inteiros:
            // 4.3.21.3.1. O valor da operação é o valor da base elevado à potência do
            // expoente.
            // 4.3.21.3.2. Caso a exponenciação não caiba no tipo dos operandos, a operação
            // é inválida.
            // 4.3.21.3.3. Caso o expoente seja negativo, a operação é inválida.
            // 4.3.21.3.4. 0 ^ 0 é definido como 1.
            // 4.3.21.4. Caso os operandos sejam floats:
            // 4.3.21.4.1. O valor da operação é o valor da base elevado à potência do
            // expoente.
            // 4.3.21.4.2. 0 ^ 0 é definido como 1.
            // 4.3.21.4.3. As regras de exponenciação de floats do padrão IEEE-754 se
            // aplicam.

            // 4.3.22. Módulo: %
            // 4.3.22.1. O tipo da operação é o tipo dos operandos.
            // 4.3.22.2. Os dois operandos precisam ser do mesmo tipo.
            // 4.3.22.3. Caso os operandos sejam inteiros:
            // 4.3.22.3.1. O valor da operação é o resto truncado da divisão euclidiana dos
            // valores dos operandos.
            // 4.3.22.3.2. Caso o divisor seja zero, a operação é inválida.
            // 4.3.22.4. Caso os operandos sejam floats:
            // 4.3.22.4.1. O valor da operação é o resto da divisão euclidiana dos valores
            // dos operandos.
            // 4.3.22.4.2. As regras de divisão por zero de floats do padrão IEEE-754 se
            // aplicam.
            // 4.3.22.5. Para qualquer outro tipo, a operação é inválida.

            case Expr.BinaryExpression(Expr left, Token operator, Expr right): {

                // Analisar os operandos
                var leftExpr = expression(left);
                var rightExpr = expression(right);

                // Verificar operadores de intervalos (..)
                if (operator.type() == TokenType.DOT_DOT) {
                    // Verificar se os tipos dos limites são compatíveis
                    if (!compatibleTypes(leftExpr.type(), rightExpr.type())) {
                        error(operator, "Os tipos dos limites do intervalo não são compatíveis. Esquerda: "
                                + leftExpr.type() + ", Direita: " + rightExpr.type());
                        return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr,
                                table.typeId(Type.INVALID));
                    }

                    // Retornar o tipo do intervalo como RANGE
                    var rangeType = new Type.Named("..", List.of(table.typeDefinition(leftExpr.type())));
                    return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, table.typeId(rangeType));
                }

                // Verificar operadores de atribuição e compostos (+=, -=, etc.)

                if (operator.type() == TokenType.EQUAL || operator.type() == TokenType.PLUS_EQUAL
                        || operator.type() == TokenType.MINUS_EQUAL || operator.type() == TokenType.STAR_EQUAL
                        || operator.type() == TokenType.SLASH_EQUAL || operator.type() == TokenType.PERCENT_EQUAL
                        || operator.type() == TokenType.HAT_EQUAL) {

                    // O lado esquerdo deve ser uma variável ou campo de registro (lugar atribuível)
                    if (!isAssignableExpression(leftExpr)) {
                        error(operator,
                                "O lado esquerdo de uma atribuição deve ser uma variável ou um campo de registro.");
                        return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr,
                                table.typeId(Type.INVALID));
                    }

                    // Validar compatibilidade de tipos entre os operandos
                    if (!compatibleTypes(leftExpr.type(), rightExpr.type())) {
                        error(operator, "Os tipos dos operandos não são compatíveis para a operação '"
                                + operator.lexeme() + "'. Esquerda: " + table.types().get(leftExpr.type()).get().type()
                                + ", Direita: "
                                + table.types().get(rightExpr.type()).get().type());
                        return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr,
                                table.typeId(Type.INVALID));
                    }

                    // Retornar o tipo do lado esquerdo como resultado
                    return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, leftExpr.type());
                }

                // Validar compatibilidade de tipos para operadores binários gerais
                if (!compatibleTypes(leftExpr.type(), rightExpr.type())) {
                    error(operator, "Os tipos dos operandos não são compatíveis para a operação '"
                            + operator.lexeme() + "'. Esquerda: " + table.types().get(leftExpr.type()).get().type()
                            + ", Direita: "
                            + table.types().get(rightExpr.type()).get().type());
                    return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, table.typeId(Type.INVALID));
                }

                // Determinar o tipo do resultado com base no operador
                Id<TypeInfo> resultType = switch (operator.type()) {

                    // Operações aritméticas retornam o tipo do lado esquerdo
                    case PLUS, MINUS, STAR, SLASH, PERCENT, HAT -> {
                        if (!isNumericType(leftExpr.type()) || !isNumericType(rightExpr.type())) {
                            error(operator,
                                    "Os operandos devem ser numéricos para a operação '" + operator.lexeme() + "'.");
                            yield table.typeId(Type.INVALID);
                        }

                        else if ((operator.type() == TokenType.SLASH || operator.type() == TokenType.PERCENT)
                                && rightExpr instanceof TypedExpr.Literal literal
                                && literal.value().lexeme().equals("0")) {
                            error(operator, "Divisão por zero.");
                            yield table.typeId(Type.INVALID);
                        }

                        // Retorna o tipo concreto, evitando {integer} quando possível
                        yield table.typeId(TypeAlgebra.glb(table.typeDefinition(leftExpr.type()),
                                table.typeDefinition(rightExpr.type())));
                    }

                    // Comparações retornam booleanos
                    case LESSER, LESSER_EQUAL, GREATER, GREATER_EQUAL -> {
                        if (!isNumericType(leftExpr.type()) || !isNumericType(rightExpr.type())) {
                            error(operator,
                                    "Os operandos devem ser numéricos para a operação '" + operator.lexeme() + "'.");
                            yield table.typeId(Type.INVALID);
                        }
                        yield table.typeId(Type.BOOLEAN);
                    }

                    // Comparações de igualdade retornam booleanos
                    case EQUAL_EQUAL, BANG_EQUAL -> table.typeId(Type.BOOLEAN);

                    // Operadores lógicos também retornam booleanos
                    case AND, OR -> {
                        if (!compatibleTypes(leftExpr.type(), table.typeId(Type.BOOLEAN))
                                || !compatibleTypes(rightExpr.type(), table.typeId(Type.BOOLEAN))) {
                            error(operator, "Operadores lógicos exigem operandos booleanos.");
                            yield table.typeId(Type.INVALID);
                        }
                        yield table.typeId(Type.BOOLEAN);
                    }

                    // Intervalo
                    case DOT_DOT -> {
                        if (!isIntegerType(leftExpr.type()) || !isIntegerType(rightExpr.type())
                                || !compatibleTypes(leftExpr.type(), rightExpr.type())) {
                            error(operator,
                                    "Intervalos só podem ser construídos entre dois valores numéricos inteiros.");
                            yield table.typeId(Type.INVALID);
                        }
                        var lext = table.typeDefinition(leftExpr.type());
                        var rext = table.typeDefinition(rightExpr.type());
                        var common = TypeAlgebra.glb(lext, rext);
                        table.typeId(common);
                        yield table.typeId(new Type.Named("..", List.of(common)));
                    }

                    // Operadores não suportados
                    default -> {
                        error(operator, "Operação '" + operator.lexeme() + "' não suportada.");
                        yield table.typeId(Type.INVALID);
                    }
                };

                // Retornar a expressão binária tipada com o tipo resultante
                return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, resultType);
            }

            case Expr.UnaryExpression(Token operator, Expr operand): {
                var operandExpr = expression(operand);
                return switch (operator.type()) {
                    case TokenType.BANG ->
                        new TypedExpr.UnaryExpression(operator, operandExpr, table.typeId(Type.BOOLEAN));
                    case TokenType.MINUS -> new TypedExpr.UnaryExpression(operator, operandExpr, operandExpr.type());
                    case TokenType.HASH ->
                        new TypedExpr.UnaryExpression(operator, operandExpr, table.typeId(Type.I_LITERAL));
                    default -> throw new RuntimeException("Operação unária não suportada: " + operator.type());
                };
            }

            case Expr.FunctionCall(Token parens, Expr target, List<Expr.Argument> arguments): {
                // Caso não seja uma conversão explícita, tratar como função normal
                var typedTarget = expression(target);
                if (!(table.typeDefinition(typedTarget.type()) instanceof Type.Function functionType)) {
                    error(typedTarget instanceof TypedExpr.VariableExpression variable ? variable.name() : null,
                            "O alvo da chamada não é uma função.");
                    return new TypedExpr.FunctionCall(typedTarget, List.of(), table.typeId(Type.INVALID));
                }

                // Analisar argumentos e verificar compatibilidade
                var typedArguments = new ArrayList<TypedExpr.Argument>();
                var expectedArgs = functionType.args();

                if (arguments.size() != expectedArgs.size()) {
                    error(parens, "Número incorreto de argumentos. Esperado: " + expectedArgs.size() +
                            ", recebido: " + arguments.size());
                    return new TypedExpr.FunctionCall(typedTarget, List.of(), table.typeId(Type.INVALID));
                }

                for (int i = 0; i < arguments.size(); i++) {
                    var arg = arguments.get(i);
                    var expectedType = expectedArgs.get(i);
                    var typedValue = expression(arg.value());
                    if (!compatibleTypes(typedValue.type(), table.typeId(expectedType))) {
                        error(arg.value() instanceof Expr.Literal literal ? literal.value() : null,
                                "Tipo incompatível para o argumento " + (i + 1) + ". Esperado: " + expectedType +
                                        ", recebido: " + table.typeDefinition(typedValue.type()));
                    }
                    typedArguments.add(new TypedExpr.Argument(arg.label(), typedValue, typedValue.type()));
                }

                // O tipo da chamada é o tipo de retorno da função
                return new TypedExpr.FunctionCall(typedTarget, typedArguments, table.typeId(functionType.ret()));
            }

            case Expr.Argument(Optional<Token> label, Expr value): {
                var typedValue = expression(value);
                return new TypedExpr.Argument(label, typedValue, typedValue.type());
            }

            case Expr.ListAccess(Expr target, Expr place): {
                // Analisar a lista (alvo)
                var targetExpr = expression(target);
                if (!(table.typeDefinition(targetExpr.type()) instanceof Type.Named listType)
                        || !listType.name().equals("[]")) {
                    error(targetExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "O alvo do acesso deve ser uma lista.");
                    return new TypedExpr.ListAccess(targetExpr, expression(place), table.typeId(Type.INVALID));
                }

                // Analisar o índice
                var placeExpr = expression(place);
                if (!compatibleTypes(placeExpr.type(), table.typeId(Type.I_LITERAL))) {
                    error(placeExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "O índice de acesso deve ser um número inteiro.");
                    return new TypedExpr.ListAccess(targetExpr, placeExpr, table.typeId(Type.INVALID));
                }

                // Retornar o tipo dos elementos da lista
                return new TypedExpr.ListAccess(targetExpr, placeExpr, table.typeId(listType.args().get(0)));
            }

            case Expr.RecAccess(Expr target, Token place): {
                // Analisar o alvo do acesso
                var targetExpr = expression(target);

                // Verificar se o alvo é um registro
                if (!(table.typeDefinition(targetExpr.type()) instanceof Type.Record recordType)) {
                    error(place, "O alvo de um acesso a registro deve ser um registro.");
                    return new TypedExpr.RecAccess(targetExpr, place, table.typeId(Type.INVALID));
                }

                // Verificar se o campo existe no registro
                var fieldIndex = recordType.fields().indexOf(place.lexeme());
                if (fieldIndex == -1) {
                    error(place, "O campo '" + place.lexeme() + "' não existe no registro.");
                    return new TypedExpr.RecAccess(targetExpr, place, table.typeId(Type.INVALID));
                }

                // Retornar o tipo do campo acessado
                var fieldType = recordType.types().get(fieldIndex);
                return new TypedExpr.RecAccess(targetExpr, place, table.typeId(fieldType));
            }

            case Expr.ForExpression(Token variable, TypeAst typeAst, Expr range, Expr.Block body): {
                // Resolver o tipo da variável do loop
                var variableType = typeAst(typeAst);

                // Analisar a expressão do intervalo
                var rangeExpr = expression(range);
                if (!(table.typeDefinition(rangeExpr.type()) instanceof Type.Named namedType
                        && namedType.name().equals(".."))) {
                    error(variable, "A expressão do intervalo deve ser um intervalo válido.");
                    return new TypedExpr.ForExpression(variable, typeAst, rangeExpr, null, table.typeId(Type.INVALID));
                }

                // Criar escopo para o corpo do loop
                environment = new Environment(environment);
                var localInfo = new LocalInfo(variable.lexeme(), variableType);
                var localId = table.locals().insert(localInfo);
                environment.put(variable.lexeme(), new EnvEntry.Local(localId));
                var bodyExpr = block(body);
                environment = environment.enclosing().orElse(null);

                // O tipo do loop `for` é sempre `unit`
                return new TypedExpr.ForExpression(variable, typeAst, rangeExpr, bodyExpr, table.typeId(Type.UNIT));
            }

            case Expr.IfExpression(Expr condition, Expr.Block thenBranch, Optional<Expr> elseBranch): {
                var conditionExpr = expression(condition);

                // Certifique-se de que a condição é do tipo booleano
                if (!compatibleTypes(conditionExpr.type(), table.typeId(Type.BOOLEAN))) {
                    error(conditionExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "A condição do 'if' deve ser do tipo booleano. \n" + conditionExpr + "\nTipo Recebido => "
                                    + conditionExpr.type());
                    return new TypedExpr.IfExpression(conditionExpr, block(thenBranch),
                            elseBranch.map(this::expression), table.typeId(Type.INVALID));
                }

                // Criar escopo para os blocos
                environment = new Environment(environment);
                var thenExpr = block(thenBranch);
                environment = environment.enclosing().orElse(null);

                Optional<TypedExpr> elseExpr = elseBranch.map(branch -> {
                    environment = new Environment(environment);
                    TypedExpr result = expression(branch);
                    environment = environment.enclosing().orElse(null);
                    return result;
                });

                var ifType = elseExpr.map(elseBlock -> compatibleTypes(thenExpr.type(), elseBlock.type())
                        ? thenExpr.type()
                        : table.typeId(Type.INVALID)).orElse(table.typeId(Type.UNIT));

                if (table.typeDefinition(ifType).equals(Type.INVALID) && elseExpr.isPresent()) {
                    error(conditionExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "Os blocos 'then' e 'else' devem ter o mesmo tipo.");
                }

                return new TypedExpr.IfExpression(conditionExpr, thenExpr, elseExpr, ifType);
            }

            case Expr.WhileExpression(Expr condition, Expr.Block body): {
                // Analisar a condição
                var conditionExpr = expression(condition);
                if (!compatibleTypes(conditionExpr.type(), table.typeId(Type.BOOLEAN))) {
                    error(conditionExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "A condição do 'while' deve ser do tipo booleano.");
                    return new TypedExpr.WhileExpression(conditionExpr, null, table.typeId(Type.INVALID));
                }

                // Criar escopo para o corpo do loop
                environment = new Environment(environment);
                var bodyExpr = block(body);
                environment = environment.enclosing().orElse(null);

                // O tipo do loop `while` é sempre `unit`
                return new TypedExpr.WhileExpression(conditionExpr, bodyExpr, table.typeId(Type.UNIT));
            }

            case Expr.ReturnExpression(Token keyword, Optional<Expr> value): {
                // Analisar o valor do retorno
                var returnValue = value.map(this::expression);
                var returnType = returnValue.map(TypedExpr::type).orElse(table.typeId(Type.UNIT));

                // Obter o tipo de retorno da função atual
                var functionType = table.typeDefinition(table.functions().get(currentFunction.get()).get().type());
                var functionReturnType = ((Type.Function) functionType).ret();

                // Verificar compatibilidade dos tipos
                if (!compatibleTypes(returnType, table.typeId(functionReturnType))) {
                    error(keyword,
                            "O tipo do valor retornado não é compatível com o tipo de retorno da função. Retorno esperado: "
                                    + functionReturnType + ", retorno recebido: "
                                    + table.typeDefinition(returnType));
                    return new TypedExpr.ReturnExpression(returnValue, table.typeId(Type.INVALID));
                }

                // Retornar a expressão de retorno tipada
                return new TypedExpr.ReturnExpression(returnValue, returnType);
            }

            case Expr.DebugExpression(Expr value): {
                // Analisar a expressão a ser depurada
                var debugValue = expression(value);

                // Retornar a expressão de depuração tipada
                return new TypedExpr.DebugExpression(debugValue, debugValue.type());
            }

            case Expr.ReadExpression(List<Expr> variables): {
                var typedVariables = new ArrayList<TypedExpr>();

                for (var variable : variables) {
                    var typedVariable = expression(variable);

                    if (!isAssignableExpression(typedVariable)) {
                        error(variable instanceof Expr.Literal literal ? literal.value() : null,
                                "Somente variáveis podem ser usadas na expressão 'read'.");

                        return new TypedExpr.ReadExpression(List.of(), table.typeId(Type.INVALID));
                    }

                    typedVariables.add(typedVariable);
                }

                return new TypedExpr.ReadExpression(typedVariables, table.typeId(Type.UNIT));
            }

            case Expr.Block block:
                return block(block);
        }
    }

    private boolean isAssignableExpression(TypedExpr leftExpr) {
        return leftExpr instanceof TypedExpr.VariableExpression
                || leftExpr instanceof TypedExpr.RecAccess
                || leftExpr instanceof TypedExpr.ListAccess;
    }

    private Id<TypeInfo> typeAst(TypeAst typeAst) {
        switch (typeAst) {
            case TypeAst.Named(Token name): {
                var entry = environment.getType(name.lexeme());
                if (!entry.isPresent()) {
                    error(name, "Uso de tipo não declarado");
                    return table.typeId(Type.INVALID);
                } else {
                    switch (entry.get()) {
                        case TypeEnvEntry.Type(Id<TypeInfo> id):
                            return id;
                        case TypeEnvEntry.Record(Id<RecordInfo> recordId):
                            return table.records().get(recordId).get().type();
                    }
                }
            }
            case TypeAst.List(TypeAst elementType): {
                var elemType = table.typeDefinition(typeAst(elementType));
                var listType = new Type.Named("[]", List.of(elemType));
                return table.typeId(listType);
            }
        }
    }

    private boolean compatibleTypes(Id<TypeInfo> type1, Id<TypeInfo> type2) {
        return TypeAlgebra.compatibleTypes(table.typeDefinition(type1), table.typeDefinition(type2));
    }

    // Verifica se o tipo é um inteiro (ex.: i32, i64, u8, etc.)
    private boolean isIntegerType(Id<TypeInfo> id) {
        return TypeAlgebra.isIntegerType(table.typeDefinition(id));
    }

    // Verifica se o tipo é um float (ex.: f32, f64)
    private boolean isNumericType(Id<TypeInfo> id) {
        return TypeAlgebra.isNumericType(table.typeDefinition(id));
    }

    private void error(Token token, String message) {
        FilePosition position = token != null ? token.where() : new FilePosition(1, 1, "???");
        errors.add(new CompilerError(ErrorType.SEMANTIC, message, position));
    }

    private void error(FilePosition position, String message) {
        errors.add(new CompilerError(ErrorType.SEMANTIC, message, position));
    }
}
