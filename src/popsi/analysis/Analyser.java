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

        // Registrar tipos básicos
        registerBasicTypes();
    }

    private void registerBasicTypes() {
        table.insertType("i32", new SymbolTable.TypeInfo(new SymbolTable.Id("i32".hashCode())));
        table.insertType("i64", new SymbolTable.TypeInfo(new SymbolTable.Id("i64".hashCode())));
        table.insertType("f32", new SymbolTable.TypeInfo(new SymbolTable.Id("f32".hashCode())));
        table.insertType("f64", new SymbolTable.TypeInfo(new SymbolTable.Id("f64".hashCode())));
        table.insertType("bool", new SymbolTable.TypeInfo(new SymbolTable.Id("bool".hashCode())));
        table.insertType("char", new SymbolTable.TypeInfo(new SymbolTable.Id("char".hashCode())));
        table.insertType("str", new SymbolTable.TypeInfo(new SymbolTable.Id("str".hashCode())));
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
        // Lista para armazenar as instruções tipadas
        var typedStatements = new ArrayList<TypedStmt>();

        // Analisar cada instrução
        for (var statement : block.statements()) {
            typedStatements.add(statement(statement));
        }

        // Analisar a última instrução, se existir
        var lastTypedStatement = block.lastStatement().map(this::statement);

        // Determinar o tipo do bloco
        Type blockType = lastTypedStatement.map(TypedStmt::type).orElse(Type.UNIT);

        if (typedStatements.isEmpty() && lastTypedStatement.isEmpty()) {
            error((FilePosition) null,
                    "Bloco vazio detectado. Certifique-se de que seu código contém pelo menos uma instrução.");
        }

        // Retornar o bloco tipado
        return new TypedExpr.Block(typedStatements, lastTypedStatement, blockType);
    }

    private TypedAst.Function function(Ast.Function function) {
        var parameters = new ArrayList<TypedAst.Parameter>();

        // Resolver os tipos dos parâmetros
        for (var param : function.parameters()) {
            var type = resolveType(param.type());
            parameters.add(new TypedAst.Parameter(param.name(), param.type(), type));
        }

        // Resolver o tipo de retorno
        var returnType = function.returnType().map(this::resolveType).orElse(Type.UNIT);

        // Registrar a função na tabela de símbolos
        if (table.lookupFunction(function.name().lexeme()).isPresent()) {
            error(function.name(), "Função '" + function.name().lexeme() + "' já foi declarada.");
        } else {
            table.insertFunction(function.name().lexeme(),
                    new SymbolTable.FunctionInfo(new SymbolTable.Id(function.name().hashCode())));
        }

        // Criar escopo para a função
        environment = new Environment(environment);

        // Registrar parâmetros no ambiente local
        for (var param : parameters) {
            environment.put(param.name().lexeme(), new Environment.EnvEntry.Local());
            table.insert(param.name().lexeme(), param.resolvedType());
        }

        // Analisar o corpo da função
        var bodyExpr = block(function.body());

        // Restaurar o escopo anterior
        environment = environment.enclosing().orElse(null);

        // Verificar compatibilidade do tipo de retorno
        if (!compatibleTypes(bodyExpr.type(), returnType)) {
            error(function.name(), "O tipo do corpo da função não é compatível com o tipo de retorno declarado. "
                    + "Esperado: " + returnType + ", recebido: " + bodyExpr.type());
        }

        return new TypedAst.Function(function.name(), parameters, function.returnType(), function.body(), returnType);
    }

    private TypedAst.Rec rec(Ast.Rec rec) {
        var fields = new ArrayList<TypedAst.RecField>();

        for (var field : rec.fields()) {
            var type = resolveType(field.type());
            fields.add(new TypedAst.RecField(field.name(), field.type(), type));
        }

        // Adicionar o registro na tabela de símbolos
        table.insertType(rec.name().lexeme(), new SymbolTable.TypeInfo(new SymbolTable.Id(rec.name().hashCode())));

        return new TypedAst.Rec(rec.name(), fields);
    }

    private TypedStmt statement(Stmt stmt) {

        // 1.1. O tipo de uma declaração é o tipo da expressão que ela contém.
        // 1.1.1. O tipo da declaração e o tipo da expressão devem ser compatíveis.
        // 1.2. O efeito de uma declaração é adicionar uma variável ao ambiente.
        // 1.2.1. A declaração sai de escopo ao final do bloco em que ela foi declarada.
        // 1.2.2. Caso já exista uma variável com o mesmo nome no ambiente, a existente
        // é ocultada, até que essa variável saia de escopo.
        // 1.3. O valor de uma declaração é o valor da expressão que ela contém, ou o
        // valor Unit, caso a expressão seja vazia.I

        switch (stmt) {
            case Stmt.Declaration(Token name, TypeAst typeAst, Optional<Expr> value): {
                var resolvedType = resolveType(typeAst);

                // Analisar a expressão inicial, se houver
                var typedValue = value.map(this::expression);

                // Validar compatibilidade de tipos
                if (typedValue.isPresent() && !compatibleTypes(typedValue.get().type(), resolvedType)) {
                    error(name, "Tipo incompatível para a variável '" + name.lexeme() + "'. Esperado: " + resolvedType
                            + ", recebido: " + typedValue.get().type());
                }

                // Verificar se a variável já foi declarada
                if (table.lookup(name.lexeme()).isPresent()) {
                    error(name, "Variável '" + name.lexeme() + "' já foi declarada.");
                } else {
                    table.insert(name.lexeme(), resolvedType);
                }

                environment.put(name.lexeme(), new Environment.EnvEntry.Local());

                return new TypedStmt.Declaration(name, typeAst, typedValue, resolvedType);
            }

            // 2.1. O tipo de uma expressão é o tipo do valor que ela representa.
            // 2.2. O valor de uma expressão é o valor que ela representa.
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
            // 1.1. O tipo de um literal inteiro é '{integer}'.
            // 1.2. O tipo de um literal float é '{float}'.
            // 1.3. O tipo de um literal string é 'str'.
            // 1.4. O tipo de um literal char é 'char'.
            // 1.5. O valor de um literal é ele mesmo.
            case Expr.Literal(Token value): {
                return switch (value.type()) {
                    case TokenType.INTEGER -> new TypedExpr.Literal(value, Type.I_LITERAL);
                    case TokenType.FLOAT -> new TypedExpr.Literal(value, Type.F_LITERAL);
                    case TokenType.STRING -> new TypedExpr.Literal(value, Type.STR);
                    case TokenType.CHAR -> new TypedExpr.Literal(value, Type.CHAR);
                    default -> throw new RuntimeException("Unexpected token type: " + value.type());
                };
            }

            // 2.1. Uma variável precisa estar em escopo para ser usada.
            // 2.2. O tipo de uma variável é o tipo que foi atribuído a ela.
            // 2.3. O valor de uma variável é o valor que foi atribuído a ela no instante em
            // que ele é avaliado.
            case Expr.VariableExpression(Token name): {
                var variableType = table.lookup(name.lexeme());

                if (variableType.isEmpty()) {
                    error(name, "Uso de variável não declarada: '" + name.lexeme() + "'.");
                    return new TypedExpr.VariableExpression(name, Type.INVALID);
                }

                // Retornar a variável com o tipo correto
                return new TypedExpr.VariableExpression(name, variableType.get());
            }

            // 3.1. O tipo de uma lista é o tipo dos elementos que ela contém.
            // 3.2. Todos os elementos de uma lista precisam ter tipos compatíveis.
            // 3.3. O valor de uma lista é a própria lista.
            case Expr.ListExpression(FilePosition position, List<Expr> elements): {
                if (elements.isEmpty()) {
                    return new TypedExpr.ListExpression(position, List.of(), Type.UNKNOWN);
                }

                var first = expression(elements.get(0));
                var elementType = first.type();
                var typedElements = new ArrayList<TypedExpr>();
                for (var element : elements) {
                    var typedElement = expression(element);
                    // TODO: Não queremos igualdade estrita aqui, mas sim compatibilidade de tipos.
                    // Precisamos implementar um método `Type::compatible` para isso.
                    // Exemplos:
                    // - {integer} é compatível com u8, i8, u16, i16, u32, i32, u64, i64
                    // - {float} é compatível com f32, f64
                    // - [[], [1]] é compatível com [[{integer}]]
                    // Mudar para um método que não seja igualdade estrita.
                    // TODO: seria melhor colocar o erro na posição do elemento, não na posição da
                    // lista.
                    if (!compatibleTypes(typedElement.type(), elementType)) {
                        error(position, "Todos os elementos da lista devem possuir o mesmo tipo.");
                    }
                    typedElements.add(typedElement);
                }

                var listType = new Type.Named("[]", List.of(elementType));
                return new TypedExpr.ListExpression(position, typedElements, listType);
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
                        return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, Type.INVALID);
                    }

                    // Retornar o tipo do intervalo como RANGE
                    var rangeType = new Type.Named("RANGE", List.of(leftExpr.type()));
                    // Registrar o tipo do intervalo no SymbolTable
                    table.insert(operator.lexeme(), rangeType);
                    return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, rangeType);
                }

                // Verificar operadores de atribuição e compostos (+=, -=, etc.)
                if (operator.type() == TokenType.EQUAL || operator.type().toString().endsWith("_EQUAL")) {
                    // O lado esquerdo deve ser uma variável ou campo de registro (lugar atribuível)
                    if (!(leftExpr instanceof TypedExpr.VariableExpression
                            || leftExpr instanceof TypedExpr.RecAccess)) {
                        error(operator,
                                "O lado esquerdo de uma atribuição deve ser uma variável ou um campo de registro.");
                        return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, Type.INVALID);
                    }

                    // Validar compatibilidade de tipos entre os operandos
                    if (!compatibleTypes(leftExpr.type(), rightExpr.type())) {
                        error(operator, "Os tipos dos operandos não são compatíveis para a operação '"
                                + operator.lexeme() + "'. Esquerda: " + leftExpr.type() + ", Direita: "
                                + rightExpr.type());
                        return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, Type.INVALID);
                    }

                    // Registrar o tipo da atribuição no SymbolTable
                    table.insert(operator.lexeme(), leftExpr.type());
                    // Retornar o tipo do lado esquerdo como resultado
                    return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, leftExpr.type());
                }

                // Validar compatibilidade de tipos para operadores binários gerais
                if (!compatibleTypes(leftExpr.type(), rightExpr.type())) {
                    error(operator, "Os tipos dos operandos não são compatíveis para a operação '"
                            + operator.lexeme() + "'. Esquerda: " + leftExpr.type() + ", Direita: " + rightExpr.type());
                    return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, Type.INVALID);
                }

                // Determinar o tipo do resultado com base no operador
                Type resultType = switch (operator.type()) {
                    // Operações aritméticas retornam o tipo do lado esquerdo
                    case PLUS, MINUS, STAR, SLASH, PERCENT, HAT -> {
                        if (!isNumericType(leftExpr.type()) || !isNumericType(rightExpr.type())) {
                            error(operator,
                                    "Os operandos devem ser numéricos para a operação '" + operator.lexeme() + "'.");
                            yield Type.INVALID;
                        }
                        // Retorna o tipo concreto, evitando {integer} quando possível
                        yield leftExpr.type() instanceof Type.Named ? leftExpr.type() : rightExpr.type();
                    }

                    // Comparações retornam booleanos
                    case LESSER, LESSER_EQUAL, GREATER, GREATER_EQUAL -> {
                        if (!isNumericType(leftExpr.type()) || !isNumericType(rightExpr.type())) {
                            error(operator,
                                    "Os operandos devem ser numéricos para a operação '" + operator.lexeme() + "'.");
                            yield Type.INVALID;
                        }
                        yield Type.BOOLEAN;
                    }

                    // Comparações de igualdade retornam booleanos
                    case EQUAL_EQUAL, BANG_EQUAL -> Type.BOOLEAN;

                    // Operadores lógicos também retornam booleanos
                    case AND, OR -> {
                        if (!compatibleTypes(leftExpr.type(), Type.BOOLEAN)
                                || !compatibleTypes(rightExpr.type(), Type.BOOLEAN)) {
                            error(operator, "Operadores lógicos exigem operandos booleanos.");
                            yield Type.INVALID;
                        }
                        yield Type.BOOLEAN;
                    }

                    // Operadores não suportados
                    default -> {
                        error(operator, "Operação '" + operator.lexeme() + "' não suportada.");
                        yield Type.INVALID;
                    }
                };

                // Registrar o tipo resultante no SymbolTable
                table.insert(operator.lexeme(), resultType);

                // Retornar a expressão binária tipada com o tipo resultante
                return new TypedExpr.BinaryExpression(leftExpr, operator, rightExpr, resultType);
            }

            case Expr.UnaryExpression(Token operator, Expr operand): {
                var operandExpr = expression(operand);
                return switch (operator.type()) {
                    case TokenType.BANG -> new TypedExpr.UnaryExpression(operator, operandExpr, Type.BOOLEAN);
                    case TokenType.MINUS -> new TypedExpr.UnaryExpression(operator, operandExpr, operandExpr.type());
                    case TokenType.HASH -> new TypedExpr.UnaryExpression(operator, operandExpr, Type.I_LITERAL);
                    default -> throw new RuntimeException("Operação unária não suportada: " + operator.type());
                };
            }

            case Expr.FunctionCall(Expr target, List<Expr.Argument> arguments): {
                // Analisar o alvo da chamada
                var targetExpr = expression(target);

                // Verificar se o alvo é uma função
                if (!(targetExpr.type() instanceof Type.Function functionType)) {
                    error(targetExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "O alvo da chamada não é uma função.");
                    return new TypedExpr.FunctionCall(targetExpr, List.of(), Type.INVALID);
                }

                // Verificar compatibilidade dos argumentos
                var typedArguments = new ArrayList<TypedExpr.Argument>();
                var expectedArgs = functionType.args();

                if (arguments.size() != expectedArgs.size()) {
                    error(targetExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "Número incorreto de argumentos. Esperado: " + expectedArgs.size() + ", recebido: "
                                    + arguments.size());
                    return new TypedExpr.FunctionCall(targetExpr, List.of(), Type.INVALID);
                }

                for (int i = 0; i < arguments.size(); i++) {
                    var arg = arguments.get(i);
                    var expectedType = expectedArgs.get(i);
                    var typedValue = expression(arg.value());
                    if (!compatibleTypes(typedValue.type(), expectedType)) {
                        error(arg.value() instanceof Expr.Literal literal ? literal.value() : null,
                                "Tipo incompatível para o argumento " + (i + 1) + ". Esperado: " + expectedType
                                        + ", recebido: "
                                        + typedValue.type());
                    }
                    typedArguments.add(new TypedExpr.Argument(arg.label(), typedValue, typedValue.type()));
                }

                // O tipo da chamada é o tipo de retorno da função
                return new TypedExpr.FunctionCall(targetExpr, typedArguments, functionType.ret());
            }

            case Expr.Argument(Optional<Token> label, Expr value):
                var typedValue = expression(value);
                return new TypedExpr.Argument(label, typedValue, typedValue.type());

            case Expr.ListAccess(Expr target, Expr place): {
                // Analisar a lista (alvo)
                var targetExpr = expression(target);
                if (!(targetExpr.type() instanceof Type.Named listType) || !listType.name().equals("[]")) {
                    error(targetExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "O alvo do acesso deve ser uma lista.");
                    return new TypedExpr.ListAccess(targetExpr, expression(place), Type.INVALID);
                }

                // Analisar o índice
                var placeExpr = expression(place);
                if (!compatibleTypes(placeExpr.type(), Type.I_LITERAL)) {
                    error(placeExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "O índice de acesso deve ser um número inteiro.");
                    return new TypedExpr.ListAccess(targetExpr, placeExpr, Type.INVALID);
                }

                // Retornar o tipo dos elementos da lista
                return new TypedExpr.ListAccess(targetExpr, placeExpr, listType.args().get(0));
            }

            case Expr.RecAccess(Expr target, Token place): {
                // Analisar o alvo do acesso
                var targetExpr = expression(target);

                // Verificar se o alvo é um registro
                if (!(targetExpr.type() instanceof Type.Record recordType)) {
                    error(place, "O alvo de um acesso a registro deve ser um registro.");
                    return new TypedExpr.RecAccess(targetExpr, place, Type.INVALID);
                }

                // Verificar se o campo existe no registro
                var fieldIndex = recordType.fields().indexOf(place.lexeme());
                if (fieldIndex == -1) {
                    error(place, "O campo '" + place.lexeme() + "' não existe no registro.");
                    return new TypedExpr.RecAccess(targetExpr, place, Type.INVALID);
                }

                // Retornar o tipo do campo acessado
                var fieldType = recordType.types().get(fieldIndex);
                return new TypedExpr.RecAccess(targetExpr, place, fieldType);
            }

            case Expr.ForExpression(Token variable, TypeAst typeAst, Expr range, Expr.Block body): {
                // Resolver o tipo da variável do loop
                var variableType = resolveType(typeAst);

                // Analisar a expressão do intervalo
                var rangeExpr = expression(range);
                if (!(rangeExpr.type() instanceof Type.Named namedType && namedType.name().equals(".."))) {
                    error(variable, "A expressão do intervalo deve ser um intervalo válido.");
                    return new TypedExpr.ForExpression(variable, typeAst, rangeExpr, null, Type.INVALID);
                }

                // Criar escopo para o corpo do loop
                environment = new Environment(environment);
                environment.put(variable.lexeme(), new Environment.EnvEntry.Local());
                var bodyExpr = block(body);
                environment = environment.enclosing().orElse(null);

                // O tipo do loop `for` é sempre `unit`
                return new TypedExpr.ForExpression(variable, typeAst, rangeExpr, bodyExpr, Type.UNIT);
            }

            case Expr.IfExpression(Expr condition, Expr.Block thenBranch, Optional<Expr.Block> elseBranch): {
                var conditionExpr = expression(condition);

                // Certifique-se de que a condição é do tipo booleano
                if (!compatibleTypes(conditionExpr.type(), Type.BOOLEAN)) {
                    error(conditionExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "A condição do 'if' deve ser do tipo booleano. Tipo Recebido" + conditionExpr.type());
                    return new TypedExpr.IfExpression(conditionExpr, block(thenBranch),
                            elseBranch.map(this::block), Type.INVALID);
                }

                // Criar escopo para os blocos
                environment = new Environment(environment);
                var thenExpr = block(thenBranch);
                environment = environment.enclosing().orElse(null);

                var elseExpr = elseBranch.map(branch -> {
                    environment = new Environment(environment);
                    var result = block(branch);
                    environment = environment.enclosing().orElse(null);
                    return result;
                });

                Type ifType = elseExpr.map(elseBlock -> compatibleTypes(thenExpr.type(), elseBlock.type())
                        ? thenExpr.type()
                        : Type.INVALID).orElse(Type.UNIT);

                if (ifType == Type.INVALID && elseExpr.isPresent()) {
                    error(conditionExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "Os blocos 'then' e 'else' devem ter o mesmo tipo.");
                }

                return new TypedExpr.IfExpression(conditionExpr, thenExpr, elseExpr, ifType);
            }

            case Expr.WhileExpression(Expr condition, Expr.Block body): {
                // Analisar a condição
                var conditionExpr = expression(condition);
                if (!compatibleTypes(conditionExpr.type(), Type.BOOLEAN)) {
                    error(conditionExpr instanceof TypedExpr.Literal literal ? literal.value() : null,
                            "A condição do 'while' deve ser do tipo booleano.");
                    return new TypedExpr.WhileExpression(conditionExpr, null, Type.INVALID);
                }

                // Criar escopo para o corpo do loop
                environment = new Environment(environment);
                var bodyExpr = block(body);
                environment = environment.enclosing().orElse(null);

                // O tipo do loop `while` é sempre `unit`
                return new TypedExpr.WhileExpression(conditionExpr, bodyExpr, Type.UNIT);
            }

            case Expr.ReturnExpression(Expr value): {
                // Analisar o valor do retorno
                var returnValue = expression(value);

                // Obter o tipo esperado de retorno da função atual
                var functionType = environment.enclosing()
                        .flatMap(env -> env.get("returnType"))
                        .map(entry -> ((Environment.EnvEntry.Function) entry).type())
                        .orElse(Type.UNKNOWN);

                // Verificar compatibilidade dos tipos
                if (!compatibleTypes(returnValue.type(), functionType)) {
                    error(value instanceof Expr.Literal literal ? literal.value() : null,
                            "O tipo do valor retornado não é compatível com o tipo de retorno da função.");
                    return new TypedExpr.ReturnExpression(returnValue, Type.INVALID);
                }

                // Retornar a expressão de retorno tipada
                return new TypedExpr.ReturnExpression(returnValue, returnValue.type());
            }

            case Expr.DebugExpression(Expr value): {
                // Analisar a expressão a ser depurada
                var debugValue = expression(value);

                // Retornar a expressão de depuração tipada
                return new TypedExpr.DebugExpression(debugValue, debugValue.type());
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
            return table.lookupType(named.name().lexeme())
                    .map(typeInfo -> new Type.Named(named.name().lexeme(), List.of()))
                    .orElseThrow(() -> new RuntimeException("Tipo não declarado: " + named.name().lexeme()));
        } else if (typeAst instanceof TypeAst.List list) {
            return new Type.Named("[]", List.of(resolveType(list.elementType())));
        }
        throw new RuntimeException("Tipo não suportado: " + typeAst);
    }

    private boolean compatibleTypes(Type type1, Type type2) {
        // Tipos iguais são sempre compatíveis
        if (type1.equals(type2)) {
            return true;
        }

        // Literais inteiros podem ser compatíveis com tipos numéricos específicos
        if (type1.equals(Type.I_LITERAL) && isIntegerType(type2) ||
                type2.equals(Type.I_LITERAL) && isIntegerType(type1)) {
            return true;
        }

        // Literais de ponto flutuante podem ser compatíveis com tipos float
        if (type1.equals(Type.F_LITERAL) && isFloatType(type2) ||
                type2.equals(Type.F_LITERAL) && isFloatType(type1)) {
            return true;
        }

        // Verificar compatibilidade entre listas
        if (type1 instanceof Type.Named named1 && type2 instanceof Type.Named named2) {
            if (named1.name().equals("[]") && named2.name().equals("[]")) {
                // Verificar compatibilidade dos tipos de elementos da lista
                return compatibleTypes(named1.args().get(0), named2.args().get(0));
            }
        }

        // Verificar compatibilidade entre registros
        if (type1 instanceof Type.Record record1 && type2 instanceof Type.Record record2) {
            // Verificar se os registros têm os mesmos campos e tipos
            if (record1.fields().size() != record2.fields().size()) {
                return false;
            }
            for (int i = 0; i < record1.fields().size(); i++) {
                if (!record1.fields().get(i).equals(record2.fields().get(i)) ||
                        !compatibleTypes(record1.types().get(i), record2.types().get(i))) {
                    return false;
                }
            }
            return true;
        }

        // Tipos incompatíveis por padrão
        return false;
    }

    // Verifica se o tipo é um inteiro (ex.: i32, i64, u8, etc.)
    private boolean isIntegerType(Type type) {
        return type.equals(Type.I_LITERAL) || type instanceof Type.Named named && named.name().matches("i\\d+|u\\d+");
    }

    // Verifica se o tipo é um float (ex.: f32, f64)
    private boolean isFloatType(Type type) {
        return type instanceof Type.Named named && named.name().matches("f\\d+");
    }

    private boolean isNumericType(Type type) {
        return isIntegerType(type) || isFloatType(type);
    }

    private void error(Token token, String message) {
        FilePosition position = token != null ? token.where() : new FilePosition(0, 0, "Início do arquivo");
        errors.add(new CompilerError(ErrorType.SEMANTIC, message, position));
    }

    private void error(FilePosition position, String message) {
        errors.add(new CompilerError(ErrorType.SEMANTIC, message, position));
    }
}
